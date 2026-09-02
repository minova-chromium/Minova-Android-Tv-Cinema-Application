package com.minova.cinema.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexHomeApiService
import com.minova.cinema.data.remote.PlexHomeUserDto
import com.minova.cinema.domain.PlexHomeProfile
import java.net.URI

class PlexProfileRepository(
    private val ownerConnection: PlexConnection,
    private val service: PlexHomeApiService,
) {
    suspend fun loadProfiles(activeUuid: String?): List<PlexHomeProfile> {
        val users = parsePlexHomeUsers(service.getHomeUsers())
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

/** Plex has returned both a bare array and several object-wrapped variants. */
internal fun parsePlexHomeUsers(root: JsonElement): List<PlexHomeUserDto> {
    fun JsonObject.arrayNamed(vararg names: String): JsonArray? =
        entrySet().firstOrNull { (name, value) ->
            names.any { it.equals(name, ignoreCase = true) } && value.isJsonArray
        }?.value?.asJsonArray

    val users = when {
        root.isJsonArray -> root.asJsonArray
        root.isJsonObject -> {
            val objectRoot = root.asJsonObject
            objectRoot.arrayNamed("users", "User")
                ?: objectRoot.entrySet()
                    .firstOrNull { it.key.equals("MediaContainer", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.arrayNamed("users", "User")
        }
        else -> null
    } ?: return emptyList()

    val gson = Gson()
    return users.mapNotNull { element ->
        runCatching { gson.fromJson(element, PlexHomeUserDto::class.java) }
            .getOrNull()
            ?.takeIf { it.uuid.isNotBlank() }
    }
}
