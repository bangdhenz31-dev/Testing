package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioTheme
import java.util.Locale

@Composable
fun CyberStudioCard(
    modifier: Modifier = Modifier,
    borderColor: Color = StudioTheme.colors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StudioTheme.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun DawTransportHeader(
    projectTitle: String,
    bpm: Long,
    latencyMs: Float,
    latencyFrames: Long,
    isPlaying: Boolean,
    isSavingProject: Boolean,
    onTogglePlay: () -> Unit,
    onBpmDelta: (Long) -> Unit,
    onOpenThemePicker: () -> Unit,
    onSaveProject: () -> Unit,
) {
    val colors = StudioTheme.colors
    val playBg = if (isPlaying) colors.pink else colors.neon
    val playIconTint = if (playBg.luminance() > 0.45f) Color.Black else Color.White

    Surface(
        color = colors.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SPATIAL DAW STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.neon,
                    )
                    Text(
                        text = projectTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = onOpenThemePicker,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("theme_picker_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = "Pilih Tema Studio",
                            tint = colors.purple,
                        )
                    }

                    IconButton(
                        onClick = onSaveProject,
                        enabled = !isSavingProject,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("save_project_button"),
                    ) {
                        if (isSavingProject) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.neon,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Simpan Proyek Lokal",
                                tint = colors.neon,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Play / Stop master transport
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = playBg,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("master_play_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Hentikan Audio" else "Putar Audio",
                            tint = playIconTint,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                // BPM Controller
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bg)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(
                        onClick = { onBpmDelta(-2L) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Kurangi BPM", tint = colors.neon)
                    }
                    Text(
                        text = "$bpm BPM",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.yellow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = { onBpmDelta(2L) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah BPM", tint = colors.neon)
                    }
                }

                // Low-latency badge
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f ms", latencyMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.green,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${latencyFrames}f DSP",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun SpectrumVisualizerCanvas(
    bars: FloatArray,
    eqBandsDb: List<Double>? = null,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Subtle horizontal dB grid lines
        for (i in 1..3) {
            val y = h * (i / 4f)
            drawLine(
                color = colors.border.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        val count = bars.size.coerceAtLeast(1)
        val slotW = w / count
        val barW = (slotW * 0.72f).coerceAtLeast(2f)

        val brush = Brush.verticalGradient(
            colors = listOf(colors.pink, colors.purple, colors.neon)
        )

        for (i in 0 until count) {
            val amp = bars[i].coerceIn(0.03f, 1f)
            val barH = amp * (h - 8f)
            val x = i * slotW + (slotW - barW) * 0.5f
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, h - barH - 4f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }

        // Optional 5-band EQ frequency response curve overlay
        if (eqBandsDb != null && eqBandsDb.size == 5) {
            val path = Path()
            val midY = h * 0.5f
            val points = eqBandsDb.mapIndexed { idx, db ->
                val px = (idx / 4f) * (w - 24f) + 12f
                val py = midY - (db.toFloat() / 12f) * (h * 0.38f)
                Offset(px, py)
            }
            path.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
            drawPath(
                path = path,
                color = colors.yellow,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round),
            )
            points.forEach { pt ->
                drawCircle(color = colors.textPrimary, radius = 6f, center = pt)
                drawCircle(color = colors.yellow, radius = 4f, center = pt)
            }
        }
    }
}

@Composable
fun PeakMeterBar(
    level: Float,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val clamped = level.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.green, colors.neon, colors.yellow, colors.pink)
                    )
                )
        )
    }
}
