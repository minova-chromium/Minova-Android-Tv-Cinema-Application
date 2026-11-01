package com.minova.cinema.data

import com.minova.cinema.data.remote.PlexConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GDM_PORT = 32414
private val GDM_SEARCH = "M-SEARCH * HTTP/1.1\r\n\r\n".toByteArray(Charsets.UTF_8)

data class PlexLanServer(
    val baseUrl: String,
    val name: String,
    val identifier: String?,
)

object PlexLanDiscovery {
    suspend fun discover(timeoutMs: Long = 1_500L): List<PlexLanServer> = withContext(Dispatchers.IO) {
        val discovered = linkedMapOf<String, PlexLanServer>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 180
                broadcastAddresses().forEach { address ->
                    runCatching {
                        socket.send(DatagramPacket(GDM_SEARCH, GDM_SEARCH.size, address, GDM_PORT))
                    }
                }
                val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(250L)
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(8 * 1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        parseGdmResponse(
                            payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length),
                            remoteAddress = packet.address,
                        )?.let { discovered[it.baseUrl] = it }
                    } catch (_: SocketTimeoutException) {
                        // Keep receiving until the short discovery window closes.
                    }
                }
            }
        }
        discovered.values.toList()
    }
}

internal fun parseGdmResponse(payload: ByteArray, remoteAddress: InetAddress): PlexLanServer? {
    if (remoteAddress !is Inet4Address || (!remoteAddress.isSiteLocalAddress && !remoteAddress.isLoopbackAddress)) return null
    val text = payload.toString(Charsets.UTF_8)
    if (!text.trimStart().startsWith("HTTP/1.0 200", ignoreCase = true) &&
        !text.trimStart().startsWith("HTTP/1.1 200", ignoreCase = true)
    ) return null
    val headers = text.lineSequence().drop(1).mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) null else line.take(separator).trim().lowercase() to line.drop(separator + 1).trim()
    }.toMap()
    val port = headers["port"]?.toIntOrNull() ?: 32400
    if (port !in 1..65535) return null
    val baseUrl = runCatching {
        PlexConfig.normalizeServerAddress("http://${remoteAddress.hostAddress}:$port")
    }.getOrNull() ?: return null
    return PlexLanServer(
        baseUrl = baseUrl,
        name = headers["name"].orEmpty().ifBlank { "Plex Media Server" },
        identifier = headers["resource-identifier"]?.takeIf(String::isNotBlank),
    )
}

private fun broadcastAddresses(): Set<InetAddress> {
    val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
    runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
            .filterIsInstance<Inet4Address>()
            .forEach(addresses::add)
    }
    return addresses
}
