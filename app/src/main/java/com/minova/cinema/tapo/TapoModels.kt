package com.minova.cinema.tapo

data class TapoCredentials(
    val email: String,
    val password: String,
)

data class TapoLight(
    val ipAddress: String,
    val nickname: String,
    val model: String,
    val isOn: Boolean,
    val brightness: Int,
    val isAssigned: Boolean,
)

data class TapoLightsUiState(
    val hasCredentials: Boolean = false,
    val discovering: Boolean = false,
    val lights: List<TapoLight> = emptyList(),
    val message: String? = null,
)

internal data class TapoDeviceInfo(
    val nickname: String,
    val model: String,
    val isOn: Boolean,
    val brightness: Int,
)

internal data class TapoDiscoveryResult(
    val lights: List<DiscoveredTapoLight>,
    val fallbackLightCount: Int,
)
