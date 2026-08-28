package com.minova.cinema.tapo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Coordinates discovery, assignment persistence, and synchronized fades. */
class TapoLightsRepository(
    private val authManager: TapoAuthManager,
    private val discoveryManager: TapoDiscoveryManager,
    private val preferences: TapoCinemaPreferences,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        TapoLightsUiState(hasCredentials = authManager.read() != null),
    )
    val state: StateFlow<TapoLightsUiState> = _state.asStateFlow()

    private val clients = ConcurrentHashMap<String, TapoKlapClient>()
    private var assignedIps: Set<String> = emptySet()
    private val restoreStates = ConcurrentHashMap<String, TapoDeviceInfo>()
    private val lastCommandedBrightness = ConcurrentHashMap<String, Int>()
    private var fadeJob: Job? = null
    private var lastPlaybackState: Boolean? = null

    init {
        scope.launch {
            preferences.assignedIps.collect { selected ->
                assignedIps = selected
                _state.value = _state.value.copy(
                    lights = _state.value.lights.map { light ->
                        light.copy(isAssigned = light.ipAddress in selected)
                    },
                )
            }
        }
        // Rebuild local sessions after an app/TV restart so remembered Cinema
        // Room assignments work without visiting Settings every time.
        if (authManager.read() != null) discover()
    }

    fun saveCredentials(email: String, password: String) {
        runCatching { authManager.save(email, password) }
            .onSuccess {
                clients.clear()
                restoreStates.clear()
                _state.value = TapoLightsUiState(
                    hasCredentials = true,
                    message = "Tapo login saved securely. Scanning the local network…",
                )
                discover()
            }
            .onFailure { error ->
                _state.value = _state.value.copy(message = error.message)
            }
    }

    fun clearCredentials() {
        fadeJob?.cancel()
        authManager.clear()
        clients.clear()
        restoreStates.clear()
        _state.value = TapoLightsUiState(message = "Tapo login removed from this TV.")
    }

    fun discover() {
        val credentials = authManager.read()
        if (credentials == null) {
            _state.value = _state.value.copy(
                hasCredentials = false,
                message = "Connect your Tapo account before scanning for lights.",
            )
            return
        }
        scope.launch {
            _state.value = _state.value.copy(discovering = true, message = null)
            runCatching { discoveryManager.discover(credentials) }
                .onSuccess { discovery ->
                    clients.clear()
                    discovery.lights.forEach { clients[it.light.ipAddress] = it.client }
                    val lights = discovery.lights.map { result ->
                        result.light.copy(isAssigned = result.light.ipAddress in assignedIps)
                    }
                    _state.value = _state.value.copy(
                        hasCredentials = true,
                        discovering = false,
                        lights = lights,
                        message = if (lights.isEmpty()) {
                            "No compatible Tapo KLAP lights were found by broadcast or local network scan. " +
                                "Confirm the TV and lights are on the same LAN, the Tapo login is correct, " +
                                "and client isolation is disabled."
                        } else {
                            val suffix = if (lights.size == 1) "light" else "lights"
                            buildString {
                                append("Found ${lights.size} compatible Tapo $suffix.")
                                if (discovery.fallbackLightCount > 0) {
                                    append(" The local fallback scan found ")
                                    append(discovery.fallbackLightCount)
                                    append(" that did not answer the broadcast.")
                                }
                            }
                        },
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        discovering = false,
                        message = error.message ?: "Tapo discovery failed.",
                    )
                }
        }
    }

    fun setAssigned(ipAddress: String, assigned: Boolean) {
        scope.launch {
            preferences.setAssigned(ipAddress, assigned)
        }
    }

    fun onPlaybackChanged(playing: Boolean) {
        if (lastPlaybackState == null && !playing) {
            lastPlaybackState = false
            return
        }
        if (lastPlaybackState == playing) return
        lastPlaybackState = playing
        fadeJob?.cancel()
        fadeJob = scope.launch {
            if (playing) fadeDownForCinema() else restoreAfterCinema()
        }
    }

    private suspend fun fadeDownForCinema() {
        val targets = selectedClients()
        if (targets.isEmpty()) return

        val snapshots = coroutineScope {
            targets.map { (ip, client) ->
                async(Dispatchers.IO) {
                    runCatching { ip to client.getDeviceInfo() }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        restoreStates.clear()
        snapshots.forEach { (ip, info) ->
            restoreStates[ip] = info
            lastCommandedBrightness[ip] = info.brightness
        }
        val active = snapshots.filter { it.second.isOn }
        fade(
            targets = active.mapNotNull { (ip, info) ->
                clients[ip]?.let { client -> FadeTarget(ip, client, info.brightness, 0) }
            },
            durationMs = DIM_DURATION_MS,
        )
    }

    private suspend fun restoreAfterCinema() {
        val targets = restoreStates.mapNotNull { (ip, original) ->
            if (!original.isOn) return@mapNotNull null
            clients[ip]?.let { client ->
                FadeTarget(
                    ipAddress = ip,
                    client = client,
                    startBrightness = lastCommandedBrightness[ip] ?: MIN_ON_BRIGHTNESS,
                    targetBrightness = original.brightness.coerceAtLeast(MIN_ON_BRIGHTNESS),
                )
            }
        }
        fade(targets, RESTORE_DURATION_MS)
        restoreStates.clear()
    }

    private fun selectedClients(): List<Pair<String, TapoKlapClient>> = assignedIps.mapNotNull { ip ->
        clients[ip]?.let { ip to it }
    }

    /** Sends each brightness step to all bulbs concurrently to keep them in sync. */
    private suspend fun fade(targets: List<FadeTarget>, durationMs: Long) {
        if (targets.isEmpty()) return
        val steps = (durationMs / STEP_INTERVAL_MS).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            coroutineScope {
                targets.map { target ->
                    async(Dispatchers.IO) {
                        val brightness = (
                            target.startBrightness +
                                (target.targetBrightness - target.startBrightness) * fraction
                            ).toInt().coerceIn(0, 100)
                        runCatching { target.client.setBrightness(brightness) }
                            .onSuccess { lastCommandedBrightness[target.ipAddress] = brightness }
                    }
                }.awaitAll()
            }
            if (step < steps) delay(STEP_INTERVAL_MS)
        }
    }

    private data class FadeTarget(
        val ipAddress: String,
        val client: TapoKlapClient,
        val startBrightness: Int,
        val targetBrightness: Int,
    )

    private companion object {
        const val DIM_DURATION_MS = 4_000L
        const val RESTORE_DURATION_MS = 1_500L
        const val STEP_INTERVAL_MS = 200L
        const val MIN_ON_BRIGHTNESS = 1
    }
}
