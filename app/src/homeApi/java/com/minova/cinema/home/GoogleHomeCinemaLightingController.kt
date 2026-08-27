package com.minova.cinema.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import com.google.home.FactoryRegistry
import com.google.home.ForcePermissionFlow
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Home 1.10 implementation. This source directory is compiled only
 * when `MINOVA_HOME_SDK_ENABLED=true` and Google's SDK is installed in
 * mavenLocal(). Direct commands remain local/low-latency when the device and
 * hub support them; no Home Assistant or Minova cloud service is involved.
 */
@OptIn(HomeExperimentalApi::class)
class GoogleHomeCinemaLightingController(context: Context) : CinemaLightingController {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val assignedIds = preferences.getStringSet(KEY_ASSIGNED_LIGHTS, emptySet())
        .orEmpty()
        .toMutableSet()
    private val client: HomeClient = homeClient(appContext)
    private val _state = MutableStateFlow(LightingUiState(sdkAvailable = true))
    override val state: StateFlow<LightingUiState> = _state.asStateFlow()
    private val devicesById = mutableMapOf<String, HomeDevice>()
    private var registered = false
    private var permissionObserver: Job? = null
    private var fadeJob: Job? = null
    private var lastPlaybackState: Boolean? = null
    // Only lights that Cinema Mode actually dimmed are restored. A light that
    // was already off must stay off when playback pauses or ends.
    private val cinemaDimmedIds = ConcurrentHashMap.newKeySet<String>()

    override fun registerPermissionCaller(activity: ComponentActivity) {
        if (registered) return
        registered = true
        client.registerActivityResultCallerForPermissions(activity)
        permissionObserver = scope.launch {
            client.hasPermissions().collect { permission ->
                val granted = permission == PermissionsState.GRANTED
                _state.value = _state.value.copy(
                    permissionGranted = granted,
                    message = if (granted) null else "Allow access to a Google Home to choose theater lights.",
                )
                if (granted) discoverLights()
            }
        }
    }

    override fun requestPermissions() {
        if (!registered) return
        scope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val result = runCatching {
                client.requestPermissions(ForcePermissionFlow.FORCE_LAUNCH)
            }.getOrElse { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = userFacingHomeError(error),
                )
                return@launch
            }
            _state.value = _state.value.copy(
                loading = false,
                message = if (result.status == PermissionsResultStatus.SUCCESS) null
                else result.errorMessage ?: "Google Home permission was not granted.",
            )
            if (result.status == PermissionsResultStatus.SUCCESS) discoverLights()
        }
    }

    override fun refreshLights() {
        scope.launch { discoverLights() }
    }

    override fun setAssigned(lightId: String, assigned: Boolean) {
        if (assigned) assignedIds += lightId else assignedIds -= lightId
        preferences.edit { putStringSet(KEY_ASSIGNED_LIGHTS, assignedIds.toSet()) }
        _state.value = _state.value.copy(
            lights = _state.value.lights.map { light ->
                if (light.id == lightId) light.copy(isAssigned = assigned) else light
            },
        )
    }

    override fun onCinemaPlaybackChanged(playing: Boolean) {
        if (lastPlaybackState == playing) return
        lastPlaybackState = playing
        fadeJob?.cancel()
        fadeJob = scope.launch {
            fadeAssignedLights(targetPercent = if (playing) 0 else RESTORE_PERCENT)
        }
    }

    override fun release() {
        fadeJob?.cancel()
        permissionObserver?.cancel()
        scope.cancel()
    }

    /**
     * Structure/Device APIs supply the homes, rooms, and lights that the user
     * granted to Minova Cinema. A Cinema assignment is deliberately stored per
     * device ID, so playback can never affect an unselected light.
     */
    private suspend fun discoverLights() {
        if (!_state.value.permissionGranted) return
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching {
            val discovered = mutableListOf<TheaterLight>()
            val latestDevices = mutableMapOf<String, HomeDevice>()
            for (structure in client.structures().list()) {
                val roomNames = structure.rooms().list().associate { room -> room.id.id to room.name }
                for (device in structure.devices().list()) {
                    val types = device.types().first()
                    // Google Home represents common bulbs using four different
                    // Matter device types. Color bulbs still expose LevelControl
                    // and must therefore be treated as dimmable lights too.
                    val supportsDimming = types.any {
                        it.factory == DimmableLightDevice ||
                            it.factory == ColorTemperatureLightDevice ||
                            it.factory == ExtendedColorLightDevice
                    }
                    val isLight = supportsDimming || types.any { it.factory == OnOffLightDevice }
                    if (!isLight) continue

                    val id = device.id.id
                    latestDevices[id] = device
                    discovered += TheaterLight(
                        id = id,
                        name = device.name,
                        roomName = device.roomId?.id?.let(roomNames::get),
                        supportsDimming = supportsDimming,
                        isAssigned = id in assignedIds,
                    )
                }
            }
            devicesById.clear()
            devicesById.putAll(latestDevices)
            _state.value = _state.value.copy(
                loading = false,
                lights = discovered.sortedWith(compareBy({ it.roomName.orEmpty() }, { it.name })),
                message = if (discovered.isEmpty()) "No compatible Google Home lights were found." else null,
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                loading = false,
                message = userFacingHomeError(error),
            )
        }
    }

    private fun userFacingHomeError(error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .mapNotNull(Throwable::message)
            .joinToString(" ")
        return if (
            details.contains("API_UNAVAILABLE", ignoreCase = true) ||
            details.contains("error: 17", ignoreCase = true) ||
            details.contains("Permissions.API is not available", ignoreCase = true)
        ) {
            "Google Home is unavailable on this device. Android Studio TV emulators often do not " +
                "provide the Home Permissions service; test Cinema lights on a Google-certified " +
                "Android TV 10+ device with current Google Play services."
        } else {
            error.message ?: "Google Home could not complete this request."
        }
    }

    private suspend fun fadeAssignedLights(targetPercent: Int) = coroutineScope {
        val targetIds = if (targetPercent == 0) assignedIds.toSet() else cinemaDimmedIds.toSet()
        targetIds.mapNotNull { id -> devicesById[id]?.let { id to it } }.forEach { (id, device) ->
            launch(Dispatchers.IO) {
                runCatching { fadeLight(id, device, targetPercent) }
            }
        }
    }

    private suspend fun fadeLight(id: String, device: HomeDevice, targetPercent: Int) {
        // Home SDK 1.10 exposes standard traits through each device type. The
        // older HomeDevice.trait() shortcut was removed in Home SDK 1.4.
        val types = device.types().first()
        val dimmable = types.filterIsInstance<DimmableLightDevice>().firstOrNull()
        val colorTemperature = types.filterIsInstance<ColorTemperatureLightDevice>().firstOrNull()
        val extendedColor = types.filterIsInstance<ExtendedColorLightDevice>().firstOrNull()
        val onOffLight = types.filterIsInstance<OnOffLightDevice>().firstOrNull()
        val onOff = dimmable?.standardTraits?.onOff
            ?: colorTemperature?.standardTraits?.onOff
            ?: extendedColor?.standardTraits?.onOff
            ?: onOffLight?.standardTraits?.onOff
            ?: return
        val levelControl = dimmable?.standardTraits?.levelControl
            ?: colorTemperature?.standardTraits?.levelControl
            ?: extendedColor?.standardTraits?.levelControl
            ?: onOffLight?.standardTraits?.levelControl
        val wasOn = onOff.onOff == true
        if (targetPercent == 0 && !wasOn) return
        if (targetPercent == 0) cinemaDimmedIds += id
        if (targetPercent > 0 && id !in cinemaDimmedIds) return

        if (levelControl == null) {
            if (targetPercent == 0) onOff.off() else onOff.on()
            if (targetPercent > 0) cinemaDimmedIds -= id
            return
        }

        if (targetPercent > 0) onOff.on()
        val startLevel = levelControl.currentLevel?.toInt()
            ?: if (targetPercent == 0) MAX_LEVEL else 0
        val targetLevel = (MAX_LEVEL * targetPercent / 100f).toInt().coerceIn(0, MAX_LEVEL)
        for (step in 1..FADE_STEPS) {
            val fraction = step.toFloat() / FADE_STEPS
            val level = (startLevel + (targetLevel - startLevel) * fraction)
                .toInt()
                .coerceIn(0, MAX_LEVEL)
            levelControl.moveToLevelWithOnOff(
                level = level.toUByte(),
                transitionTime = null,
                optionsMask = LevelControlTrait.OptionsBitmap(),
                optionsOverride = LevelControlTrait.OptionsBitmap(),
            )
            delay(FADE_DURATION_MS / FADE_STEPS)
        }
        if (targetPercent == 0) onOff.off()
        else cinemaDimmedIds -= id
    }

    private companion object {
        const val PREFERENCES_NAME = "minova_cinema_google_home"
        const val KEY_ASSIGNED_LIGHTS = "assigned_theater_lights"
        const val MAX_LEVEL = 254
        const val RESTORE_PERCENT = 15
        const val FADE_STEPS = 20
        const val FADE_DURATION_MS = 4_000L

        @Volatile private var singletonClient: HomeClient? = null

        fun homeClient(context: Context): HomeClient = singletonClient ?: synchronized(this) {
            singletonClient ?: Home.getClient(
                context.applicationContext,
                HomeConfig(
                    coroutineContext = Dispatchers.IO,
                    factoryRegistry = FactoryRegistry(
                        traits = listOf(OnOff, LevelControl),
                        types = listOf(
                            DimmableLightDevice,
                            ColorTemperatureLightDevice,
                            ExtendedColorLightDevice,
                            OnOffLightDevice,
                        ),
                    ),
                ),
            ).also { singletonClient = it }
        }
    }
}
