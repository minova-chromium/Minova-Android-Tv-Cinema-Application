package com.minova.cinema.ui.experience

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text
import coil3.imageLoader
import com.minova.cinema.data.local.ExperienceSettings
import com.minova.cinema.data.local.PlaybackSettings
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import com.minova.cinema.ui.theme.MinovaMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalExperienceSettings = staticCompositionLocalOf { ExperienceSettings() }

@Composable
internal fun ExperienceSettingsDialog(settings: ExperienceSettings, onChange: (ExperienceSettings) -> Unit,
    onPreset: (String) -> Unit, onRestoreLights: () -> Unit, onClose: () -> Unit,
    hasCustomPreset: Boolean = false, onSavePreset: () -> Unit = {}, onLoadPreset: () -> Unit = {},
    playback: PlaybackSettings = PlaybackSettings(), onCinemaChange: (PlaybackSettings) -> Unit = {},
    showCinemaControls: Boolean = true) {
    val loader = LocalContext.current.imageLoader
    val scope = rememberCoroutineScope()
    var imageBytes by remember { mutableLongStateOf(0L) }
    var clearing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { imageBytes = withContext(Dispatchers.IO) { loader.diskCache?.size ?: 0L } }
    val languages = listOf("" to "Plex / automatic", "en" to "English", "nl" to "Dutch", "fr" to "French", "de" to "German", "es" to "Spanish", "it" to "Italian", "ja" to "Japanese")
    CinemaPanel(if (showCinemaControls) "Cinema preferences" else "App preferences", onClose, status = message) {
        item {
            Text(
                if (showCinemaControls) "Languages, accessibility and brightness are saved for this Plex profile. Cinema Mode switches are shared on this TV. Changes apply immediately."
                else "Language, subtitle, accessibility and storage preferences are saved for this Plex profile. Changes apply immediately.",
                color = MinovaMuted,
            )
        }
        if (showCinemaControls) {
            item { ChoiceStrip("Cinema Mode preset", listOf("full" to "Full cinema", "quiet" to "Quiet evening", "play" to "Just play"), "") { onPreset(it); message = "Preset applied. Your selected lights and local bumper file are unchanged." } }
            item { Text("Full cinema: trailers + configured bumper + lights. Quiet evening: lights only. Just play: no pre-show or automatic lighting.", color = MinovaMuted) }
            item { SettingsSecondaryButton(onClick = { onCinemaChange(playback.copy(cinemaModeEnabled = !playback.cinemaModeEnabled)) }) { Text("Cinema Mode: ${if (playback.cinemaModeEnabled) "On" else "Off"}") } }
            item { SettingsSecondaryButton(onClick = { onCinemaChange(playback.copy(cinemaTrailersEnabled = !playback.cinemaTrailersEnabled)) }) { Text("Movie trailers: ${if (playback.cinemaTrailersEnabled) "On" else "Off"}") } }
            item { SettingsSecondaryButton(onClick = { onCinemaChange(playback.copy(cinemaBumperEnabled = !playback.cinemaBumperEnabled)) }) { Text("Configured local bumper: ${if (playback.cinemaBumperEnabled) "On" else "Off"}") } }
            item { SettingsSecondaryButton(onClick = { onCinemaChange(playback.copy(cinemaLightsEnabled = !playback.cinemaLightsEnabled)) }) { Text("Automatic cinema lighting: ${if (playback.cinemaLightsEnabled) "On" else "Off"}") } }
            item { SettingsSecondaryButton(onClick = { onSavePreset(); message = "Custom preset saved for this profile." }) { Text("Save current Cinema Mode and brightness as My preset") } }
            item { SettingsSecondaryButton(enabled = hasCustomPreset, onClick = { onLoadPreset(); message = "My preset applied." }) { Text("Apply My preset") } }
        }
        item { ChoiceStrip("Audio language", languages, settings.audioLanguage) { onChange(settings.copy(audioLanguage = it)) } }
        item { ChoiceStrip("Subtitles", listOf("off" to "Always off") + languages, if (settings.subtitlesOff) "off" else settings.subtitleLanguage) { onChange(settings.copy(subtitlesOff = it == "off", subtitleLanguage = if (it == "off") "" else it)) } }
        item { ChoiceStrip("Subtitle size", listOf("0.85" to "Small", "1.0" to "Normal", "1.25" to "Large", "1.5" to "Extra large"), "${settings.subtitleScale}") { onChange(settings.copy(subtitleScale = it.toFloat())) } }
        item { SettingsSecondaryButton(onClick = { onChange(settings.copy(subtitleBackground = !settings.subtitleBackground)) }) { Text("Subtitle background: ${if (settings.subtitleBackground) "On" else "Off"}") } }
        item { ChoiceStrip("Interface text", listOf("1.0" to "Normal", "1.1" to "Larger", "1.2" to "Largest"), "${settings.textScale}") { onChange(settings.copy(textScale = it.toFloat())) } }
        item { SettingsSecondaryButton(onClick = { onChange(settings.copy(strongFocus = !settings.strongFocus)) }) { Text("Stronger focus outline: ${if (settings.strongFocus) "On" else "Off"}") } }
        item { SettingsSecondaryButton(onClick = { onChange(settings.copy(highContrast = !settings.highContrast)) }) { Text("Higher interface contrast: ${if (settings.highContrast) "On" else "Off"}") } }
        item { SettingsSecondaryButton(onClick = { onChange(settings.copy(reducedMotion = !settings.reducedMotion)) }) { Text("Reduced motion: ${if (settings.reducedMotion) "On" else "Off"}") } }
        item { SettingsSecondaryButton(onClick = { onChange(settings.copy(endScreen = !settings.endScreen)) }) { Text("End-of-movie suggestions: ${if (settings.endScreen) "On" else "Off"}") } }
        item { SettingsSecondaryButton(onClick = { onChange(settings.copy(themeMusic = !settings.themeMusic)) }) { Text("Plex theme music while browsing: ${if (settings.themeMusic) "On" else "Off"}") } }
        item { ChoiceStrip("Theme music volume", listOf("0.04" to "Very quiet", "0.08" to "Quiet", "0.15" to "Medium"), "${settings.themeVolume}") { onChange(settings.copy(themeVolume = it.toFloat())) } }
        item { Text("Theme music is played only when your Plex server provides a local theme track. It stops when leaving browsing or putting the app in the background.", color = MinovaMuted) }
        if (showCinemaControls) {
            item { ChoiceStrip("Tapo movie brightness", listOf(0, 5, 10, 15, 25).map { "$it" to "$it%" }, "${settings.dimLevel}") { onChange(settings.copy(dimLevel = it.toInt())) } }
            item { ChoiceStrip("Tapo pause / finish brightness", listOf("-1" to "Original brightness") + listOf(15, 25, 50, 75, 100).map { "$it" to "$it%" }, "${settings.restoreLevel}") { onChange(settings.copy(restoreLevel = it.toInt())) } }
            item { SettingsSecondaryButton(onClick = { onRestoreLights(); message = "Restoring the captured original brightness of selected Tapo lights, if a playback snapshot is available." }) { Text("Restore Tapo lights now") } }
            item { Text("Only selected lights are controlled. Lights that were off stay off. Manual restore always uses their captured original state.", color = MinovaMuted) }
        }
        item { Text("Artwork disk cache: ${imageBytes / (1024 * 1024)} MB", color = MinovaMuted) }
        item { SettingsSecondaryButton(enabled = !clearing, onClick = {
            clearing = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { loader.diskCache?.clear(); loader.memoryCache?.clear() } }
                    .onSuccess { imageBytes = loader.diskCache?.size ?: 0L; message = "Artwork cleared. Plex login, metadata, and preferences were kept; visible images will reload." }
                    .onFailure { message = "Couldn't clear artwork. Try again after returning to Home." }
                clearing = false
            }
        }) { Text(if (clearing) "Clearing artwork…" else "Clear artwork cache only") } }
    }
}
