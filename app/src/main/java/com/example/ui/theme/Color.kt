package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class DawThemePreset(
    val displayName: String,
    val description: String,
) {
    CYBER_NEON(
        displayName = "Cyber Neon",
        description = "Studio futuristik gelap dengan aksen sian & ungu",
    ),
    ANALOG_WARM(
        displayName = "Analog Console",
        description = "Konsol rekaman klasik dengan meter amber & tembaga",
    ),
    EMERALD_MATRIX(
        displayName = "Matrix Mastering",
        description = "Konsol mastering gelap dengan fosfor hijau presisi",
    ),
    SUNSET_SYNTH(
        displayName = "Vapor Sunset",
        description = "Nuansa retrowave magenta, coral & biru elektrik",
    ),
    DAYLIGHT_PRO(
        displayName = "Studio Light",
        description = "Tema terang kontras tinggi untuk ruangan bercahaya",
    );

    companion object {
        fun fromName(name: String?): DawThemePreset =
            entries.firstOrNull { it.name == name } ?: CYBER_NEON
    }
}

@Immutable
data class StudioPalette(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val neon: Color,
    val purple: Color,
    val pink: Color,
    val yellow: Color,
    val live: Color,
    val green: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val isLight: Boolean,
)

val CyberNeonPalette = StudioPalette(
    bg = Color(0xFF05060D),
    surface = Color(0xFF0C0E1E),
    surfaceElevated = Color(0xFF12162E),
    border = Color(0xFF1B2244),
    neon = Color(0xFF00F3FF),
    purple = Color(0xFFAB00FF),
    pink = Color(0xFFFF0077),
    yellow = Color(0xFFFFCC00),
    live = Color(0xFFFF2A55),
    green = Color(0xFF10F5A0),
    textPrimary = Color(0xFFE6ECFF),
    textMuted = Color(0xFF8C98C2),
    isLight = false,
)

val AnalogWarmPalette = StudioPalette(
    bg = Color(0xFF0E0B09),
    surface = Color(0xFF191410),
    surfaceElevated = Color(0xFF241D17),
    border = Color(0xFF3A2E24),
    neon = Color(0xFFFF9F1C),
    purple = Color(0xFFE07A5F),
    pink = Color(0xFFFF595E),
    yellow = Color(0xFFFFD166),
    live = Color(0xFFFF3B30),
    green = Color(0xFF2EC4B6),
    textPrimary = Color(0xFFF6EFE8),
    textMuted = Color(0xFFB5A495),
    isLight = false,
)

val EmeraldMatrixPalette = StudioPalette(
    bg = Color(0xFF040A07),
    surface = Color(0xFF0A1610),
    surfaceElevated = Color(0xFF10231A),
    border = Color(0xFF1A3A2B),
    neon = Color(0xFF00FF9C),
    purple = Color(0xFF00C2FF),
    pink = Color(0xFFB8FF47),
    yellow = Color(0xFFF4E04D),
    live = Color(0xFFFF3D71),
    green = Color(0xFF34D399),
    textPrimary = Color(0xFFE2FCEE),
    textMuted = Color(0xFF7CA892),
    isLight = false,
)

val SunsetSynthPalette = StudioPalette(
    bg = Color(0xFF11071A),
    surface = Color(0xFF1B0C29),
    surfaceElevated = Color(0xFF28133B),
    border = Color(0xFF42215E),
    neon = Color(0xFF5CE1E6),
    purple = Color(0xFFD946EF),
    pink = Color(0xFFFF4D8D),
    yellow = Color(0xFFFFBE55),
    live = Color(0xFFFF3366),
    green = Color(0xFF38BDF8),
    textPrimary = Color(0xFFF9ECFF),
    textMuted = Color(0xFFB79ACA),
    isLight = false,
)

val DaylightProPalette = StudioPalette(
    bg = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE2E8F0),
    border = Color(0xFFCBD5E1),
    neon = Color(0xFF0284C7),
    purple = Color(0xFF7C3AED),
    pink = Color(0xFFDB2777),
    yellow = Color(0xFFD97706),
    live = Color(0xFFDC2626),
    green = Color(0xFF059669),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF475569),
    isLight = true,
)

fun paletteForPreset(preset: DawThemePreset): StudioPalette = when (preset) {
    DawThemePreset.CYBER_NEON -> CyberNeonPalette
    DawThemePreset.ANALOG_WARM -> AnalogWarmPalette
    DawThemePreset.EMERALD_MATRIX -> EmeraldMatrixPalette
    DawThemePreset.SUNSET_SYNTH -> SunsetSynthPalette
    DawThemePreset.DAYLIGHT_PRO -> DaylightProPalette
}

// Legacy aliases preserved for backward compatibility
val CyberBg = CyberNeonPalette.bg
val CyberSurface = CyberNeonPalette.surface
val CyberSurfaceElevated = CyberNeonPalette.surfaceElevated
val CyberBorder = CyberNeonPalette.border
val CyberNeon = CyberNeonPalette.neon
val CyberPurple = CyberNeonPalette.purple
val CyberPink = CyberNeonPalette.pink
val CyberYellow = CyberNeonPalette.yellow
val CyberLive = CyberNeonPalette.live
val CyberGreen = CyberNeonPalette.green
val CyberTextPrimary = CyberNeonPalette.textPrimary
val CyberTextMuted = CyberNeonPalette.textMuted
