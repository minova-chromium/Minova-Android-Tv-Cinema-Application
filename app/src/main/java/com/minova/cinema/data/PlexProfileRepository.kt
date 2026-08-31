package com.minova.cinema.data

import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexHomeApiService
import com.minova.cinema.domain.PlexHomeProfile
import java.net.URI

class PlexProfileRepository(
    private val ownerConnection: PlexConnection,
    private val service: PlexHomeApiService,
) {
    suspend fun loadProfiles(activeUuid: String?): List<PlexHomeProfile> {
        val users = service.getHomeUsers()
        val resolvedActiveUuid = activeUuid ?: users.firstOrNull { it.admin }?.uuid
        return users.map { user ->
            PlexHomeProfile(
                id = user.id,
                uuid = user.uuid,
                title = user.title ?: user.username ?: "Plex user",
                thumbUrl = user.thumb,
                isProtected = user.protected,
                isManaged = user.restricted,
                isAdmin = user.admin,
                isActive = user.uuid == resolvedActiveUuid,
            )
        }
    }

    suspend fun switch(profile: PlexHomeProfile, pin: String?): PlexConnection {
        val switchedToken = service.switchUser(
            uuid = profile.uuid,
            pin = pin?.takeIf(String::isNotBlank),
        ).token ?: error("Plex did not return a profile token.")

        val targetHost = runCatching { URI(ownerConnection.baseUrl).host }.getOrNull()
        val resources = service.getResources(switchedToken = switchedToken)
        val matchingServer = resources.firstOrNull { resource ->
            resource.provides?.split(',')?.any { it.trim() == "server" } == true &&
                resource.connections.any { connection ->
                    runCatching { URI(connection.uri).host }.getOrNull() == targetHost
                }
        } ?: resources.firstOrNull { resource ->
            resource.provides?.split(',')?.any { it.trim() == "server" } == true
        }
        return PlexConnection(
            baseUrl = ownerConnection.baseUrl,
            token = matchingServer?.accessToken?.takeIf(String::isNotBlank) ?: switchedToken,
        )
    }
}
