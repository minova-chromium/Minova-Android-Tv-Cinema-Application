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
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
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
    internal suspend fun discover(credentials: TapoCredentials): TapoDiscoveryResult {
        val broadcastAddresses = discoverIpAddresses()
        // Some routers do not reliably forward UDP broadcast replies between a
        // wired/5 GHz TV and 2.4 GHz bulbs. Probe only the TV's local /24 as a
        // bounded fallback, then authenticate candidates before showing them.
        val subnetAddresses = discoverLocalHttpEndpoints()
        val addresses = broadcastAddresses + subnetAddresses
        val semaphore = Semaphore(MAX_PARALLEL_AUTHENTICATIONS)
        val lights = coroutineScope {
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
        return TapoDiscoveryResult(
            lights = lights,
            fallbackLightCount = lights.count { it.light.ipAddress !in broadcastAddresses },
        )
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

    /**
     * Finds HTTP endpoints on the same /24 segments as this TV. The scan is
     * deliberately bounded to private, directly connected IPv4 networks and
     * port 80. A device is never presented as Tapo until authenticated
     * get_device_info succeeds with the user's locally stored credentials.
     */
    private suspend fun discoverLocalHttpEndpoints(): Set<String> = coroutineScope {
        val localAddresses = localSiteAddresses()
        val localAddressStrings = localAddresses.mapNotNull(InetAddress::getHostAddress).toSet()
        val candidates = localAddresses
            .mapNotNull(InetAddress::getHostAddress)
            .mapNotNull(::slash24Prefix)
            .distinct()
            .take(MAX_LOCAL_SUBNETS)
            .flatMap { prefix -> (1..254).map { host -> "$prefix.$host" } }
            .filterNot { it in localAddressStrings }
            .distinct()

        val semaphore = Semaphore(MAX_PARALLEL_TCP_PROBES)
        candidates.map { address ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        Socket().use { socket ->
                            socket.connect(
                                InetSocketAddress(address, TAPO_HTTP_PORT),
                                TCP_PROBE_TIMEOUT_MS,
                            )
                        }
                        address
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    private fun localSiteAddresses(): List<Inet4Address> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
    }.getOrDefault(emptyList())

    private fun slash24Prefix(address: String): String? {
        val octets = address.split('.')
        if (octets.size != 4 || octets.any { it.toIntOrNull() !in 0..255 }) return null
        return octets.take(3).joinToString(".")
    }

    private fun secureRandomInt(): Int = java.security.SecureRandom().nextInt()

    private companion object {
        val gson = Gson()
        const val TAPO_DISCOVERY_PORT = 20002
        const val TAPO_HTTP_PORT = 80
        const val TDP_HEADER_SIZE = 16
        const val TDP_INITIAL_CRC = 0x5A6B7C8D
        const val DISCOVERY_ROUNDS = 3
        const val BETWEEN_ROUNDS_MS = 350L
        const val DISCOVERY_WINDOW_MS = 4_000L
        const val RECEIVE_TIMEOUT_MS = 350
        const val MAX_PACKET_SIZE = 8_192
        const val MAX_PARALLEL_AUTHENTICATIONS = 6
        const val MAX_PARALLEL_TCP_PROBES = 48
        const val TCP_PROBE_TIMEOUT_MS = 220
        const val MAX_LOCAL_SUBNETS = 2
    }
}
