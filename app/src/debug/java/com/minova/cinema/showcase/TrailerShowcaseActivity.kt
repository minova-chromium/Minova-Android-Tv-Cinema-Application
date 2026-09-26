package com.minova.cinema.showcase

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.BuildConfig
import com.minova.cinema.PromoCaptureContract
import com.minova.cinema.R
import com.minova.cinema.ui.intro.AnimatedIntroScreen
import com.minova.cinema.ui.player.MinovaTrailerPreRoll
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaTeal
import kotlinx.coroutines.delay

/** Debug-only, deterministic capture surface. It is absent from release APKs. */
class TrailerShowcaseActivity : ComponentActivity() {
    private var captureReleased by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.DEBUG) { "Trailer showcase is debug-only." }
        check(intent.getBooleanExtra(PromoCaptureContract.EXTRA_PROMO_RECORDING, false)) {
            "Trailer showcase requires the explicit promo recording flag."
        }
        val waitForCaptureSignal = intent.getBooleanExtra(EXTRA_WAIT_FOR_CAPTURE_SIGNAL, false)

        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            MinovaCinemaTheme {
                TrailerShowcase(
                    showRecordingDisclaimer = true,
                    onCinemaPlaybackChanged = ::recordLightingHook,
                    waitForCaptureSignal = waitForCaptureSignal,
                    captureReleased = captureReleased,
                )
            }
        }
    }

    private fun recordLightingHook(playing: Boolean) {
        if (!playing) return
        getSharedPreferences(TELEMETRY_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TAPO_DIM_EVENT_FIRED, true)
            .apply()
        Log.i(LOG_TAG, "Cinema playback started; TP-Link Tapo dim listener fired")
    }

    fun releaseCapture() {
        captureReleased = true
    }

    companion object {
        const val TELEMETRY_PREFERENCES = "trailer_showcase_telemetry"
        const val KEY_TAPO_DIM_EVENT_FIRED = "tapo_dim_event_fired"
        const val EXTRA_WAIT_FOR_CAPTURE_SIGNAL =
            "com.minova.cinema.extra.WAIT_CAPTURE_SIGNAL"
        const val CAPTURE_READY_FILE = "showcase_ready"
        const val CAPTURE_GO_FILE = "showcase_go"
        private const val LOG_TAG = "TrailerShowcase"

        fun intent(activity: ComponentActivity): Intent = Intent(
            activity,
            TrailerShowcaseActivity::class.java,
        ).putExtra(PromoCaptureContract.EXTRA_PROMO_RECORDING, true)
            .putExtra(PromoCaptureContract.EXTRA_DISABLE_PLEX_TRAILERS, true)
    }
}

private enum class ShowcaseStage { Armed, Intro, Browse, Detail, PreRoll, Playback, Complete }

private data class ShowcaseTitle(
    val title: String,
    val subtitle: String,
    val year: String,
    val accent: Color,
    val accentTwo: Color,
)

private val showcaseTitles = listOf(
    ShowcaseTitle("Beyond the Horizon", "A signal from the edge of everything", "2026", Color(0xFF0A88C9), Color(0xFF0CE2E8)),
    ShowcaseTitle("Neon Divide", "Every city keeps a second shadow", "2025", Color(0xFF5525B9), Color(0xFFDF24B7)),
    ShowcaseTitle("The Last Broadcast", "One message. No known sender.", "2026", Color(0xFF0A4D78), Color(0xFFE0A347)),
    ShowcaseTitle("Lumen", "The doorway was never meant to open", "2024", Color(0xFF126073), Color(0xFF79F2FF)),
    ShowcaseTitle("Silent Tide", "Below the surface, something remembers", "2025", Color(0xFF064A5B), Color(0xFF36A7BB)),
    ShowcaseTitle("Cinder Line", "The frontier burns brighter at night", "2026", Color(0xFF8F351A), Color(0xFFFF9B32)),
    ShowcaseTitle("Fractured Orbit", "Gravity was only the beginning", "2025", Color(0xFF344459), Color(0xFFBACAE0)),
    ShowcaseTitle("New Dawn", "Tomorrow is closer than it looks", "2026", Color(0xFF5A3E22), Color(0xFFF3C467)),
)

@Composable
private fun TrailerShowcase(
    showRecordingDisclaimer: Boolean,
    onCinemaPlaybackChanged: (Boolean) -> Unit,
    waitForCaptureSignal: Boolean,
    captureReleased: Boolean,
) {
    var stage by remember {
        mutableStateOf(if (waitForCaptureSignal) ShowcaseStage.Armed else ShowcaseStage.Intro)
    }
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(captureReleased) {
        if (!waitForCaptureSignal || !captureReleased) return@LaunchedEffect
        stage = ShowcaseStage.Intro
    }

    BackHandler(enabled = stage != ShowcaseStage.Intro) {
        stage = if (stage == ShowcaseStage.Playback) ShowcaseStage.Complete else ShowcaseStage.Browse
        onCinemaPlaybackChanged(false)
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(620)) togetherWith fadeOut(tween(420)) },
        label = "trailer_showcase_stage",
    ) { activeStage ->
        when (activeStage) {
            ShowcaseStage.Armed -> Box(Modifier.fillMaxSize().background(Color.Black))
            ShowcaseStage.Intro -> Box(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Minova showcase intro" },
            ) {
                AnimatedIntroScreen { stage = ShowcaseStage.Browse }
            }
            ShowcaseStage.Browse -> ShowcaseBrowseScreen(
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it },
                onOpen = { stage = ShowcaseStage.Detail },
            )
            ShowcaseStage.Detail -> ShowcaseDetailScreen(
                title = showcaseTitles[selectedIndex],
                onPlay = { stage = ShowcaseStage.PreRoll },
            )
            ShowcaseStage.PreRoll -> MinovaTrailerPreRoll(
                showRecordingDisclaimer = showRecordingDisclaimer,
                onFinished = { stage = ShowcaseStage.Playback },
            )
            ShowcaseStage.Playback -> ShowcasePlaybackScreen(
                title = showcaseTitles[selectedIndex],
                onPlaybackStarted = { onCinemaPlaybackChanged(true) },
                onFinished = {
                    onCinemaPlaybackChanged(false)
                    stage = ShowcaseStage.Complete
                },
            )
            ShowcaseStage.Complete -> ShowcaseCompleteScreen()
        }
    }
}

@Composable
private fun ShowcaseBrowseScreen(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    val firstRow = showcaseTitles.take(4)
    val secondRow = showcaseTitles.drop(4)
    val topRequesters = remember { List(firstRow.size) { FocusRequester() } }
    val bottomRequesters = remember { List(secondRow.size) { FocusRequester() } }
    val selected = showcaseTitles[selectedIndex]

    LaunchedEffect(Unit) {
        delay(850)
        topRequesters.first().requestFocus()
    }

    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = { fadeIn(tween(850)) togetherWith fadeOut(tween(850)) },
            label = "showcase_backdrop",
        ) { index ->
            val art = showcaseTitles[index]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MinovaNightDeep,
                                art.accent.copy(alpha = 0.72f),
                                art.accentTwo.copy(alpha = 0.44f),
                                Color.Black,
                            ),
                        ),
                    ),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.9f),
                    0.52f to Color.Black.copy(alpha = 0.36f),
                    1f to Color.Black.copy(alpha = 0.12f),
                ),
            ),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 52.dp, vertical = 24.dp)) {
            ShowcaseHeader()
            Spacer(Modifier.height(34.dp))
            Text(selected.title, color = Color.White, fontSize = 45.sp, fontWeight = FontWeight.Bold)
            Text(
                "${selected.year}  •  PG-13  •  2h 11m  •  4K UHD  •  Dolby Atmos",
                color = MinovaMuted,
                fontSize = 19.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                selected.subtitle,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 21.sp,
                modifier = Modifier.padding(top = 13.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text("FEATURED PREMIERES", color = MinovaCyan, fontSize = 16.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                firstRow.forEachIndexed { index, title ->
                    ShowcasePoster(
                        title = title,
                        index = index,
                        focusRequester = topRequesters[index],
                        upRequester = FocusRequester.Cancel,
                        downRequester = bottomRequesters[index],
                        onSelected = onSelected,
                        onOpen = onOpen,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("NEW RELEASES", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                secondRow.forEachIndexed { rowIndex, title ->
                    val index = rowIndex + firstRow.size
                    ShowcasePoster(
                        title = title,
                        index = index,
                        focusRequester = bottomRequesters[rowIndex],
                        upRequester = topRequesters[rowIndex],
                        downRequester = FocusRequester.Cancel,
                        onSelected = onSelected,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowcaseHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        AsyncImage(model = R.raw.minova_symbol_color, contentDescription = null, modifier = Modifier.size(48.dp))
        Text("MINOVA CINEMA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(28.dp))
        listOf("Home", "Movies", "Series", "Watchlist").forEachIndexed { index, label ->
            Text(
                label,
                color = if (index == 0) MinovaCyan else MinovaMuted,
                fontSize = 20.sp,
                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ShowcasePoster(
    title: ShowcaseTitle,
    index: Int,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onSelected: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(205.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.055f else 1f
                scaleY = if (focused) 1.055f else 1f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(MinovaSurface)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MinovaCyan else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp),
            )
            .focusRequester(focusRequester)
            .focusProperties {
                up = upRequester
                down = downRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelected(index)
            }
            .clickable(onClick = onOpen)
            .focusable()
            .testTag("showcase-poster-$index"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(105.dp)
                .background(Brush.linearGradient(listOf(title.accent, title.accentTwo, Color.Black))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.title.take(1),
                color = Color.White.copy(alpha = 0.26f),
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text(
                title.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(title.year, color = MinovaMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ShowcaseDetailScreen(title: ShowcaseTitle, onPlay: () -> Unit) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(650)
        playFocus.requestFocus()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color.Black, title.accent.copy(alpha = 0.8f), title.accentTwo.copy(alpha = 0.52f))),
        ),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.72f), Color.Transparent)),
            ),
        )
        Column(
            Modifier.width(690.dp).padding(start = 72.dp, top = 96.dp),
        ) {
            Text("MINOVA FEATURE PRESENTATION", color = MinovaCyan, letterSpacing = 2.sp, fontSize = 17.sp)
            Text(title.title, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("${title.year}  •  PG-13  •  131 min", color = MinovaMuted, fontSize = 21.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "A cinematic journey beyond the familiar, presented exactly as your Plex library intended.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 22.sp,
                lineHeight = 30.sp,
                modifier = Modifier.padding(top = 22.dp),
            )
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShowcaseBadge("4K UHD")
                ShowcaseBadge("AFR MATCH")
                ShowcaseBadge("5.1 PASSTHROUGH")
                ShowcaseBadge("DIRECT PLAY")
            }
            Button(
                onClick = onPlay,
                modifier = Modifier.padding(top = 34.dp).focusRequester(playFocus).testTag("showcase-play"),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Play feature")
            }
        }
    }
}

@Composable
private fun ShowcaseBadge(label: String) {
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun ShowcasePlaybackScreen(
    title: ShowcaseTitle,
    onPlaybackStarted: () -> Unit,
    onFinished: () -> Unit,
) {
    var progress by remember { mutableIntStateOf(4) }
    LaunchedEffect(Unit) {
        onPlaybackStarted()
        repeat(5) {
            delay(1_000)
            progress += 7
        }
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(title.accentTwo.copy(alpha = 0.55f), title.accent.copy(alpha = 0.4f), Color.Black),
            ),
        ),
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(48.dp),
        ) {
            Text("NOW PLAYING", color = MinovaCyan, fontSize = 15.sp, letterSpacing = 2.sp)
            Text(title.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            Box(
                Modifier.fillMaxWidth().height(5.dp).padding(top = 18.dp).background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(5.dp).background(MinovaCyan))
            }
            Row(Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("PLAYBACK STARTED", color = MinovaTeal, modifier = Modifier.testTag("showcase-playback-started"))
                Text("4K UHD", color = Color.White)
                Text("Direct Play", color = MinovaTeal)
                Text("5.1 Passthrough", color = Color.White)
            }
        }
    }
}

@Composable
private fun ShowcaseCompleteScreen() {
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("showcase-complete"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(model = R.raw.minova_symbol_color, contentDescription = null, modifier = Modifier.size(156.dp))
            Spacer(Modifier.height(24.dp))
            Text("MINOVA CINEMA", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MinovaCyan)
                Text("SHOWCASE COMPLETE", color = MinovaMuted, fontSize = 18.sp, letterSpacing = 1.4.sp)
            }
        }
    }
}
