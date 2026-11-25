package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.DawTelemetry
import com.example.ui.DawViewModel
import com.example.ui.StudioUiState
import com.example.ui.components.EqSpectrumAnalyzer
import com.example.ui.theme.StudioTheme
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerPluginScreen(
    uiState: StudioUiState,
    telemetry: DawTelemetry,
    viewModel: DawViewModel,
) {
    val colors = StudioTheme.colors
    val project = uiState.activeProject
    val bandLabels = listOf("60 Hz (Sub)", "230 Hz (Low)", "910 Hz (Mid)", "3.6 kHz (Pres)", "12 kHz (Air)")
    val bandColors = listOf(colors.pink, colors.purple, colors.neon, colors.green, colors.yellow)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Real-Time Multi-Track EQ Spectrum Analyzer Component
        item {
            EqSpectrumAnalyzer(
                telemetry = telemetry,
                eqBandsDb = project.eqBandsDb,
                onEqBandChange = { band, db -> viewModel.updateEqBand(band, db) },
            )
        }

        // EQ Presets Row
        item {
            CyberStudioCard {
                Text(
                    text = "PRESET EQUALIZER",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.yellow,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Flat", "Bass Club", "Vocal 8D", "Studio Master").forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.applyEqPreset(preset) },
                            label = { Text(preset) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.purple.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            }
        }

        // 5-Band Parametric Equalizer + Deep Bass Enhancer
        item {
            CyberStudioCard {
                Text(
                    text = "EQUALIZER 5-BAND & BASS BOOSTER",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.pink,
                )

                SliderRow(
                    label = "Deep Bass Enhancer (90 Hz)",
                    valueText = String.format(Locale.US, "+%.1f dB", project.bassBoostDb),
                    value = project.bassBoostDb.toFloat(),
                    range = 0f..18f,
                    accent = colors.pink,
                    onValueChange = { v -> viewModel.updateActiveProject { it.copy(bassBoostDb = v.toDouble()) } },
                )

                for (i in 0..4) {
                    val db = project.eqBandsDb.getOrElse(i) { 0.0 }
                    SliderRow(
                        label = bandLabels[i],
                        valueText = String.format(Locale.US, "%+.1f dB", db),
                        value = db.toFloat(),
                        range = -12f..12f,
                        accent = bandColors[i],
                        onValueChange = { v -> viewModel.updateEqBand(i, v.toDouble()) },
                    )
                }
            }
        }

        // Audio Plugin Insert Rack
        item {
            CyberStudioCard(borderColor = colors.purple.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = colors.purple)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "RACK PLUGIN AUDIO DSP",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.purple,
                    )
                }

                // Plugin 1: Dynamics Compressor
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamics Compressor (4:1)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Text(
                                text = String.format(Locale.US, "Gain Reduction: -%.1f dB", telemetry.compressorGrDb),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.yellow,
                            )
                        }
                        Switch(
                            checked = project.compressorEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateActiveProject { it.copy(compressorEnabled = enabled) }
                            },
                            modifier = Modifier.testTag("compressor_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.neon),
                        )
                    }
                    if (project.compressorEnabled) {
                        SliderRow(
                            label = "Threshold",
                            valueText = String.format(Locale.US, "%.0f dB", project.compressorThresholdDb),
                            value = project.compressorThresholdDb.toFloat(),
                            range = -40f..0f,
                            accent = colors.yellow,
                            onValueChange = { v ->
                                viewModel.updateActiveProject { it.copy(compressorThresholdDb = v.toDouble()) }
                            },
                        )
                    }
                }

                // Plugin 2: Stereo Ping-Pong Delay
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stereo Ping-Pong Delay", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Text(
                                text = "320 ms Stereo Echo",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                        Switch(
                            checked = project.delayEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateActiveProject { it.copy(delayEnabled = enabled) }
                            },
                            modifier = Modifier.testTag("delay_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.purple),
                        )
                    }
                    if (project.delayEnabled) {
                        SliderRow(
                            label = "Delay Wet Mix",
                            valueText = "${(project.delayMix * 100).toInt()}%",
                            value = project.delayMix.toFloat(),
                            range = 0f..1f,
                            accent = colors.purple,
                            onValueChange = { v ->
                                viewModel.updateActiveProject { it.copy(delayMix = v.toDouble()) }
                            },
                        )
                    }
                }

                // Plugin 3: Master Soft-Clip Limiter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Master Soft-Clip Limiter", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        Text(
                            text = "Mencegah distorsi digital saat ekspor & live",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.green,
                        )
                    }
                    Switch(
                        checked = project.limiterEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateActiveProject { it.copy(limiterEnabled = enabled) }
                        },
                        modifier = Modifier.testTag("limiter_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.green),
                    )
                }
            }
        }
    }
}
