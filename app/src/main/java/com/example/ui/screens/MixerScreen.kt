package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.DawTelemetry
import com.example.data.VoiceEffectPreset
import com.example.ui.DawViewModel
import com.example.ui.StudioUiState
import com.example.ui.theme.StudioTheme
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixerScreen(
    uiState: StudioUiState,
    telemetry: DawTelemetry,
    viewModel: DawViewModel,
) {
    val context = LocalContext.current
    val colors = StudioTheme.colors
    val project = uiState.activeProject

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importAudioClip(context, uri)
            }
        }
    )

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.toggleMicCapture(granted)
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Real-time Master Spectrum + Latency Engine Card
        item {
            CyberStudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = colors.green)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "MESIN DSP LATENSI RENDAH",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.neon,
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f ms", telemetry.estimatedLatencyMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.green,
                    )
                }

                SpectrumVisualizerCanvas(
                    bars = telemetry.spectrumBars,
                    modifier = Modifier.height(64.dp),
                )

                Text(
                    text = "Ukuran Buffer Audio (Frames)",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(64L, 128L, 256L, 512L, 1024L).forEach { frames ->
                        val selected = project.latencyFrames == frames
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateActiveProject { it.copy(latencyFrames = frames) } },
                            label = { Text("${frames}f") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.neon.copy(alpha = 0.2f),
                                selectedLabelColor = colors.neon,
                            ),
                        )
                    }
                }
            }
        }

        // 16-Step Sequencer Grid
        item {
            CyberStudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SEQUENCER 16-STEP (SYNTH)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.yellow,
                    )
                    Text(
                        text = "Ketuk pad untuk intensitas",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (col in 0..7) {
                                val stepIdx = row * 8 + col
                                val intensity = project.stepPattern.getOrElse(stepIdx) { 0L }.toInt()
                                val isCurrent = telemetry.isPlaying && telemetry.activeStep == stepIdx
                                val padColor = when (intensity) {
                                    3 -> colors.pink
                                    2 -> colors.purple
                                    1 -> colors.neon
                                    else -> colors.surfaceElevated
                                }
                                val padTextColor = when {
                                    intensity == 0 -> colors.textMuted
                                    padColor.luminance() > 0.45f -> Color.Black
                                    else -> Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(padColor.copy(alpha = if (intensity == 0) 0.7f else 0.88f))
                                        .border(
                                            width = if (isCurrent) 2.dp else 1.dp,
                                            color = if (isCurrent) colors.yellow else colors.border,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable { viewModel.toggleStep(stepIdx) }
                                        .testTag("step_pad_$stepIdx"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${stepIdx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = padTextColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Track 1: Cyber Synth Channel
        item {
            MixerChannelCard(
                channelTag = "CH 1",
                title = "Cyber Synth & Beat Engine",
                accentColor = colors.neon,
                gain = project.synthGain.toFloat(),
                peak = telemetry.synthPeak,
                muted = uiState.synthMuted,
                solo = uiState.synthSolo,
                spatial8D = uiState.synth8DEnabled,
                onGainChange = { v -> viewModel.updateActiveProject { it.copy(synthGain = v.toDouble()) } },
                onToggleMute = viewModel::toggleSynthMute,
                onToggleSolo = viewModel::toggleSynthSolo,
                onToggle8D = viewModel::toggleSynth8D,
            )
        }

        // Track 2: Audio Clip / File Channel
        item {
            MixerChannelCard(
                channelTag = "CH 2",
                title = "Klip Audio Lokal (MP3 / WAV)",
                subtitle = telemetry.loadedClipName,
                accentColor = colors.purple,
                gain = project.clipGain.toFloat(),
                peak = telemetry.clipPeak,
                muted = uiState.clipMuted,
                solo = uiState.clipSolo,
                spatial8D = uiState.clip8DEnabled,
                onGainChange = { v -> viewModel.updateActiveProject { it.copy(clipGain = v.toDouble()) } },
                onToggleMute = viewModel::toggleClipMute,
                onToggleSolo = viewModel::toggleClipSolo,
                onToggle8D = viewModel::toggleClip8D,
                extraContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_audio_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.purple.copy(alpha = 0.22f),
                                contentColor = colors.textPrimary,
                            ),
                            border = BorderStroke(1.dp, colors.purple),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Impor File Audio")
                        }
                        if (telemetry.loadedClipDurationSec > 0f) {
                            Surface(
                                onClick = { viewModel.audioEngine.clearClip() },
                                shape = RoundedCornerShape(12.dp),
                                color = colors.bg,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.AudioFile, contentDescription = null, tint = colors.neon, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reset", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // Track 3: Live Mic + Voice FX + Auto-Ducking
        item {
            MixerChannelCard(
                channelTag = "CH 3",
                title = "Live Mikrofon & Voice FX",
                subtitle = if (telemetry.isMicActive) "ON AIR · Mikrofon Aktif" else "Standby",
                accentColor = colors.pink,
                gain = project.micGain.toFloat(),
                peak = telemetry.micPeak,
                muted = uiState.micMuted,
                solo = uiState.micSolo,
                spatial8D = !uiState.micCenterLock,
                onGainChange = { v -> viewModel.updateActiveProject { it.copy(micGain = v.toDouble()) } },
                onToggleMute = viewModel::toggleMicMute,
                onToggleSolo = viewModel::toggleMicSolo,
                onToggle8D = viewModel::toggleMicCenterLock,
                maxGain = 2.0f,
                extraContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.toggleMicCapture(true)
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("toggle_mic_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (telemetry.isMicActive) colors.live else colors.surfaceElevated,
                                    contentColor = if (telemetry.isMicActive) Color.White else colors.textPrimary,
                                ),
                                border = BorderStroke(1.dp, if (telemetry.isMicActive) colors.live else colors.pink),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (telemetry.isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (telemetry.isMicActive) "Matikan Mic" else "Aktifkan Mic")
                            }

                            FilterChip(
                                selected = uiState.micAutoDucking,
                                onClick = viewModel::toggleMicAutoDucking,
                                label = { Text("Auto-Duck") },
                                modifier = Modifier.height(48.dp),
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            VoiceEffectPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = uiState.voiceEffect == preset,
                                    onClick = { viewModel.setVoiceEffect(preset) },
                                    label = { Text(preset.label) },
                                )
                            }
                        }
                    }
                }
            )
        }

        // Track 4: YouTube WebView Stream Channel
        item {
            MixerChannelCard(
                channelTag = "CH 4",
                title = "YouTube WebView Stream",
                subtitle = when {
                    telemetry.isYouTubeCaptureActive && telemetry.youtubeRouteToDsp ->
                        "ON AIR · Stream Masuk Bus 8D DSP"
                    telemetry.isYouTubeCaptureActive ->
                        "MONITOR FFT · Siap Sampling ke CH 2"
                    else ->
                        project.youtubeTitle.ifBlank { "Referensi YouTube" }
                },
                accentColor = colors.live,
                gain = project.youtubeGain.toFloat(),
                peak = telemetry.youtubePeak,
                muted = uiState.youtubeMuted,
                solo = uiState.youtubeSolo,
                spatial8D = uiState.youtube8DEnabled,
                onGainChange = { v -> viewModel.setYoutubeVolume(v) },
                onToggleMute = viewModel::toggleYoutubeMute,
                onToggleSolo = viewModel::toggleYoutubeSolo,
                onToggle8D = viewModel::toggleYoutube8D,
            )
        }

        // Master Bus Card
        item {
            CyberStudioCard(borderColor = colors.neon.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = colors.neon)
                        Spacer(Modifier.width(8.dp))
                        Text("MASTER BUS OUTPUT", style = MaterialTheme.typography.titleMedium, color = colors.neon)
                    }
                    Text(
                        text = "${(project.masterGain * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neon,
                    )
                }
                PeakMeterBar(level = telemetry.masterPeak)
                Slider(
                    value = project.masterGain.toFloat(),
                    onValueChange = { v -> viewModel.updateActiveProject { it.copy(masterGain = v.toDouble()) } },
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = colors.neon, activeTrackColor = colors.neon),
                )
            }
        }
    }
}

@Composable
private fun MixerChannelCard(
    channelTag: String,
    title: String,
    subtitle: String? = null,
    accentColor: Color,
    gain: Float,
    peak: Float,
    muted: Boolean,
    solo: Boolean,
    spatial8D: Boolean,
    onGainChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onToggle8D: () -> Unit,
    maxGain: Float = 1.5f,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val colors = StudioTheme.colors
    CyberStudioCard(borderColor = accentColor.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(channelTag, style = MaterialTheme.typography.labelMedium, color = accentColor)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChannelToggleChip("M", muted, colors.live, onToggleMute)
                ChannelToggleChip("S", solo, colors.yellow, onToggleSolo)
                ChannelToggleChip("8D", spatial8D, colors.neon, onToggle8D)
            }
        }

        PeakMeterBar(level = peak)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value = gain,
                onValueChange = onGainChange,
                valueRange = 0f..maxGain,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
            )
            Text(
                text = "${(gain * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                modifier = Modifier.width(44.dp),
            )
        }

        if (extraContent != null) {
            extraContent()
        }
    }
}

@Composable
private fun ChannelToggleChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val colors = StudioTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) activeColor.copy(alpha = 0.25f) else colors.bg,
        border = BorderStroke(1.dp, if (active) activeColor else colors.border),
        modifier = Modifier.size(width = 40.dp, height = 36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) activeColor else colors.textMuted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
