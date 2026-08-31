package com.minova.cinema.tapo

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TapoLocalClientTest {
    @Test
    fun tdpResponsePreservesAdvertisedHttpsEndpoint() {
        val json = """{
            "result": {
                "mgt_encrypt_schm": {
                    "encrypt_type": "KLAP",
                    "is_support_https": true,
                    "http_port": 4433,
                    "lv": 2
                }
            }
        }""".trimIndent().toByteArray()
        val packet = ByteArray(16) + json

        assertEquals(
            TapoEndpointHint("https", 4433),
            parseTapoEndpointHint(packet, 0, packet.size),
        )
    }

    @Test
    fun fallsBackFromKlapToLegacySecurePassthroughAndReusesIt() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val klapAttempts = AtomicInteger()
        var requestedBrightness: Int? = null
        val keyMaterial = ByteArray(32) { (it + 1).toByte() }
        val aesKey = keyMaterial.copyOfRange(0, 16)
        val aesIv = keyMaterial.copyOfRange(16, 32)

        server.createContext("/") { exchange ->
            if (exchange.requestURI.path.endsWith("/handshake1")) {
                klapAttempts.incrementAndGet()
                exchange.respond(404, "{}")
                return@createContext
            }

            val request = gson.fromJson(
                exchange.requestBody.bufferedReader().use { it.readText() },
                JsonObject::class.java,
            )
            when (request.get("method")?.asString) {
                "handshake" -> {
                    val pem = request.getAsJsonObject("params").get("key").asString
                    val publicBytes = Base64.getMimeDecoder().decode(
                        pem.replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .trim(),
                    )
                    val publicKey = KeyFactory.getInstance("RSA")
                        .generatePublic(X509EncodedKeySpec(publicBytes))
                    val encryptedKey = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                        init(Cipher.ENCRYPT_MODE, publicKey)
                        doFinal(keyMaterial)
                    }
                    exchange.responseHeaders.add("Set-Cookie", "SESSIONID=test-session; Path=/")
                    exchange.respond(
                        200,
                        gson.toJson(
                            mapOf(
                                "error_code" to 0,
                                "result" to mapOf("key" to Base64.getEncoder().encodeToString(encryptedKey)),
                            ),
                        ),
                    )
                }

                "securePassthrough" -> {
                    assertEquals("TP_SESSIONID=test-session", exchange.requestHeaders.getFirst("Cookie"))
                    val encrypted = request.getAsJsonObject("params").get("request").asString
                    val inner = gson.fromJson(decrypt(encrypted, aesKey, aesIv), JsonObject::class.java)
                    val innerResponse = when (inner.get("method").asString) {
                        "login_device" -> mapOf("error_code" to 0, "result" to mapOf("token" to "test-token"))
                        "get_device_info" -> mapOf(
                            "error_code" to 0,
                            "result" to mapOf(
                                "nickname" to Base64.getEncoder().encodeToString("Cinema Left".toByteArray()),
                                "model" to "L510",
                                "device_on" to true,
                                "brightness" to 73,
                            ),
                        )
                        "set_device_info" -> {
                            requestedBrightness = inner.getAsJsonObject("params").get("brightness").asInt
                            mapOf("error_code" to 0)
                        }
                        else -> error("Unexpected legacy request: $inner")
                    }
                    val response = mapOf(
                        "error_code" to 0,
                        "result" to mapOf(
                            "response" to encrypt(gson.toJson(innerResponse), aesKey, aesIv),
                        ),
                    )
                    exchange.respond(200, gson.toJson(response))
                }

                else -> exchange.respond(400, "{}")
            }
        }
        server.start()

        try {
            val client = TapoLocalClient(
                ipAddress = "127.0.0.1",
                credentials = TapoCredentials("owner@example.test", "local-test-password"),
                endpointHint = TapoEndpointHint("http", server.address.port),
            )

            val info = client.getDeviceInfo()
            assertEquals("Cinema Left", info.nickname)
            assertEquals("L510", info.model)
            assertEquals(73, info.brightness)
            assertTrue(klapAttempts.get() > 0)

            client.setBrightness(42)
            assertEquals(42, requestedBrightness)
            assertEquals(1, klapAttempts.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun authenticatesAndQueriesAnL630StyleKlapV2Device() = runBlocking {
        val credentials = TapoCredentials("owner@example.test", "case-sensitive-password")
        val remoteSeed = ByteArray(16) { (it + 31).toByte() }
        val authHash = sha256(
            sha1(credentials.email.toByteArray()) + sha1(credentials.password.toByteArray()),
        )
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var localSeed: ByteArray? = null

        server.createContext("/") { exchange ->
            when (exchange.requestURI.path) {
                "/app/handshake1" -> {
                    val local = exchange.requestBody.use { it.readBytes() }
                    localSeed = local
                    exchange.responseHeaders.add("Set-Cookie", "TP_SESSIONID=klap-v2; TIMEOUT=86400")
                    exchange.respondBytes(200, remoteSeed + sha256(local + remoteSeed + authHash))
                }

                "/app/handshake2" -> {
                    val local = checkNotNull(localSeed)
                    val actual = exchange.requestBody.use { it.readBytes() }
                    assertTrue(actual.contentEquals(sha256(remoteSeed + local + authHash)))
                    exchange.respondBytes(200, ByteArray(0))
                }

                "/app/request" -> {
                    val local = checkNotNull(localSeed)
                    val sequence = exchange.requestURI.query.substringAfter("seq=").toInt()
                    val cipher = TestKlapCipher(local, remoteSeed, authHash)
                    val request = gson.fromJson(
                        cipher.decrypt(exchange.requestBody.use { it.readBytes() }, sequence),
                        JsonObject::class.java,
                    )
                    assertEquals("get_device_info", request.get("method").asString)
                    val response = gson.toJson(
                        mapOf(
                            "error_code" to 0,
                            "result" to mapOf(
                                "nickname" to "Cinema Spot",
                                "model" to "L630",
                                "device_on" to true,
                                "brightness" to 64,
                            ),
                        ),
                    )
                    exchange.respondBytes(200, cipher.encrypt(response, sequence))
                }

                else -> exchange.respond(404, "{}")
            }
        }
        server.start()

        try {
            val client = TapoLocalClient(
                ipAddress = "127.0.0.1",
                credentials = credentials,
                endpointHint = TapoEndpointHint("http", server.address.port),
            )

            val info = client.getDeviceInfo()

            assertEquals("Cinema Spot", info.nickname)
            assertEquals("L630", info.model)
            assertEquals(64, info.brightness)
        } finally {
            server.stop(0)
        }
    }

    private fun encrypt(plain: String, key: ByteArray, iv: ByteArray): String =
        Base64.getEncoder().encodeToString(
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            },
        )

    private fun decrypt(encoded: String, key: ByteArray, iv: ByteArray): String =
        String(
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                doFinal(Base64.getDecoder().decode(encoded))
            },
            StandardCharsets.UTF_8,
        )

    private fun HttpExchange.respond(status: Int, json: String) {
        respondBytes(status, json.toByteArray(StandardCharsets.UTF_8), "application/json")
    }

    private fun HttpExchange.respondBytes(
        status: Int,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
    ) {
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private class TestKlapCipher(
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

        fun decrypt(payload: ByteArray, sequence: Int): String {
            val sequenceBytes = ByteBuffer.allocate(4).putInt(sequence).array()
            val cipherText = payload.copyOfRange(32, payload.size)
            assertTrue(
                payload.copyOfRange(0, 32).contentEquals(
                    sha256(signatureKey + sequenceBytes + cipherText),
                ),
            )
            val plain = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivPrefix + sequenceBytes))
                doFinal(cipherText)
            }
            return String(plain, StandardCharsets.UTF_8)
        }

        fun encrypt(plain: String, sequence: Int): ByteArray {
            val sequenceBytes = ByteBuffer.allocate(4).putInt(sequence).array()
            val cipherText = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivPrefix + sequenceBytes))
                doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            }
            return sha256(signatureKey + sequenceBytes + cipherText) + cipherText
        }
    }

    private companion object {
        val gson = Gson()

        fun sha1(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(input)
        fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
    }
}
