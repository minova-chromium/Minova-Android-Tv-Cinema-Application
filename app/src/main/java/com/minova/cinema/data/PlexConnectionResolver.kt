package com.minova.cinema.data

import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexServiceFactory
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class PlexConnectionResolver(
    private val discoverLan: suspend () -> List<PlexLanServer> = { PlexLanDiscovery.discover() },
    private val canConnect: suspend (PlexConnection) -> Boolean = { candidate ->
        try {
            withTimeoutOrNull(2_500L) {
                PlexServiceFactory.create(candidate).getLibrarySections()
                true
            } == true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    },
) {
    suspend fun resolve(connection: PlexConnection, serverIdentifier: String? = null): PlexConnection {
        val saved = connectionRoutes(connection)
        val savedLan = saved.filter(::isDirectLanPlexAddress)
        for (address in savedLan) {
            val candidate = connection.withRoute(address, saved)
            if (canConnect(candidate)) return candidate
        }

        val discoveredServers = try {
            discoverLan()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val identifiedServers = serverIdentifier?.takeIf(String::isNotBlank)?.let { expected ->
            discoveredServers.filter { it.identifier == expected }
        } ?: discoveredServers
        val discovered = identifiedServers
            .map(PlexLanServer::baseUrl)
            .mapNotNull { runCatching { PlexConfig.normalizeServerAddress(it) }.getOrNull() }
            .distinct()
        val allRoutes = (savedLan + discovered + saved.filterNot(::isDirectLanPlexAddress)).distinct()
        for (address in discovered + saved.filterNot(::isDirectLanPlexAddress)) {
            val candidate = connection.withRoute(address, allRoutes)
            if (canConnect(candidate)) return candidate
        }
        return connection.copy(fallbackBaseUrls = allRoutes.filterNot { it == connection.baseUrl })
    }
}

internal fun connectionRoutes(connection: PlexConnection): List<String> =
    (listOf(connection.baseUrl) + connection.fallbackBaseUrls)
        .mapNotNull { runCatching { PlexConfig.normalizeServerAddress(it) }.getOrNull() }
        .flatMap { address -> listOfNotNull(address, plexDirectLanFallback(address)) }
        .distinct()

internal fun isDirectLanPlexAddress(address: String): Boolean = runCatching {
    val host = URI(address).host ?: return@runCatching false
    val octets = host.split('.').map(String::toIntOrNull)
    octets.size == 4 && octets.all { it != null && it in 0..255 } && when (octets[0]) {
        10, 127 -> true
        169 -> octets[1] == 254
        172 -> octets[1] in 16..31
        192 -> octets[1] == 168
        else -> false
    }
}.getOrDefault(false)

internal fun plexDirectLanFallback(address: String): String? = runCatching {
    val uri = URI(address)
    val match = Regex("^(\\d{1,3})-(\\d{1,3})-(\\d{1,3})-(\\d{1,3})\\..+\\.plex\\.direct$", RegexOption.IGNORE_CASE)
        .matchEntire(uri.host.orEmpty()) ?: return@runCatching null
    val host = match.groupValues.drop(1).joinToString(".")
    if (!isDirectLanPlexAddress("http://$host")) return@runCatching null
    PlexConfig.normalizeServerAddress("http://$host:${if (uri.port > 0) uri.port else 32400}")
}.getOrNull()

private fun PlexConnection.withRoute(route: String, allRoutes: List<String>): PlexConnection = copy(
    baseUrl = route,
    fallbackBaseUrls = allRoutes.filterNot { it == route },
)
