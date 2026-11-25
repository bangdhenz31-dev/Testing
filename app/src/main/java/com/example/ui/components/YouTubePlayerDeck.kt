package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.audio.DawTelemetry
import com.example.audio.YouTubeCaptureBridge
import com.example.ui.DawViewModel
import com.example.ui.StudioUiState
import com.example.ui.YouTubeDeckMode
import com.example.ui.YouTubePlayerCommand
import com.example.ui.screens.CyberStudioCard
import com.example.ui.screens.PeakMeterBar
import com.example.ui.theme.StudioTheme
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

private val ALLOWED_YT_HOSTS = setOf(
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "youtu.be",
    "www.youtube-nocookie.com",
    "consent.youtube.com",
    "consent.google.com",
)

private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

private class YouTubeJsBridge(
    private val mainHandler: Handler,
    private val onReady: () -> Unit,
    private val onState: (Int) -> Unit,
    private val onProgress: (Float, Float) -> Unit,
    private val onTitle: (String) -> Unit,
) {
    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onStateChanged(state: Int) {
        mainHandler.post { onState(state) }
    }

    @JavascriptInterface
    fun onTimeUpdate(currentSec: Double, durationSec: Double) {
        mainHandler.post {
            onProgress(currentSec.toFloat(), durationSec.toFloat())
        }
    }

    @JavascriptInterface
    fun onVideoMeta(title: String) {
        val safeTitle = title.take(140)
        mainHandler.post { onTitle(safeTitle) }
    }
}

/**
 * Integrated Android WebView YouTube Player, Live Search Browser, A-B Sampler,
 * and AudioPlaybackCapture stream bridge into the DAW mixer & FFT analyzer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouTubePlayerDeck(
    uiState: StudioUiState,
    telemetry: DawTelemetry,
    viewModel: DawViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = StudioTheme.colors
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val activeVideoId = uiState.activeProject.youtubeVideoId
        .takeIf { it.matches(VIDEO_ID_REGEX) }
        ?: "jfKfPfyJRdk"

    var playerReady by remember { mutableStateOf(false) }
    var webViewRecreateKey by remember { mutableIntStateOf(0) }
    var useSoftwareLayer by remember { mutableStateOf(false) }
    var webViewError by remember { mutableStateOf<String?>(null) }
    var lastRenderedMode by remember(webViewRecreateKey) { mutableStateOf<YouTubeDeckMode?>(null) }
    var lastLoadedVideoId by remember(webViewRecreateKey) { mutableStateOf<String?>(null) }
    var lastLoadedSearchUrl by remember(webViewRecreateKey) { mutableStateOf<String?>(null) }

    val latestViewModel by rememberUpdatedState(viewModel)
    val latestDeckMode by rememberUpdatedState(uiState.youtubeDeckMode)

    // MediaProjection & RECORD_AUDIO launchers for direct DAW WebView stream capture (API 29+)
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            YouTubeCaptureBridge.startCaptureService(context, result.resultCode, result.data!!)
        }
    }

    val launchProjectionPrompt: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mgr != null) {
                projectionLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchProjectionPrompt()
        }
    }

    val webView = remember(context, webViewRecreateKey, useSoftwareLayer) {
        runCatching {
            createConfiguredYouTubeWebView(
                context = context,
                mainHandler = mainHandler,
                useSoftwareLayer = useSoftwareLayer,
                onReady = {
                    playerReady = true
                    webViewError = null
                },
                onState = { state -> latestViewModel.onYoutubePlayerStateChanged(state) },
                onProgress = { cur, dur -> latestViewModel.onYoutubeProgress(cur, dur) },
                onTitle = { title -> latestViewModel.onYoutubeMetaTitle(title) },
                onVideoIntercepted = { videoId, pageTitle ->
                    if (latestDeckMode == YouTubeDeckMode.LIVE_SEARCH) {
                        latestViewModel.onYouTubeVideoPickedFromBrowser(videoId, pageTitle)
                    }
                },
                onRenderGone = { didCrash ->
                    Log.w("YouTubePlayerDeck", "WebView render process gone (didCrash=$didCrash)")
                    playerReady = false
                    if (!useSoftwareLayer) {
                        // Automatically recover once with software layer if GPU/rendernode failed
                        useSoftwareLayer = true
                        webViewError = null
                        webViewRecreateKey++
                    } else {
                        webViewError = "Renderer YouTube terputus. Ketuk Muat Ulang untuk melanjutkan."
                    }
                },
                onLoadError = { desc ->
                    webViewError = desc
                },
            )
        }.onFailure { err ->
            Log.e("YouTubePlayerDeck", "WebView initialization failed", err)
            webViewError = "WebView tidak tersedia pada perangkat ini."
        }.getOrNull()
    }

    BackHandler(enabled = uiState.youtubeDeckMode == YouTubeDeckMode.LIVE_SEARCH) {
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            viewModel.setYoutubeDeckMode(YouTubeDeckMode.PLAYER)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            if (webView != null) {
                runCatching {
                    webView.removeJavascriptInterface("AndroidYtBridge")
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }
    }

    LaunchedEffect(viewModel, webView) {
        val activeView = webView ?: return@LaunchedEffect
        viewModel.youtubeCommands.collectLatest { cmd ->
            if (latestDeckMode != YouTubeDeckMode.PLAYER) return@collectLatest
            when (cmd) {
                is YouTubePlayerCommand.LoadVideo -> {
                    val safeId = cmd.videoId.takeIf { it.matches(VIDEO_ID_REGEX) } ?: return@collectLatest
                    if (playerReady) {
                        activeView.evaluateJavascript("window.ytLoadVideo && window.ytLoadVideo('$safeId');", null)
                    }
                }
                YouTubePlayerCommand.Play -> {
                    activeView.evaluateJavascript("window.ytPlay && window.ytPlay();", null)
                }
                YouTubePlayerCommand.Pause -> {
                    activeView.evaluateJavascript("window.ytPause && window.ytPause();", null)
                }
                is YouTubePlayerCommand.SeekTo -> {
                    activeView.evaluateJavascript(
                        String.format(Locale.US, "window.ytSeekTo && window.ytSeekTo(%.2f);", cmd.seconds),
                        null,
                    )
                }
                is YouTubePlayerCommand.SetPlaybackRate -> {
                    activeView.evaluateJavascript(
                        String.format(Locale.US, "window.ytSetSpeed && window.ytSetSpeed(%.2f);", cmd.rate),
                        null,
                    )
                }
                is YouTubePlayerCommand.SetVolume -> {
                    val pct = cmd.percent.coerceIn(0, 100)
                    activeView.evaluateJavascript("window.ytSetVolume && window.ytSetVolume($pct);", null)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Card 1: WebView YouTube Player & Live Search Deck
        CyberStudioCard(borderColor = colors.live.copy(alpha = 0.55f)) {
            // Header + Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = colors.live)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "YOUTUBE WEBVIEW DECK",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "ID: $activeVideoId",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.neon,
                        )
                    }
                }

                // Live status indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (telemetry.isYouTubeCaptureActive) colors.live.copy(alpha = 0.22f) else colors.surfaceElevated,
                    border = BorderStroke(1.dp, if (telemetry.isYouTubeCaptureActive) colors.live else colors.border),
                ) {
                    Text(
                        text = when {
                            telemetry.isYouTubeSampling -> "SAMPLING"
                            telemetry.isYouTubeCaptureActive -> "STREAM → DAW"
                            uiState.youtubePlaybackState == 1 -> "PLAYING"
                            else -> "REFERENCE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.isYouTubeCaptureActive) colors.live else colors.neon,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }

            // Deck Mode Tabs: Player & Sampling vs Live YouTube Search Browser
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeckModeButton(
                    label = "Player & Sampling",
                    icon = Icons.Filled.PlayArrow,
                    selected = uiState.youtubeDeckMode == YouTubeDeckMode.PLAYER,
                    accent = colors.live,
                    onClick = { viewModel.setYoutubeDeckMode(YouTubeDeckMode.PLAYER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("yt_mode_player"),
                )
                DeckModeButton(
                    label = "Jelajah YouTube",
                    icon = Icons.Filled.Search,
                    selected = uiState.youtubeDeckMode == YouTubeDeckMode.LIVE_SEARCH,
                    accent = colors.neon,
                    onClick = { viewModel.setYoutubeDeckMode(YouTubeDeckMode.LIVE_SEARCH) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("yt_mode_browser"),
                )
            }

            // WebView Viewport (16:9 in Player mode, taller in Browser mode)
            val aspect = if (uiState.youtubeDeckMode == YouTubeDeckMode.LIVE_SEARCH) 4f / 3f else 16f / 9f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val activeWebView = webView
                if (activeWebView == null || webViewError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = webViewError ?: "WebView sedang disiapkan...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                webViewError = null
                                playerReady = false
                                webViewRecreateKey++
                            },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.live,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Muat Ulang Player")
                        }
                    }
                } else {
                    key(webViewRecreateKey) {
                        AndroidView(
                            factory = { activeWebView },
                            update = { view ->
                                when (uiState.youtubeDeckMode) {
                                    YouTubeDeckMode.PLAYER -> {
                                        if (lastRenderedMode != YouTubeDeckMode.PLAYER) {
                                            lastRenderedMode = YouTubeDeckMode.PLAYER
                                            lastLoadedVideoId = activeVideoId
                                            playerReady = false
                                            view.loadDataWithBaseURL(
                                                "https://www.youtube.com",
                                                buildYouTubeIframeHtml(activeVideoId),
                                                "text/html",
                                                "UTF-8",
                                                null,
                                            )
                                        } else if (lastLoadedVideoId != activeVideoId) {
                                            lastLoadedVideoId = activeVideoId
                                            if (playerReady) {
                                                view.evaluateJavascript(
                                                    "window.ytLoadVideo && window.ytLoadVideo('$activeVideoId');",
                                                    null,
                                                )
                                            } else {
                                                view.loadDataWithBaseURL(
                                                    "https://www.youtube.com",
                                                    buildYouTubeIframeHtml(activeVideoId),
                                                    "text/html",
                                                    "UTF-8",
                                                    null,
                                                )
                                            }
                                        }
                                    }
                                    YouTubeDeckMode.LIVE_SEARCH -> {
                                        if (lastRenderedMode != YouTubeDeckMode.LIVE_SEARCH ||
                                            lastLoadedSearchUrl != uiState.youtubeLiveSearchUrl
                                        ) {
                                            lastRenderedMode = YouTubeDeckMode.LIVE_SEARCH
                                            lastLoadedSearchUrl = uiState.youtubeLiveSearchUrl
                                            playerReady = false
                                            view.loadUrl(uiState.youtubeLiveSearchUrl)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("youtube_webview"),
                        )
                    }
                }
            }

            if (uiState.youtubeDeckMode == YouTubeDeckMode.LIVE_SEARCH) {
                Text(
                    text = "Ketuk video apa saja di hasil YouTube untuk langsung memuatnya ke Player & Sampler DAW.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.neon,
                )
            }

            // Active Track Title
            Text(
                text = uiState.activeProject.youtubeTitle.ifBlank { "Track Referensi YouTube" },
                style = MaterialTheme.typography.titleSmall,
                color = colors.neon,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Native Transport Scrub Bar & Timecode (Player Mode)
            if (uiState.youtubeDeckMode == YouTubeDeckMode.PLAYER) {
                val dur = uiState.youtubeDurationSec.coerceAtLeast(1f)
                val cur = uiState.youtubeCurrentSec.coerceIn(0f, dur)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = cur,
                        onValueChange = { viewModel.seekYoutubeTo(it) },
                        valueRange = 0f..dur,
                        enabled = uiState.youtubeDurationSec > 0f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.live,
                            activeTrackColor = colors.live,
                            inactiveTrackColor = colors.border,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatSecTime(uiState.youtubeCurrentSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.neon,
                        )
                        if (uiState.youtubeLoopStartSec != null || uiState.youtubeLoopEndSec != null) {
                            val aText = uiState.youtubeLoopStartSec?.let { formatSecTime(it) } ?: "--:--"
                            val bText = uiState.youtubeLoopEndSec?.let { formatSecTime(it) } ?: "--:--"
                            Text(
                                text = "Loop: $aText → $bText",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.youtubeLoopEnabled) colors.yellow else colors.textMuted,
                            )
                        }
                        Text(
                            text = formatSecTime(uiState.youtubeDurationSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }

                // Transport Buttons (-5s, Play/Pause, +5s, Sync DAW)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { viewModel.seekYoutubeTo(uiState.youtubeCurrentSec - 5f) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.bg)
                                .border(1.dp, colors.border, CircleShape),
                        ) {
                            Icon(Icons.Filled.Replay5, contentDescription = "Mundur 5 detik", tint = colors.textPrimary)
                        }

                        Surface(
                            onClick = viewModel::toggleYoutubePlayPause,
                            shape = CircleShape,
                            color = colors.live,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("yt_play_pause_button"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (uiState.youtubePlaybackState == 1) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = "Putar / Jeda YouTube",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.seekYoutubeTo(uiState.youtubeCurrentSec + 5f) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.bg)
                                .border(1.dp, colors.border, CircleShape),
                        ) {
                            Icon(Icons.Filled.Forward5, contentDescription = "Maju 5 detik", tint = colors.textPrimary)
                        }
                    }

                    FilterChip(
                        selected = uiState.syncDawWithYoutube,
                        onClick = viewModel::toggleSyncDawWithYoutube,
                        leadingIcon = {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Sync DAW") },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("yt_sync_daw_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.neon.copy(alpha = 0.22f),
                            selectedLabelColor = colors.neon,
                        ),
                    )
                }

                // Playback Speed & Stream Volume
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { rate ->
                        val selected = kotlin.math.abs(uiState.youtubePlaybackRate - rate) < 0.01f
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setYoutubePlaybackRate(rate) },
                            label = { Text("${rate}x") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.purple.copy(alpha = 0.24f),
                                selectedLabelColor = colors.textPrimary,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Volume YouTube", tint = colors.neon)
                    Slider(
                        value = uiState.youtubeVolume,
                        onValueChange = viewModel::setYoutubeVolume,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = colors.neon, activeTrackColor = colors.neon),
                    )
                    Text(
                        text = "${(uiState.youtubeVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neon,
                        modifier = Modifier.width(42.dp),
                    )
                }

                // A-B Loop Sampling Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallLoopActionChip(
                        label = "Set A",
                        active = uiState.youtubeLoopStartSec != null,
                        accent = colors.yellow,
                        onClick = viewModel::setYoutubeLoopA,
                        modifier = Modifier.weight(1f),
                    )
                    SmallLoopActionChip(
                        label = "Set B",
                        active = uiState.youtubeLoopEndSec != null,
                        accent = colors.yellow,
                        onClick = viewModel::setYoutubeLoopB,
                        modifier = Modifier.weight(1f),
                    )
                    SmallLoopActionChip(
                        label = if (uiState.youtubeLoopEnabled) "Loop ON" else "Loop",
                        active = uiState.youtubeLoopEnabled,
                        accent = colors.green,
                        icon = Icons.Filled.Loop,
                        onClick = viewModel::toggleYoutubeLoopEnabled,
                        modifier = Modifier.weight(1.2f),
                    )
                    SmallLoopActionChip(
                        label = "Reset",
                        active = false,
                        accent = colors.textMuted,
                        onClick = viewModel::clearYoutubeLoop,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Card 2: Direct Stream Into DAW Audio Engine & Live Sampler (API 29+)
        CyberStudioCard(borderColor = colors.purple.copy(alpha = 0.55f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = colors.purple)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "STREAM AUDIO KE DAW & SAMPLER",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.purple,
                        )
                        Text(
                            text = "Tangkap stream YouTube untuk FFT Analyzer, 8D DSP & sample CH 2",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Text(
                    text = "Tangkapan audio internal memerlukan Android 10 (API 29) atau lebih baru. Gunakan Mode Referensi & Sync DAW di atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            } else {
                PeakMeterBar(level = telemetry.youtubePeak)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (telemetry.isYouTubeCaptureActive) {
                                YouTubeCaptureBridge.stopCaptureService(context)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    launchProjectionPrompt()
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("yt_capture_toggle_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (telemetry.isYouTubeCaptureActive) {
                                colors.live
                            } else {
                                colors.purple.copy(alpha = 0.22f)
                            },
                            contentColor = if (telemetry.isYouTubeCaptureActive) Color.White else colors.textPrimary,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.isYouTubeCaptureActive) colors.live else colors.purple,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = if (telemetry.isYouTubeCaptureActive) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (telemetry.isYouTubeCaptureActive) "Stop Stream" else "Tangkap Stream",
                            maxLines = 1,
                        )
                    }

                    Button(
                        onClick = viewModel::toggleYoutubeSampleRecording,
                        enabled = telemetry.isYouTubeCaptureActive,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("yt_sample_record_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (telemetry.isYouTubeSampling) {
                                colors.pink
                            } else {
                                colors.surfaceElevated
                            },
                            contentColor = if (telemetry.isYouTubeSampling) Color.White else colors.textPrimary,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.isYouTubeSampling) colors.pink else colors.neon.copy(alpha = 0.6f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = if (telemetry.isYouTubeSampling) {
                                Icons.Filled.Stop
                            } else {
                                Icons.Filled.FiberManualRecord
                            },
                            contentDescription = null,
                            tint = if (telemetry.isYouTubeSampling) Color.White else colors.pink,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (telemetry.isYouTubeSampling) {
                                String.format(Locale.US, "Simpan %.1fs", telemetry.youtubeSampleSeconds)
                            } else {
                                "Rekam → CH 2"
                            },
                            maxLines = 1,
                        )
                    }
                }

                // DSP Routing Toggle: Reference Monitor + FFT vs Live 8D DSP Bus
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Proses Langsung ke Bus 8D DSP & EQ",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (telemetry.youtubeRouteToDsp) {
                                "Aktif: Stream melewati 8D Radar, 5-Band EQ & Reverb"
                            } else {
                                "Monitor FFT & Sampler (mencegah audio ganda di speaker)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.youtubeRouteToDsp) colors.green else colors.textMuted,
                        )
                    }
                    Switch(
                        checked = telemetry.youtubeRouteToDsp,
                        onCheckedChange = viewModel::setYoutubeRouteToDsp,
                        modifier = Modifier.testTag("yt_route_dsp_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.neon),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else colors.bg,
        border = BorderStroke(1.dp, if (selected) accent else colors.border),
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else colors.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.textPrimary else colors.textMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallLoopActionChip(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = StudioTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) accent.copy(alpha = 0.22f) else colors.bg,
        border = BorderStroke(1.dp, if (active) accent else colors.border),
        modifier = modifier.height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (active) accent else colors.textMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) accent else colors.textPrimary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

private fun formatSecTime(seconds: Float): String {
    val total = seconds.coerceAtLeast(0f).toInt()
    val mins = total / 60
    val secs = total % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun isAllowedYouTubeUri(uri: Uri?): Boolean {
    if (uri == null) return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase(Locale.US) ?: return false
    return host in ALLOWED_YT_HOSTS
}

private fun extractVideoIdFromUri(uri: Uri?): String? {
    if (uri == null || !isAllowedYouTubeUri(uri)) return null
    val host = uri.host?.lowercase(Locale.US) ?: return null
    val candidate = when {
        host == "youtu.be" -> uri.pathSegments.firstOrNull()
        uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
        uri.path?.startsWith("/embed/") == true -> uri.pathSegments.getOrNull(1)
        else -> uri.getQueryParameter("v")
    }
    return candidate?.takeIf { it.matches(VIDEO_ID_REGEX) }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredYouTubeWebView(
    context: Context,
    mainHandler: Handler,
    useSoftwareLayer: Boolean,
    onReady: () -> Unit,
    onState: (Int) -> Unit,
    onProgress: (Float, Float) -> Unit,
    onTitle: (String) -> Unit,
    onVideoIntercepted: (String, String?) -> Unit,
    onRenderGone: (Boolean) -> Unit,
    onLoadError: (String) -> Unit,
): WebView {
    return WebView(context).apply {
        if (useSoftwareLayer) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false

        addJavascriptInterface(
            YouTubeJsBridge(mainHandler, onReady, onState, onProgress, onTitle),
            "AndroidYtBridge",
        )

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return true
                if (!isAllowedYouTubeUri(uri)) {
                    // Block non-HTTPS or non-YouTube external navigation
                    return true
                }
                val pickedId = extractVideoIdFromUri(uri)
                if (pickedId != null) {
                    onVideoIntercepted(pickedId, view?.title)
                    return true
                }
                return false
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                val pickedId = extractVideoIdFromUri(uri)
                if (pickedId != null) {
                    onVideoIntercepted(pickedId, view?.title)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val desc = error?.description?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Koneksi YouTube terganggu."
                    mainHandler.post { onLoadError("Gagal memuat YouTube: $desc") }
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail?.didCrash() == true
                } else {
                    true
                }
                mainHandler.post { onRenderGone(crashed) }
                return true
            }
        }
    }
}

private fun buildYouTubeIframeHtml(videoId: String): String {
    val safeId = videoId.takeIf { it.matches(VIDEO_ID_REGEX) } ?: "jfKfPfyJRdk"
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000000; overflow: hidden; }
                #player { width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player = null;
                var progressTimer = null;

                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        width: '100%',
                        height: '100%',
                        videoId: '$safeId',
                        playerVars: {
                            playsinline: 1,
                            controls: 1,
                            rel: 0,
                            modestbranding: 1,
                            origin: 'https://www.youtube.com'
                        },
                        events: {
                            onReady: function() {
                                if (window.AndroidYtBridge) {
                                    window.AndroidYtBridge.onPlayerReady();
                                    try {
                                        var data = player.getVideoData();
                                        if (data && data.title) window.AndroidYtBridge.onVideoMeta(data.title);
                                    } catch (e) {}
                                }
                            },
                            onStateChange: function(event) {
                                if (window.AndroidYtBridge) {
                                    window.AndroidYtBridge.onStateChanged(event.data);
                                    try {
                                        var data = player.getVideoData();
                                        if (data && data.title) window.AndroidYtBridge.onVideoMeta(data.title);
                                    } catch (e) {}
                                }
                                if (event.data === YT.PlayerState.PLAYING) {
                                    if (!progressTimer) {
                                        progressTimer = setInterval(function() {
                                            if (player && player.getCurrentTime && window.AndroidYtBridge) {
                                                window.AndroidYtBridge.onTimeUpdate(
                                                    player.getCurrentTime() || 0,
                                                    player.getDuration() || 0
                                                );
                                            }
                                        }, 250);
                                    }
                                } else {
                                    if (progressTimer) {
                                        clearInterval(progressTimer);
                                        progressTimer = null;
                                    }
                                    if (player && player.getCurrentTime && window.AndroidYtBridge) {
                                        window.AndroidYtBridge.onTimeUpdate(
                                            player.getCurrentTime() || 0,
                                            player.getDuration() || 0
                                        );
                                    }
                                }
                            }
                        }
                    });
                }

                window.ytLoadVideo = function(id) {
                    if (player && player.loadVideoById) {
                        player.loadVideoById(id);
                    }
                };
                window.ytPlay = function() {
                    if (player && player.playVideo) player.playVideo();
                };
                window.ytPause = function() {
                    if (player && player.pauseVideo) player.pauseVideo();
                };
                window.ytSeekTo = function(sec) {
                    if (player && player.seekTo) player.seekTo(sec, true);
                };
                window.ytSetSpeed = function(rate) {
                    if (player && player.setPlaybackRate) player.setPlaybackRate(rate);
                };
                window.ytSetVolume = function(pct) {
                    if (player && player.setVolume) player.setVolume(pct);
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}
