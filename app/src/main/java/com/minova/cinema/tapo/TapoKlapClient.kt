package com.minova.cinema.tapo

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Small local-only client for the KLAP v1/v2 protocol used by current Tapo
 * bulbs and light strips. Credentials never leave the LAN and are represented
 * on the wire only by protocol hashes.
 */
internal class TapoKlapClient(
    private val ipAddress: String,
    private val credentials: TapoCredentials,
    private val http: OkHttpClient = sharedHttp,
) {
    private val requestMutex = Mutex()
    private var session: KlapSession? = null

    suspend fun getDeviceInfo(): TapoDeviceInfo = requestMutex.withLock {
        val result = sendRequest(
            mapOf(
                "method" to "get_device_info",
                "requestTimeMils" to System.currentTimeMillis(),
            ),
        ).getAsJsonObject("result") ?: error("Tapo did not return device information.")

        val brightness = result.intOrNull("brightness")?.coerceIn(0, 100)
            ?: error("The discovered Tapo device is not a dimmable light.")
        TapoDeviceInfo(
            nickname = decodeNickname(result.stringOrNull("nickname") ?: ipAddress),
            model = result.stringOrNull("model")
                ?: result.stringOrNull("device_model")
                ?: "Tapo light",
            isOn = result.get("device_on")?.asBoolean ?: false,
            brightness = brightness,
        )
    }

    suspend fun setBrightness(percent: Int) = requestMutex.withLock {
        val brightness = percent.coerceIn(0, 100)
        val params = if (brightness == 0) {
            mapOf("device_on" to false)
        } else {
            mapOf("device_on" to true, "brightness" to brightness)
        }
        sendRequest(
            mapOf(
                "method" to "set_device_info",
                "params" to params,
                "requestTimeMils" to System.currentTimeMillis(),
            ),
        )
        Unit
    }

    private suspend fun sendRequest(payload: Map<String, Any>): JsonObject = withContext(Dispatchers.IO) {
        var activeSession = ensureSession()
        val json = gson.toJson(payload)
        var response = runCatching { sendEncrypted(activeSession, json) }
        if (response.isFailure) {
            // Sessions expire and a bulb may reboot while the app is open.
            // Re-authenticate once before surfacing a failure to the user.
            session = null
            activeSession = ensureSession()
            response = runCatching { sendEncrypted(activeSession, json) }
        }
        response.getOrThrow().also { body ->
            val errorCode = body.intOrNull("error_code") ?: 0
            check(errorCode == 0) { "Tapo request failed with error $errorCode." }
        }
    }

    private fun ensureSession(): KlapSession {
        session?.takeIf { System.currentTimeMillis() < it.expiresAtMs }?.let { return it }
        val established = establishSession()
        session = established
        return established
    }

    private fun establishSession(): KlapSession {
        val localSeed = ByteArray(16).also(secureRandom::nextBytes)
        val handshakeUrl = "http://$ipAddress/app/handshake1"
        val response = http.newCall(
            Request.Builder()
                .url(handshakeUrl)
                .post(localSeed.toRequestBody(BINARY_MEDIA_TYPE))
                .build(),
        ).execute()
        val bytes = response.use { result ->
            check(result.isSuccessful) { "Tapo handshake failed (${result.code})." }
            val cookie = result.headers.values("Set-Cookie")
                .asSequence()
                .mapNotNull(::sessionCookieFromHeader)
                .firstOrNull()
                ?: error("Tapo did not return a session cookie.")
            val body = result.body?.bytes() ?: error("Tapo returned an empty handshake.")
            cookie to body
        }
        val cookie = bytes.first
        val handshake = bytes.second
        check(handshake.size >= 48) { "Tapo returned an invalid handshake." }
        val remoteSeed = handshake.copyOfRange(0, 16)
        val serverHash = handshake.copyOfRange(16, 48)

        val variants = listOf(KlapVariant.V2, KlapVariant.V1)
        val selected = variants.firstOrNull { variant ->
            MessageDigest.isEqual(
                variant.handshakeOne(localSeed, remoteSeed, credentials),
                serverHash,
            )
        } ?: error("Tapo rejected the email or password. They are case-sensitive.")

        val authHash = selected.authHash(credentials)
        val handshakeTwo = selected.handshakeTwo(localSeed, remoteSeed, credentials)
        http.newCall(
            Request.Builder()
                .url("http://$ipAddress/app/handshake2")
                .header("Cookie", cookie)
                .post(handshakeTwo.toRequestBody(BINARY_MEDIA_TYPE))
                .build(),
        ).execute().use { result ->
            check(result.isSuccessful) { "Tapo authentication failed (${result.code})." }
        }

        return KlapSession(
            cookie = cookie,
            cipher = KlapCipher(localSeed, remoteSeed, authHash),
            expiresAtMs = System.currentTimeMillis() + SESSION_LIFETIME_MS,
        )
    }

    private fun sendEncrypted(session: KlapSession, json: String): JsonObject {
        val encrypted = session.cipher.encrypt(json.toByteArray(StandardCharsets.UTF_8))
        val response = http.newCall(
            Request.Builder()
                .url("http://$ipAddress/app/request?seq=${encrypted.sequence}")
                .header("Cookie", session.cookie)
                .post(encrypted.payload.toRequestBody(BINARY_MEDIA_TYPE))
                .build(),
        ).execute()
        val responseBytes = response.use { result ->
            check(result.isSuccessful) { "Tapo request failed (${result.code})." }
            result.body?.bytes() ?: error("Tapo returned an empty response.")
        }
        return gson.fromJson(session.cipher.decrypt(responseBytes), JsonObject::class.java)
    }

    private enum class KlapVariant {
        V1 {
            override fun authHash(credentials: TapoCredentials): ByteArray = md5(
                md5(credentials.email.toByteArray()) + md5(credentials.password.toByteArray()),
            )

            override fun handshakeOne(
                local: ByteArray,
                remote: ByteArray,
                credentials: TapoCredentials,
            ): ByteArray = sha256(local + authHash(credentials))

            override fun handshakeTwo(
                local: ByteArray,
                remote: ByteArray,
                credentials: TapoCredentials,
            ): ByteArray = sha256(remote + authHash(credentials))
        },
        V2 {
            override fun authHash(credentials: TapoCredentials): ByteArray = sha256(
                sha1(credentials.email.toByteArray()) + sha1(credentials.password.toByteArray()),
            )

            override fun handshakeOne(
                local: ByteArray,
                remote: ByteArray,
                credentials: TapoCredentials,
            ): ByteArray = sha256(local + remote + authHash(credentials))

            override fun handshakeTwo(
                local: ByteArray,
                remote: ByteArray,
                credentials: TapoCredentials,
            ): ByteArray = sha256(remote + local + authHash(credentials))
        };

        abstract fun authHash(credentials: TapoCredentials): ByteArray
        abstract fun handshakeOne(
            local: ByteArray,
            remote: ByteArray,
            credentials: TapoCredentials,
        ): ByteArray

        abstract fun handshakeTwo(
            local: ByteArray,
            remote: ByteArray,
            credentials: TapoCredentials,
        ): ByteArray
    }

    private data class KlapSession(
        val cookie: String,
        val cipher: KlapCipher,
        val expiresAtMs: Long,
    )

    private data class EncryptedPayload(val payload: ByteArray, val sequence: Int)

    private class KlapCipher(
        localSeed: ByteArray,
        remoteSeed: ByteArray,
        authHash: ByteArray,
    ) {
        private val key = sha256("lsk".toByteArray() + localSeed + remoteSeed + authHash)
            .copyOfRange(0, 16)
        private val ivMaterial = sha256("iv".toByteArray() + localSeed + remoteSeed + authHash)
        private val ivPrefix = ivMaterial.copyOfRange(0, 12)
        private val signatureKey = sha256("ldk".toByteArray() + localSeed + remoteSeed + authHash)
            .copyOfRange(0, 28)
        private var sequence = ByteBuffer.wrap(ivMaterial.copyOfRange(28, 32)).int

        fun encrypt(plainText: ByteArray): EncryptedPayload {
            sequence += 1
            val sequenceBytes = ByteBuffer.allocate(4).putInt(sequence).array()
            val cipherText = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    IvParameterSpec(ivPrefix + sequenceBytes),
                )
                doFinal(plainText)
            }
            val signature = sha256(signatureKey + sequenceBytes + cipherText)
            return EncryptedPayload(signature + cipherText, sequence)
        }

        fun decrypt(payload: ByteArray): String {
            check(payload.size > 32) { "Tapo returned an invalid encrypted response." }
            val sequenceBytes = ByteBuffer.allocate(4).putInt(sequence).array()
            val plain = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    IvParameterSpec(ivPrefix + sequenceBytes),
                )
                doFinal(payload.copyOfRange(32, payload.size))
            }
            return String(plain, StandardCharsets.UTF_8)
        }
    }

    private companion object {
        val gson = Gson()
        val secureRandom = SecureRandom()
        val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
        const val SESSION_LIFETIME_MS = 20L * 60L * 1000L
        val sharedHttp = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()

        fun md5(input: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(input)
        fun sha1(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(input)
        fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

        fun sessionCookieFromHeader(header: String): String? {
            val value = header.split(';')
                .firstOrNull { it.trim().startsWith("TP_SESSIONID=") }
                ?.trim()
            return value
        }

        fun decodeNickname(raw: String): String {
            if (raw.length % 4 != 0 || !raw.matches(Regex("[A-Za-z0-9+/=_-]+"))) return raw
            val decoded = runCatching {
                String(Base64.decode(raw, Base64.DEFAULT), StandardCharsets.UTF_8)
            }.getOrNull()?.trim().orEmpty()
            return decoded.takeIf { value ->
                value.isNotBlank() && value.all { it == '\n' || it == '\r' || !it.isISOControl() }
            } ?: raw
        }

        fun JsonObject.stringOrNull(name: String): String? =
            get(name)?.takeUnless { it.isJsonNull }?.asString

        fun JsonObject.intOrNull(name: String): Int? =
            get(name)?.takeUnless { it.isJsonNull }?.asInt
    }
}

