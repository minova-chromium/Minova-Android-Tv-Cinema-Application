package com.minova.cinema.tapo

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/** Discovers Tapo endpoints over UDP 20002, then authenticates to read names. */
internal data class DiscoveredTapoLight(
    val light: TapoLight,
    val client: TapoKlapClient,
)

class TapoDiscoveryManager(private val context: Context) {
    internal suspend fun discover(credentials: TapoCredentials): List<DiscoveredTapoLight> {
        val addresses = discoverIpAddresses()
        val semaphore = Semaphore(MAX_PARALLEL_AUTHENTICATIONS)
        return coroutineScope {
            addresses.map { address ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        runCatching {
                            val client = TapoKlapClient(address, credentials)
                            val info = client.getDeviceInfo()
                            DiscoveredTapoLight(
                                light = TapoLight(
                                    ipAddress = address,
                                    nickname = info.nickname,
                                    model = info.model,
                                    isOn = info.isOn,
                                    brightness = info.brightness,
                                    isAssigned = false,
                                ),
                                client = client,
                            )
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.light.nickname }
        }
    }

    private suspend fun discoverIpAddresses(): Set<String> = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("minova-tapo-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.reuseAddress = true
                socket.soTimeout = RECEIVE_TIMEOUT_MS
                val query = createDiscoveryQuery()
                val targets = broadcastAddresses()
                repeat(DISCOVERY_ROUNDS) {
                    targets.forEach { target ->
                        socket.send(DatagramPacket(query, query.size, target, TAPO_DISCOVERY_PORT))
                    }
                    Thread.sleep(BETWEEN_ROUNDS_MS)
                }

                val found = linkedSetOf<String>()
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DISCOVERY_WINDOW_MS)
                while (System.nanoTime() < deadline) {
                    val buffer = ByteArray(MAX_PACKET_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(packet) }.onSuccess {
                        val address = packet.address
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            found += address.hostAddress.orEmpty()
                        }
                    }
                }
                found.filter(String::isNotBlank).toSet()
            }
        } finally {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }

    /**
     * Tapo's TDP v2 probe is a 16-byte big-endian header followed by a JSON
     * payload containing a temporary RSA public key. We only retain response
     * source addresses; all names and capabilities come from authenticated
     * get_device_info calls afterward.
     */
    private fun createDiscoveryQuery(): ByteArray {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encodedKey = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(keyPair.public.encoded)
        val publicPem = "-----BEGIN PUBLIC KEY-----\n$encodedKey\n-----END PUBLIC KEY-----\n"
        val payload = gson.toJson(mapOf("params" to mapOf("rsa_key" to publicPem))).toByteArray()

        val buffer = ByteBuffer.allocate(TDP_HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(2)
        buffer.put(0)
        buffer.putShort(1)
        buffer.putShort(payload.size.toShort())
        buffer.put(17)
        buffer.put(0)
        buffer.putInt(secureRandomInt())
        buffer.putInt(TDP_INITIAL_CRC)
        buffer.put(payload)
        val packet = buffer.array()
        val crc = CRC32().apply { update(packet) }.value.toInt()
        ByteBuffer.wrap(packet, 12, 4).order(ByteOrder.BIG_ENDIAN).putInt(crc)
        return packet
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .filterIsInstance<Inet4Address>()
                .forEach(addresses::add)
        }
        return addresses
    }

    private fun secureRandomInt(): Int = java.security.SecureRandom().nextInt()

    private companion object {
        val gson = Gson()
        const val TAPO_DISCOVERY_PORT = 20002
        const val TDP_HEADER_SIZE = 16
        const val TDP_INITIAL_CRC = 0x5A6B7C8D
        const val DISCOVERY_ROUNDS = 3
        const val BETWEEN_ROUNDS_MS = 350L
        const val DISCOVERY_WINDOW_MS = 4_000L
        const val RECEIVE_TIMEOUT_MS = 350
        const val MAX_PACKET_SIZE = 8_192
        const val MAX_PARALLEL_AUTHENTICATIONS = 6
    }
}
