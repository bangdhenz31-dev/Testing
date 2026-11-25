package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.DawTelemetry
import com.example.data.SpatialPattern
import com.example.ui.DawViewModel
import com.example.ui.StudioUiState
import com.example.ui.theme.StudioTheme
import java.util.Locale
import kotlin.math.hypot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpatialRadarScreen(
    uiState: StudioUiState,
    telemetry: DawTelemetry,
    viewModel: DawViewModel,
) {
    val colors = StudioTheme.colors
    val project = uiState.activeProject
    val activePattern = SpatialPattern.fromRaw(project.spatialPattern)

    val spatialX = telemetry.emitterNormX * project.orbitRadiusMeters.toFloat()
    val spatialZ = telemetry.emitterNormY * project.orbitRadiusMeters.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Interactive 360° Binaural Spatial Radar
        item {
            CyberStudioCard(borderColor = colors.neon.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Radar, contentDescription = null, tint = colors.neon)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "RADAR SPASIAL 8D / 9D",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.neon,
                        )
                    }
                    Button(
                        onClick = viewModel::toggleAutoOrbit,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("toggle_orbit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.autoOrbit) colors.neon.copy(alpha = 0.2f) else colors.yellow.copy(alpha = 0.2f),
                            contentColor = if (uiState.autoOrbit) colors.neon else colors.yellow,
                        ),
                        border = BorderStroke(1.dp, if (uiState.autoOrbit) colors.neon else colors.yellow),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = if (uiState.autoOrbit) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (uiState.autoOrbit) "Orbit Auto" else "Manual",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                Text(
                    text = "Sentuh atau geser titik indikator pada radar untuk mengatur posisi suara 360°.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.bg)
                            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cx = size.width * 0.5f
                                    val cy = size.height * 0.5f
                                    val maxR = size.width * 0.42f
                                    var dx = (offset.x - cx) / maxR
                                    var dy = (offset.y - cy) / maxR
                                    val d = hypot(dx, dy)
                                    if (d > 1f) {
                                        dx /= d; dy /= d
                                    }
                                    viewModel.setManualRadarPosition(dx, dy)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val cx = size.width * 0.5f
                                    val cy = size.height * 0.5f
                                    val maxR = size.width * 0.42f
                                    var dx = (change.position.x - cx) / maxR
                                    var dy = (change.position.y - cy) / maxR
                                    val d = hypot(dx, dy)
                                    if (d > 1f) {
                                        dx /= d; dy /= d
                                    }
                                    viewModel.setManualRadarPosition(dx, dy)
                                }
                            }
                            .testTag("spatial_radar_canvas"),
                    ) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val maxR = size.width * 0.42f
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

                        // Concentric rings
                        listOf(0.25f, 0.5f, 0.75f, 1f).forEachIndexed { idx, ratio ->
                            drawCircle(
                                color = if (idx == 3) colors.neon.copy(alpha = 0.5f) else colors.neon.copy(alpha = 0.18f),
                                radius = maxR * ratio,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2f, pathEffect = dash),
                            )
                        }

                        // Crosshairs
                        drawLine(colors.neon.copy(alpha = 0.25f), Offset(cx, 12f), Offset(cx, size.height - 12f), 1.5f)
                        drawLine(colors.neon.copy(alpha = 0.25f), Offset(12f, cy), Offset(size.width - 12f, cy), 1.5f)

                        // Emitter coordinates
                        val ex = cx + telemetry.emitterNormX.coerceIn(-1f, 1f) * maxR
                        val ey = cy + telemetry.emitterNormY.coerceIn(-1f, 1f) * maxR

                        // Vector beam from listener to sound source
                        drawLine(
                            color = colors.yellow.copy(alpha = 0.6f),
                            start = Offset(cx, cy),
                            end = Offset(ex, ey),
                            strokeWidth = 3f,
                        )

                        // Listener head center node
                        drawCircle(color = colors.purple.copy(alpha = 0.35f), radius = 26f, center = Offset(cx, cy))
                        drawCircle(color = colors.purple, radius = 16f, center = Offset(cx, cy))
                        drawCircle(color = colors.textPrimary, radius = 5f, center = Offset(cx, cy))

                        // Orbiting 8D Sound Emitter node
                        drawCircle(color = colors.yellow.copy(alpha = 0.28f), radius = 28f, center = Offset(ex, ey))
                        drawCircle(color = colors.yellow, radius = 16f, center = Offset(ex, ey))
                        drawCircle(color = colors.bg, radius = 5f, center = Offset(ex, ey))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Headphones, contentDescription = null, tint = colors.purple, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Binaural ITD + ILD",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.purple,
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "X: %.1fm  |  Z: %.1fm", spatialX, spatialZ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neon,
                    )
                }
            }
        }

        // Spatial Motion Patterns & Controls
        item {
            CyberStudioCard {
                Text(
                    text = "POLA GERAKAN SPASIAL",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.neon,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpatialPattern.entries.forEach { pattern ->
                        val selected = activePattern == pattern
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setSpatialPattern(pattern) },
                            label = { Text(pattern.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.neon.copy(alpha = 0.22f),
                                selectedLabelColor = colors.neon,
                            ),
                        )
                    }
                }

                // Orbit Speed
                SliderRow(
                    label = "Kecepatan Putar (Hz)",
                    valueText = String.format(Locale.US, "%.2f Hz", project.orbitSpeedHz),
                    value = project.orbitSpeedHz.toFloat(),
                    range = 0.05f..1.50f,
                    accent = colors.neon,
                    onValueChange = { v -> viewModel.updateActiveProject { it.copy(orbitSpeedHz = v.toDouble()) } },
                )

                // Soundstage Radius
                SliderRow(
                    label = "Jarak Soundstage (Radius)",
                    valueText = String.format(Locale.US, "%.1f m", project.orbitRadiusMeters),
                    value = project.orbitRadiusMeters.toFloat(),
                    range = 1.0f..10.0f,
                    accent = colors.purple,
                    onValueChange = { v -> viewModel.updateActiveProject { it.copy(orbitRadiusMeters = v.toDouble()) } },
                )

                // 9D Cathedral Reverb
                SliderRow(
                    label = "9D Cathedral Reverb",
                    valueText = "${(project.reverbMix * 100).toInt()}%",
                    value = project.reverbMix.toFloat(),
                    range = 0f..1f,
                    accent = colors.pink,
                    onValueChange = { v -> viewModel.updateActiveProject { it.copy(reverbMix = v.toDouble()) } },
                )
            }
        }
    }
}

@Composable
internal fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    val colors = StudioTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
    }
}
