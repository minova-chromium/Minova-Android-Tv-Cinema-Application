package com.minova.cinema.tapo

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal class TapoHttpException(
    val statusCode: Int,
    val requestPath: String,
) : IllegalStateException("Tapo request failed ($statusCode) at $requestPath.")

/**
 * Local Tapo client supporting both current KLAP devices and older
 * Secure-Passthrough (RSA + AES) devices behind one API.
 *
 * Device discovery may supply an HTTPS/4433 endpoint. This is important for
 * newer firmware: assuming every Tapo light lives at HTTP/80 makes a valid
 * KLAP implementation look like an authentication failure.
 */
internal class TapoLocalClient(
    private val ipAddress: String,
    private val credentials: TapoCredentials,
    private val endpointHint: TapoEndpointHint? = null,
) {
    private val requestMutex = Mutex()
    private var transport: ActiveTransport? = null

    suspend fun getDeviceInfo(): TapoDeviceInfo = requestMutex.withLock {
        val result = request(
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
        request(
            mapOf(
                "method" to "set_device_info",
                "params" to if (brightness == 0) {
                    mapOf("device_on" to false)
                } else {
                    mapOf("device_on" to true, "brightness" to brightness)
                },
                "requestTimeMils" to System.currentTimeMillis(),
            ),
        )
        Unit
    }

    private suspend fun request(payload: Map<String, Any>): JsonObject = withContext(Dispatchers.IO) {
        val json = gson.toJson(payload)
        var lastFailure: Throwable? = null
        var legacyFailure: Throwable? = null

        // Keep the proven protocol and endpoint for this IP. Re-authenticate
        // once when a session expires or a bulb reboots.
        transport?.let { current ->
            var candidate = current
            for (attempt in 0..1) {
                runCatching { send(candidate, json).checked() }
                    .onFailure { lastFailure = it }
                    .onSuccess { return@withContext it }
                if (attempt == 0) {
                    candidate = runCatching { establish(current.descriptor) }
                        .onFailure { lastFailure = it }
                        .getOrNull()
                        ?: break
                }
            }
            transport = null
            protocolCache.remove(ipAddress)
        }

        protocolCache[ipAddress]?.let { cached ->
            repeat(2) { attempt ->
                val restored = runCatching { establish(cached) }
                    .onFailure { lastFailure = it }
                    .getOrNull()
                if (restored != null) {
                    runCatching { send(restored, json).checked() }
                        .onFailure { lastFailure = it }
                        .onSuccess {
                            transport = restored
                            return@withContext it
                        }
                }
                if (attempt == 0) transport = null
            }
            protocolCache.remove(ipAddress)
        }

        // Modern protocol first, on every plausible advertised endpoint.
        endpointCandidates().forEach { endpoint ->
            val klap = runCatching { establishKlap(endpoint) }
                .onFailure { lastFailure = it }
                .getOrNull()
            if (klap != null) {
                runCatching { send(klap, json).checked() }
                    .onFailure { lastFailure = it }
                    .onSuccess { response ->
                        transport = klap
                        protocolCache[ipAddress] = klap.descriptor
                        return@withContext response
                    }
            }
        }

        // Old devices use RSA handshake + AES securePassthrough on HTTP.
        legacyEndpointCandidates().forEach { endpoint ->
            listOf(LegacyLogin.V2, LegacyLogin.V1).forEach { login ->
                val legacy = runCatching { establishLegacy(endpoint, login) }
                    .onFailure {
                        lastFailure = it
                        if (legacyFailure == null) legacyFailure = it
                    }
                    .getOrNull()
                if (legacy != null) {
                    runCatching { send(legacy, json).checked() }
                        .onFailure {
                            lastFailure = it
                            if (legacyFailure == null) legacyFailure = it
                        }
                        .onSuccess { response ->
                            transport = legacy
                            protocolCache[ipAddress] = legacy.descriptor
                            return@withContext response
                        }
                }
            }
        }

        throw IllegalStateException(
            "Could not authenticate with $ipAddress using KLAP or legacy Tapo local control. " +
                "Check the Tapo login and confirm the TV and light are on the same LAN.",
            legacyFailure ?: lastFailure,
        )
    }

    private fun establish(descriptor: TransportDescriptor): ActiveTransport = when (descriptor.protocol) {
        LocalProtocol.KLAP_V1, LocalProtocol.KLAP_V2 -> establishKlap(
            descriptor.endpoint,
            descriptor.protocol,
        )
        LocalProtocol.LEGACY_AES_V1 -> establishLegacy(descriptor.endpoint, LegacyLogin.V1)
        LocalProtocol.LEGACY_AES_V2 -> establishLegacy(descriptor.endpoint, LegacyLogin.V2)
    }

    private fun send(active: ActiveTransport, json: String): JsonObject = when (active) {
        is ActiveTransport.Klap -> sendKlap(active, json)
        is ActiveTransport.Legacy -> sendLegacy(active, json)
    }

    private fun establishKlap(
        endpoint: TapoEndpointHint,
        requiredProtocol: LocalProtocol? = null,
    ): ActiveTransport.Klap {
        val localSeed = ByteArray(16).also(secureRandom::nextBytes)
        val handshake1 = requestBytes(
            endpoint.url("app/handshake1"),
            body = localSeed,
            mediaType = BINARY_MEDIA_TYPE,
        )
        check(handshake1.body.size >= 48) { "Tapo returned an invalid KLAP handshake." }
        val remoteSeed = handshake1.body.copyOfRange(0, 16)
        val serverHash = handshake1.body.copyOfRange(16, 48)
        val variant = KlapVariant.entries.firstOrNull { candidate ->
            MessageDigest.isEqual(
                candidate.handshakeOne(localSeed, remoteSeed, credentials),
                serverHash,
            )
        } ?: error("Tapo rejected the KLAP credentials.")
        val protocol = if (variant == KlapVariant.V2) LocalProtocol.KLAP_V2 else LocalProtocol.KLAP_V1
        check(requiredProtocol == null || requiredProtocol == protocol) {
            "The Tapo KLAP protocol changed since discovery."
        }
        requestBytes(
            endpoint.url("app/handshake2"),
            body = variant.handshakeTwo(localSeed, remoteSeed, credentials),
            mediaType = BINARY_MEDIA_TYPE,
            cookie = handshake1.cookie,
        )
        return ActiveTransport.Klap(
            descriptor = TransportDescriptor(endpoint, protocol),
            cookie = handshake1.cookie,
            cipher = KlapCipher(localSeed, remoteSeed, variant.authHash(credentials)),
        )
    }

    private fun sendKlap(active: ActiveTransport.Klap, json: String): JsonObject {
        val encrypted = active.cipher.encrypt(json.toByteArray(StandardCharsets.UTF_8))
        val response = requestBytes(
            active.descriptor.endpoint.url("app/request", "seq" to encrypted.sequence.toString()),
            body = encrypted.payload,
            mediaType = BINARY_MEDIA_TYPE,
            cookie = active.cookie,
        )
        return gson.fromJson(active.cipher.decrypt(response.body), JsonObject::class.java)
    }

    private fun establishLegacy(
        endpoint: TapoEndpointHint,
        login: LegacyLogin,
    ): ActiveTransport.Legacy {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val publicKey = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(keyPair.public.encoded)
        val handshakePayload = gson.toJson(
            mapOf(
                "method" to "handshake",
                "params" to mapOf(
                    "key" to "-----BEGIN PUBLIC KEY-----\n$publicKey\n-----END PUBLIC KEY-----\n",
                ),
            ),
        )
        val handshake = requestBytes(
            endpoint.url("app"),
            handshakePayload.toByteArray(StandardCharsets.UTF_8),
            JSON_MEDIA_TYPE,
        )
        val envelope = gson.fromJson(String(handshake.body, StandardCharsets.UTF_8), JsonObject::class.java)
            .checked()
        val encryptedKey = envelope.getAsJsonObject("result")?.stringOrNull("key")
            ?: error("Tapo did not return a legacy session key.")
        val keyMaterial = decryptRsaKey(java.util.Base64.getDecoder().decode(encryptedKey), keyPair)
        check(keyMaterial.size >= 32) { "Tapo returned an invalid legacy session key." }
        val cipher = LegacyCipher(keyMaterial.copyOfRange(0, 16), keyMaterial.copyOfRange(16, 32))
        val provisional = ActiveTransport.Legacy(
            descriptor = TransportDescriptor(
                endpoint,
                if (login == LegacyLogin.V2) LocalProtocol.LEGACY_AES_V2 else LocalProtocol.LEGACY_AES_V1,
            ),
            cookie = handshake.cookie,
            cipher = cipher,
            token = null,
        )
        val passwordHash = sha1(credentials.password.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val emailHash = sha1(credentials.email.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val loginParams = mutableMapOf<String, String>(
            "username" to java.util.Base64.getEncoder().encodeToString(emailHash.toByteArray()),
        )
        if (login == LegacyLogin.V2) {
            loginParams["password2"] = java.util.Base64.getEncoder()
                .encodeToString(passwordHash.toByteArray())
        } else {
            loginParams["password"] = java.util.Base64.getEncoder().encodeToString(
                credentials.password.toByteArray(StandardCharsets.UTF_8),
            )
        }
        val loginResult = sendLegacy(
            provisional,
            gson.toJson(
                mapOf(
                    "method" to "login_device",
                    "params" to loginParams,
                    "request_time_milis" to System.currentTimeMillis(),
                ),
            ),
        ).checked()
        val token = loginResult.getAsJsonObject("result")?.stringOrNull("token")
            ?: error("Tapo legacy login did not return a token.")
        return provisional.copy(token = token)
    }

    private fun sendLegacy(active: ActiveTransport.Legacy, json: String): JsonObject {
        val wrapper = gson.toJson(
            mapOf(
                "method" to "securePassthrough",
                "params" to mapOf("request" to active.cipher.encrypt(json)),
            ),
        )
        val response = requestBytes(
            active.descriptor.endpoint.url("app", active.token?.let { "token" to it }),
            wrapper.toByteArray(StandardCharsets.UTF_8),
            JSON_MEDIA_TYPE,
            active.cookie,
        )
        val envelope = gson.fromJson(String(response.body, StandardCharsets.UTF_8), JsonObject::class.java)
            .checked()
        val wrapped = envelope.getAsJsonObject("result")?.stringOrNull("response")
            ?: error("Tapo returned an empty securePassthrough response.")
        val plain = if (wrapped.trimStart().startsWith("{")) wrapped else active.cipher.decrypt(wrapped)
        return gson.fromJson(plain, JsonObject::class.java)
    }

    private fun requestBytes(
        url: HttpUrl,
        body: ByteArray,
        mediaType: okhttp3.MediaType,
        cookie: String? = null,
    ): HttpResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("requestByApp", "true")
            .post(body.toRequestBody(mediaType))
        cookie?.let { builder.header("Cookie", it) }
        val client = if (url.isHttps) localHttpsClient else httpClient
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw TapoHttpException(response.code, url.encodedPath)
            }
            HttpResponse(
                body = response.body?.bytes() ?: error("Tapo returned an empty response."),
                cookie = response.headers.values("Set-Cookie")
                    .asSequence()
                    .mapNotNull(::sessionCookieFromHeader)
                    .firstOrNull()
                    ?: cookie
                    ?: error("Tapo did not return a session cookie."),
            )
        }
    }

    private fun endpointCandidates(): List<TapoEndpointHint> = buildList {
        endpointHint?.let(::add)
        add(TapoEndpointHint("http", 80))
        add(TapoEndpointHint("https", 4433))
    }.distinct()

    private fun legacyEndpointCandidates(): List<TapoEndpointHint> = endpointCandidates()
        .filter { it.scheme == "http" }
        .ifEmpty { listOf(TapoEndpointHint("http", 80)) }

    private fun TapoEndpointHint.url(
        path: String,
        query: Pair<String, String>? = null,
    ): HttpUrl = HttpUrl.Builder()
        .scheme(scheme)
        .host(ipAddress)
        .port(port)
        .apply { path.split('/').filter(String::isNotBlank).forEach(::addPathSegment) }
        .apply { query?.let { addQueryParameter(it.first, it.second) } }
        .build()

    private sealed interface ActiveTransport {
        val descriptor: TransportDescriptor

        data class Klap(
            override val descriptor: TransportDescriptor,
            val cookie: String,
            val cipher: KlapCipher,
        ) : ActiveTransport

        data class Legacy(
            override val descriptor: TransportDescriptor,
            val cookie: String,
            val cipher: LegacyCipher,
            val token: String?,
        ) : ActiveTransport
    }

    private data class TransportDescriptor(val endpoint: TapoEndpointHint, val protocol: LocalProtocol)
    private data class HttpResponse(val body: ByteArray, val cookie: String)
    private data class EncryptedPayload(val payload: ByteArray, val sequence: Int)

    private enum class LocalProtocol { KLAP_V2, KLAP_V1, LEGACY_AES_V2, LEGACY_AES_V1 }
    private enum class LegacyLogin { V2, V1 }

    private enum class KlapVariant {
        V2 {
            override fun authHash(credentials: TapoCredentials): ByteArray = sha256(
                sha1(credentials.email.toByteArray()) + sha1(credentials.password.toByteArray()),
            )
            override fun handshakeOne(local: ByteArray, remote: ByteArray, credentials: TapoCredentials) =
                sha256(local + remote + authHash(credentials))
            override fun handshakeTwo(local: ByteArray, remote: ByteArray, credentials: TapoCredentials) =
                sha256(remote + local + authHash(credentials))
        },
        V1 {
            override fun authHash(credentials: TapoCredentials): ByteArray = md5(
                md5(credentials.email.toByteArray()) + md5(credentials.password.toByteArray()),
            )
            override fun handshakeOne(local: ByteArray, remote: ByteArray, credentials: TapoCredentials) =
                sha256(local + authHash(credentials))
            override fun handshakeTwo(local: ByteArray, remote: ByteArray, credentials: TapoCredentials) =
                sha256(remote + authHash(credentials))
        };

        abstract fun authHash(credentials: TapoCredentials): ByteArray
        abstract fun handshakeOne(local: ByteArray, remote: ByteArray, credentials: TapoCredentials): ByteArray
        abstract fun handshakeTwo(local: ByteArray, remote: ByteArray, credentials: TapoCredentials): ByteArray
    }

    private class KlapCipher(localSeed: ByteArray, remoteSeed: ByteArray, authHash: ByteArray) {
        private val key = sha256("lsk".toByteArray() + localSeed + remoteSeed + authHash).copyOfRange(0, 16)
        private val ivMaterial = sha256("iv".toByteArray() + localSeed + remoteSeed + authHash)
        private val ivPrefix = ivMaterial.copyOfRange(0, 12)
        private val signatureKey = sha256("ldk".toByteArray() + localSeed + remoteSeed + authHash)
            .copyOfRange(0, 28)
        private var sequence = ByteBuffer.wrap(ivMaterial.copyOfRange(28, 32)).int

        fun encrypt(plainText: ByteArray): EncryptedPayload {
            sequence += 1
            val sequenceBytes = ByteBuffer.allocate(4).putInt(sequence).array()
            val cipherText = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivPrefix + sequenceBytes))
                doFinal(plainText)
            }
            return EncryptedPayload(sha256(signatureKey + sequenceBytes + cipherText) + cipherText, sequence)
        }

        fun decrypt(payload: ByteArray): String {
            check(payload.size > 32) { "Tapo returned an invalid encrypted response." }
            val sequenceBytes = ByteBuffer.allocate(4).putInt(sequence).array()
            val cipherText = payload.copyOfRange(32, payload.size)
            check(
                MessageDigest.isEqual(
                    payload.copyOfRange(0, 32),
                    sha256(signatureKey + sequenceBytes + cipherText),
                ),
            ) { "Tapo returned a response with an invalid KLAP signature." }
            val plain = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivPrefix + sequenceBytes))
                doFinal(cipherText)
            }
            return String(plain, StandardCharsets.UTF_8)
        }
    }

    private class LegacyCipher(private val key: ByteArray, private val iv: ByteArray) {
        fun encrypt(json: String): String = java.util.Base64.getEncoder().encodeToString(
            crypt(Cipher.ENCRYPT_MODE, json.toByteArray(StandardCharsets.UTF_8)),
        )

        fun decrypt(encoded: String): String = String(
            crypt(Cipher.DECRYPT_MODE, java.util.Base64.getDecoder().decode(encoded)),
            StandardCharsets.UTF_8,
        )

        private fun crypt(mode: Int, input: ByteArray): ByteArray =
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                // Cipher itself exposes a nullable `iv` property, so qualify
                // the session IV explicitly instead of resolving the receiver.
                init(
                    mode,
                    SecretKeySpec(this@LegacyCipher.key, "AES"),
                    IvParameterSpec(this@LegacyCipher.iv),
                )
                doFinal(input)
            }
    }

    private companion object {
        val gson = Gson()
        val secureRandom = SecureRandom()
        val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val protocolCache = ConcurrentHashMap<String, TransportDescriptor>()
        val httpClient = baseClient().build()
        val localHttpsClient: OkHttpClient by lazy {
            // Tapo bulbs use a device-local certificate that Android cannot
            // chain to a public CA. This client is used only for a literal LAN
            // IP discovered by TDP; the KLAP body remains encrypted/authenticated.
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), secureRandom)
            }
            baseClient()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        }

        fun baseClient() = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)

        fun decryptRsaKey(encrypted: ByteArray, pair: KeyPair): ByteArray =
            Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, pair.private)
                doFinal(encrypted)
            }

        fun md5(input: ByteArray) = MessageDigest.getInstance("MD5").digest(input)
        fun sha1(input: ByteArray) = MessageDigest.getInstance("SHA-1").digest(input)
        fun sha256(input: ByteArray) = MessageDigest.getInstance("SHA-256").digest(input)

        fun sessionCookieFromHeader(header: String): String? {
            val first = header.substringBefore(';').trim()
            val recognized = first.takeIf {
                it.startsWith("TP_SESSIONID=", ignoreCase = true) ||
                    it.startsWith("SESSIONID=", ignoreCase = true)
            } ?: return null
            // Legacy firmware sometimes calls this SESSIONID in the response
            // but expects TP_SESSIONID on subsequent securePassthrough calls.
            return "TP_SESSIONID=${recognized.substringAfter('=')}"
        }

        fun decodeNickname(raw: String): String {
            if (raw.length % 4 != 0 || !raw.matches(Regex("[A-Za-z0-9+/=_-]+"))) return raw
            val decoded = runCatching {
                val bytes = runCatching { java.util.Base64.getDecoder().decode(raw) }
                    .getOrElse { java.util.Base64.getUrlDecoder().decode(raw) }
                String(bytes, StandardCharsets.UTF_8)
            }.getOrNull()?.trim().orEmpty()
            return decoded.takeIf { value ->
                value.isNotBlank() && value.all { it == '\n' || it == '\r' || !it.isISOControl() }
            } ?: raw
        }

        fun JsonObject.checked(): JsonObject {
            val code = intOrNull("error_code") ?: 0
            check(code == 0) { "Tapo request failed with error $code." }
            return this
        }

        fun JsonObject.stringOrNull(name: String): String? =
            get(name)?.takeUnless { it.isJsonNull }?.asString

        fun JsonObject.intOrNull(name: String): Int? =
            get(name)?.takeUnless { it.isJsonNull }?.asInt
    }
}
