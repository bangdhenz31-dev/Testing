package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.DawTelemetry
import com.example.ui.screens.CyberStudioCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioPalette
import com.example.ui.theme.StudioTheme
import java.util.Locale
import kotlin.math.abs

enum class SpectrumSourceFilter(val label: String) {
    ALL_ACTIVE("Semua Aktif"),
    MASTER("Master Mix"),
    SYNTH("CH1 Synth"),
    CLIP("CH2 Clip"),
    MIC("CH3 Mic"),
    YOUTUBE("CH4 YouTube"),
}

private fun SpectrumSourceFilter.accentColor(colors: StudioPalette): Color = when (this) {
    SpectrumSourceFilter.ALL_ACTIVE -> colors.neon
    SpectrumSourceFilter.MASTER -> colors.green
    SpectrumSourceFilter.SYNTH -> colors.neon
    SpectrumSourceFilter.CLIP -> colors.purple
    SpectrumSourceFilter.MIC -> colors.pink
    SpectrumSourceFilter.YOUTUBE -> colors.live
}

enum class SpectrumRenderMode(val label: String) {
    DUAL_OVERLAY("Overlay + Bar"),
    BARS_AND_PEAKS("Bar + Peak Hold"),
    SPECTRAL_LINE("Kurva Spektrum"),
}

private val EQ_FREQUENCIES = listOf("60Hz", "230Hz", "910Hz", "3.6k", "12k")
private val EQ_BAND_X_FRACTIONS = floatArrayOf(0.08f, 0.28f, 0.50f, 0.72f, 0.92f)

/**
 * Real-time Canvas-based EQ Spectrum Analyzer visualizing active track FFT frequency data
 * with peak-hold caps, logarithmic frequency grid, multi-track overlays, and draggable EQ nodes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqSpectrumAnalyzer(
    telemetry: DawTelemetry,
    eqBandsDb: List<Double>,
    onEqBandChange: (bandIndex: Int, gainDb: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    var sourceFilter by remember { mutableStateOf(SpectrumSourceFilter.ALL_ACTIVE) }
    var renderMode by remember { mutableStateOf(SpectrumRenderMode.DUAL_OVERLAY) }
    var activeDragBand by remember { mutableIntStateOf(-1) }

    val latestOnEqBandChange by rememberUpdatedState(onEqBandChange)

    // Peak-hold array with smooth gravity decay across updates
    val peakHold = remember(sourceFilter) { FloatArray(32) }
    val selectedBars = when (sourceFilter) {
        SpectrumSourceFilter.ALL_ACTIVE, SpectrumSourceFilter.MASTER -> telemetry.spectrumBars
        SpectrumSourceFilter.SYNTH -> telemetry.synthSpectrumBars
        SpectrumSourceFilter.CLIP -> telemetry.clipSpectrumBars
        SpectrumSourceFilter.MIC -> telemetry.micSpectrumBars
        SpectrumSourceFilter.YOUTUBE -> telemetry.youtubeSpectrumBars
    }

    LaunchedEffect(selectedBars, telemetry.isPlaying, sourceFilter) {
        for (i in 0 until 32) {
            val v = selectedBars.getOrElse(i) { 0f }
            if (v >= peakHold[i]) {
                peakHold[i] = v
            } else {
                peakHold[i] = (peakHold[i] - 0.018f).coerceAtLeast(v)
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()

    CyberStudioCard(
        modifier = modifier,
        borderColor = colors.neon.copy(alpha = 0.5f),
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Equalizer, contentDescription = null, tint = colors.neon)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ANALYZER SPEKTRUM EQ WAKTU NYATA",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.neon,
                    )
                    Text(
                        text = "Seret node kurva EQ pada kanvas atau pantau track aktif",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = if (activeDragBand in 0..4) {
                        val db = eqBandsDb.getOrElse(activeDragBand) { 0.0 }
                        String.format(Locale.US, "%s: %+.1f dB", EQ_FREQUENCIES[activeDragBand], db)
                    } else {
                        "48kHz · 256-FFT"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (activeDragBand in 0..4) colors.yellow else colors.green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }

        // Source Track Filter Chips (showing active tracks)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpectrumSourceFilter.entries.forEach { filter ->
                val filterAccent = filter.accentColor(colors)
                val isTrackActive = when (filter) {
                    SpectrumSourceFilter.ALL_ACTIVE, SpectrumSourceFilter.MASTER ->
                        telemetry.isPlaying || telemetry.youtubeTrackActive
                    SpectrumSourceFilter.SYNTH -> telemetry.synthTrackActive
                    SpectrumSourceFilter.CLIP -> telemetry.clipTrackActive
                    SpectrumSourceFilter.MIC -> telemetry.micTrackActive
                    SpectrumSourceFilter.YOUTUBE -> telemetry.youtubeTrackActive
                }
                val selected = sourceFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { sourceFilter = filter },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTrackActive) filterAccent else colors.textMuted.copy(alpha = 0.35f)
                                )
                        )
                    },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("spectrum_filter_${filter.name.lowercase()}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = filterAccent.copy(alpha = 0.22f),
                        selectedLabelColor = filterAccent,
                    ),
                )
            }
        }

        // Render Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpectrumRenderMode.entries.forEach { mode ->
                val selected = renderMode == mode
                Surface(
                    onClick = { renderMode = mode },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) colors.purple.copy(alpha = 0.26f) else colors.bg,
                    border = BorderStroke(1.dp, if (selected) colors.purple else colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("spectrum_mode_${mode.name.lowercase()}"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.textPrimary else colors.textMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Interactive Canvas-Based EQ Spectrum Analyzer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
        ) {
            val activeAccent = sourceFilter.accentColor(colors)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val band = findNearestBand(offset.x, size.width.toFloat())
                            activeDragBand = band
                            val newDb = yToDb(offset.y, size.height.toFloat())
                            latestOnEqBandChange(band, newDb)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activeDragBand = findNearestBand(offset.x, size.width.toFloat())
                            },
                            onDragEnd = { activeDragBand = -1 },
                            onDragCancel = { activeDragBand = -1 },
                            onDrag = { change, _ ->
                                change.consume()
                                val band = if (activeDragBand in 0..4) {
                                    activeDragBand
                                } else {
                                    findNearestBand(change.position.x, size.width.toFloat()).also {
                                        activeDragBand = it
                                    }
                                }
                                val newDb = yToDb(change.position.y, size.height.toFloat())
                                latestOnEqBandChange(band, newDb)
                            },
                        )
                    }
                    .testTag("eq_spectrum_analyzer_canvas"),
            ) {
                val padLeft = 36f
                val padRight = 14f
                val padTop = 14f
                val padBottom = 26f
                val plotW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                val plotH = (size.height - padTop - padBottom).coerceAtLeast(1f)

                drawAnalyzerGrid(
                    textMeasurer = textMeasurer,
                    colors = colors,
                    padLeft = padLeft,
                    padTop = padTop,
                    plotW = plotW,
                    plotH = plotH,
                )

                // Draw primary spectrum visualization (Bars + Peak Hold OR Spectral Line)
                when (renderMode) {
                    SpectrumRenderMode.BARS_AND_PEAKS -> {
                        drawSpectrumBarsWithPeaks(
                            bars = selectedBars,
                            peaks = peakHold,
                            accent = activeAccent,
                            colors = colors,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                        )
                    }
                    SpectrumRenderMode.SPECTRAL_LINE -> {
                        drawSpectralContour(
                            bars = selectedBars,
                            strokeColor = activeAccent,
                            fillAlpha = 0.24f,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                        )
                    }
                    SpectrumRenderMode.DUAL_OVERLAY -> {
                        drawSpectrumBarsWithPeaks(
                            bars = selectedBars,
                            peaks = peakHold,
                            accent = activeAccent,
                            colors = colors,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                            barAlpha = 0.55f,
                        )
                    }
                }

                // Multi-track live overlays when viewing ALL_ACTIVE in DUAL_OVERLAY or SPECTRAL_LINE
                if (sourceFilter == SpectrumSourceFilter.ALL_ACTIVE &&
                    renderMode != SpectrumRenderMode.BARS_AND_PEAKS
                ) {
                    if (telemetry.synthTrackActive) {
                        drawSpectralContour(
                            bars = telemetry.synthSpectrumBars,
                            strokeColor = colors.neon,
                            fillAlpha = 0.14f,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                        )
                    }
                    if (telemetry.clipTrackActive) {
                        drawSpectralContour(
                            bars = telemetry.clipSpectrumBars,
                            strokeColor = colors.purple,
                            fillAlpha = 0.14f,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                        )
                    }
                    if (telemetry.micTrackActive) {
                        drawSpectralContour(
                            bars = telemetry.micSpectrumBars,
                            strokeColor = colors.pink,
                            fillAlpha = 0.18f,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                        )
                    }
                    if (telemetry.youtubeTrackActive) {
                        drawSpectralContour(
                            bars = telemetry.youtubeSpectrumBars,
                            strokeColor = colors.live,
                            fillAlpha = 0.16f,
                            padLeft = padLeft,
                            padTop = padTop,
                            plotW = plotW,
                            plotH = plotH,
                        )
                    }
                }

                // Draw interactive 5-Band EQ response curve & draggable nodes
                drawEqCurveAndNodes(
                    eqBandsDb = eqBandsDb,
                    activeDragBand = activeDragBand,
                    colors = colors,
                    padLeft = padLeft,
                    padTop = padTop,
                    plotW = plotW,
                    plotH = plotH,
                )
            }
        }

        // Active Tracks Spectral Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot("Synth", colors.neon, telemetry.synthTrackActive)
                LegendDot("Klip", colors.purple, telemetry.clipTrackActive)
                LegendDot("Mic", colors.pink, telemetry.micTrackActive)
                LegendDot("YT", colors.live, telemetry.youtubeTrackActive)
                LegendDot("EQ", colors.yellow, true)
            }
            Text(
                text = if (telemetry.isPlaying || telemetry.youtubeTrackActive) "LIVE FFT" else "STANDBY",
                style = MaterialTheme.typography.labelSmall,
                color = if (telemetry.isPlaying || telemetry.youtubeTrackActive) colors.green else colors.textMuted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color, active: Boolean) {
    val colors = StudioTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) color else color.copy(alpha = 0.28f))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) colors.textPrimary else colors.textMuted,
        )
    }
}

private fun findNearestBand(xPx: Float, totalWidthPx: Float): Int {
    val padLeft = 36f
    val padRight = 14f
    val plotW = (totalWidthPx - padLeft - padRight).coerceAtLeast(1f)
    val normX = ((xPx - padLeft) / plotW).coerceIn(0f, 1f)
    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    for (i in EQ_BAND_X_FRACTIONS.indices) {
        val d = abs(normX - EQ_BAND_X_FRACTIONS[i])
        if (d < bestDist) {
            bestDist = d
            bestIdx = i
        }
    }
    return bestIdx
}

private fun yToDb(yPx: Float, totalHeightPx: Float): Double {
    val padTop = 14f
    val padBottom = 26f
    val plotH = (totalHeightPx - padTop - padBottom).coerceAtLeast(1f)
    val normY = ((yPx - padTop) / plotH).coerceIn(0f, 1f)
    return ((0.5f - normY) * 24.0).coerceIn(-12.0, 12.0)
}

private fun DrawScope.drawAnalyzerGrid(
    textMeasurer: TextMeasurer,
    colors: StudioPalette,
    padLeft: Float,
    padTop: Float,
    plotW: Float,
    plotH: Float,
) {
    val labelStyle = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 9.sp,
        color = colors.textMuted,
    )
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    val dbLabels = listOf("+12", "+6", "0dB", "-6", "-12")

    // Horizontal dB lines
    dbLabels.forEachIndexed { idx, label ->
        val y = padTop + (idx / 4f) * plotH
        val isZeroLine = idx == 2
        drawLine(
            color = if (isZeroLine) colors.neon.copy(alpha = 0.38f) else colors.border.copy(alpha = 0.7f),
            start = Offset(padLeft, y),
            end = Offset(padLeft + plotW, y),
            strokeWidth = if (isZeroLine) 1.5f else 1f,
            pathEffect = if (isZeroLine) null else dashEffect,
        )
        val measured = textMeasurer.measure(label, style = labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(4f, (y - measured.size.height * 0.5f).coerceAtLeast(0f)),
        )
    }

    // Vertical frequency band lines + bottom frequency labels
    EQ_BAND_X_FRACTIONS.forEachIndexed { idx, frac ->
        val x = padLeft + frac * plotW
        drawLine(
            color = colors.border.copy(alpha = 0.65f),
            start = Offset(x, padTop),
            end = Offset(x, padTop + plotH),
            strokeWidth = 1f,
            pathEffect = dashEffect,
        )
        val measured = textMeasurer.measure(EQ_FREQUENCIES[idx], style = labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                (x - measured.size.width * 0.5f).coerceIn(padLeft, padLeft + plotW - measured.size.width),
                padTop + plotH + 6f,
            ),
        )
    }
}

private fun DrawScope.drawSpectrumBarsWithPeaks(
    bars: FloatArray,
    peaks: FloatArray,
    accent: Color,
    colors: StudioPalette,
    padLeft: Float,
    padTop: Float,
    plotW: Float,
    plotH: Float,
    barAlpha: Float = 0.85f,
) {
    val count = bars.size.coerceAtLeast(1)
    val slotW = plotW / count
    val barW = (slotW * 0.7f).coerceAtLeast(2f)

    val barBrush = Brush.verticalGradient(
        colors = listOf(
            colors.pink.copy(alpha = barAlpha),
            colors.purple.copy(alpha = barAlpha),
            accent.copy(alpha = barAlpha),
        ),
        startY = padTop,
        endY = padTop + plotH,
    )

    for (i in 0 until count) {
        val amp = bars[i].coerceIn(0.015f, 1f)
        val barH = amp * plotH
        val x = padLeft + i * slotW + (slotW - barW) * 0.5f
        val topY = padTop + plotH - barH

        drawRoundRect(
            brush = barBrush,
            topLeft = Offset(x, topY),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(3f, 3f),
        )

        // Peak hold cap
        val peakVal = peaks.getOrElse(i) { 0f }.coerceIn(0.02f, 1f)
        val peakY = (padTop + plotH - peakVal * plotH).coerceIn(padTop, padTop + plotH - 2f)
        drawLine(
            color = colors.yellow.copy(alpha = 0.9f),
            start = Offset(x, peakY),
            end = Offset(x + barW, peakY),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSpectralContour(
    bars: FloatArray,
    strokeColor: Color,
    fillAlpha: Float,
    padLeft: Float,
    padTop: Float,
    plotW: Float,
    plotH: Float,
) {
    val count = bars.size
    if (count < 2) return

    val points = List(count) { i ->
        val x = padLeft + (i.toFloat() / (count - 1)) * plotW
        val amp = bars[i].coerceIn(0f, 1f)
        val y = padTop + plotH - amp * plotH
        Offset(x, y)
    }

    val linePath = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.lastIndex) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cx = (p0.x + p1.x) * 0.5f
            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
    }

    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(points.last().x, padTop + plotH)
        lineTo(points.first().x, padTop + plotH)
        close()
    }

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(strokeColor.copy(alpha = fillAlpha), Color.Transparent),
            startY = padTop,
            endY = padTop + plotH,
        ),
    )
    drawPath(
        path = linePath,
        color = strokeColor,
        style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawEqCurveAndNodes(
    eqBandsDb: List<Double>,
    activeDragBand: Int,
    colors: StudioPalette,
    padLeft: Float,
    padTop: Float,
    plotW: Float,
    plotH: Float,
) {
    val zeroY = padTop + plotH * 0.5f
    val eqBandColors = listOf(colors.pink, colors.purple, colors.neon, colors.green, colors.yellow)
    val points = EQ_BAND_X_FRACTIONS.mapIndexed { idx, frac ->
        val db = eqBandsDb.getOrElse(idx) { 0.0 }.toFloat().coerceIn(-12f, 12f)
        val x = padLeft + frac * plotW
        val y = zeroY - (db / 12f) * (plotH * 0.48f)
        Offset(x, y)
    }

    val curvePath = Path().apply {
        moveTo(padLeft, points.first().y)
        lineTo(points.first().x, points.first().y)
        for (i in 0 until points.lastIndex) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cx = (p0.x + p1.x) * 0.5f
            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
        lineTo(padLeft + plotW, points.last().y)
    }

    val areaPath = Path().apply {
        addPath(curvePath)
        lineTo(padLeft + plotW, zeroY)
        lineTo(padLeft, zeroY)
        close()
    }

    drawPath(
        path = areaPath,
        color = colors.yellow.copy(alpha = 0.14f),
    )
    drawPath(
        path = curvePath,
        color = colors.yellow,
        style = Stroke(width = 3.2f, cap = StrokeCap.Round),
    )

    points.forEachIndexed { idx, pt ->
        val nodeColor = eqBandColors[idx]
        val isDragging = idx == activeDragBand
        drawCircle(
            color = nodeColor.copy(alpha = if (isDragging) 0.45f else 0.25f),
            radius = if (isDragging) 18f else 13f,
            center = pt,
        )
        drawCircle(
            color = nodeColor,
            radius = if (isDragging) 9f else 7f,
            center = pt,
        )
        drawCircle(
            color = colors.textPrimary,
            radius = 3f,
            center = pt,
        )
    }
}
