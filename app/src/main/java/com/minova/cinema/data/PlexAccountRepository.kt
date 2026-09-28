package com.minova.cinema.data

import com.minova.cinema.data.remote.PlexAccountApiService
import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexResourceConnectionDto
import com.minova.cinema.data.remote.PlexServiceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PlexPinChallenge(
    val id: Long,
    val code: String,
    val authorizationUrl: String,
)

data class PlexDiscoveredServer(
    val id: String,
    val name: String,
    val owned: Boolean,
    val accountToken: String,
    val accessToken: String,
    val connections: List<PlexResourceConnectionDto>,
)

class PlexSignInException(message: String) : IllegalStateException(message)

class PlexAccountRepository(
    private val clientIdentifier: String,
    private val api: PlexAccountApiService,
) {
    suspend fun createPin(): PlexPinChallenge {
        val pin = api.createPin(strong = false)
        if (pin.id <= 0L || pin.code.isBlank()) {
            throw PlexSignInException("Plex did not create a sign-in code. Try again.")
        }
        return PlexPinChallenge(
            id = pin.id,
            code = pin.code,
            authorizationUrl = buildPlexAuthorizationUrl(pin.code),
        )
    }

    suspend fun awaitAuthorization(challenge: PlexPinChallenge): String {
        val token = withTimeoutOrNull(PIN_TIMEOUT_MS) {
            while (true) {
                delay(PIN_POLL_MS)
                api.getPin(challenge.id).authToken
                    ?.takeIf(String::isNotBlank)
                    ?.let { return@withTimeoutOrNull it }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        return token ?: throw PlexSignInException(
            "That Plex sign-in code expired. Select Sign in with Plex to request a new one.",
        )
    }

    suspend fun discoverServers(accountToken: String): List<PlexDiscoveredServer> =
        api.getResources(accountToken)
            .filter { resource ->
                resource.provides.orEmpty().split(',').any { it.trim() == "server" } &&
                    resource.connections.any { !it.uri.isNullOrBlank() }
            }
            .mapIndexed { index, resource ->
                PlexDiscoveredServer(
                    id = resource.clientIdentifier?.takeIf(String::isNotBlank)
                        ?: "server-$index-${resource.name.orEmpty()}",
                    name = resource.name?.takeIf(String::isNotBlank) ?: "Plex Media Server",
                    owned = resource.owned,
                    accountToken = accountToken,
                    accessToken = resource.accessToken?.takeIf(String::isNotBlank) ?: accountToken,
                    connections = resource.connections,
                )
            }

    suspend fun resolveConnection(server: PlexDiscoveredServer): PlexConnection {
        for (candidate in rankPlexConnections(server.connections)) {
            val uri = candidate.uri ?: continue
            val normalized = runCatching { PlexConfig.normalizeServerAddress(uri) }.getOrNull()
                ?: continue
            val connection = PlexConnection(
                baseUrl = normalized,
                token = server.accessToken,
                clientIdentifier = clientIdentifier,
            )
            val reachable = try {
                withTimeoutOrNull(CONNECTION_TEST_TIMEOUT_MS) {
                    PlexServiceFactory.create(connection).getLibrarySections()
                    true
                } == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (reachable) return connection
        }
        throw PlexSignInException(
            "${server.name} was found, but none of its local or remote addresses could be reached.",
        )
    }

    private companion object {
        const val PIN_POLL_MS = 1_000L
        const val PIN_TIMEOUT_MS = 5 * 60_000L
        const val CONNECTION_TEST_TIMEOUT_MS = 6_000L
    }
}

internal fun rankPlexConnections(
    connections: List<PlexResourceConnectionDto>,
): List<PlexResourceConnectionDto> = connections
    .filter { !it.uri.isNullOrBlank() }
    .distinctBy { it.uri?.trimEnd('/') }
    .sortedWith(
        compareBy<PlexResourceConnectionDto> {
            when {
                it.local == true -> 0
                it.relay != true -> 1
                else -> 2
            }
        }.thenBy { if (it.uri.orEmpty().startsWith("https://", ignoreCase = true)) 0 else 1 },
    )

internal fun buildPlexAuthorizationUrl(code: String): String {
    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
    // Short (non-strong) PINs are claimed through Plex's link page. The Auth
    // App fragment URL is intended for the longer strong-code flow and rejects
    // these four-character TV codes after the user presses Sign In.
    return "https://plex.tv/link/?pin=${encode(code)}"
}
