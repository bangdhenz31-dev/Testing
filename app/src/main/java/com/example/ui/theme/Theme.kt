package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalStudioPalette = staticCompositionLocalOf { CyberNeonPalette }

object StudioTheme {
    val colors: StudioPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioPalette.current
}

private fun StudioPalette.toMaterialColorScheme(): ColorScheme {
    val onPrimaryColor = if (neon.luminance() > 0.45f) Color.Black else Color.White
    return if (isLight) {
        lightColorScheme(
            primary = neon,
            onPrimary = onPrimaryColor,
            primaryContainer = neon.copy(alpha = 0.14f),
            onPrimaryContainer = neon,
            secondary = purple,
            onSecondary = Color.White,
            secondaryContainer = purple.copy(alpha = 0.14f),
            onSecondaryContainer = purple,
            tertiary = pink,
            onTertiary = Color.White,
            tertiaryContainer = pink.copy(alpha = 0.14f),
            onTertiaryContainer = pink,
            background = bg,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textMuted,
            outline = border,
            error = live,
            onError = Color.White,
        )
    } else {
        darkColorScheme(
            primary = neon,
            onPrimary = onPrimaryColor,
            primaryContainer = neon.copy(alpha = 0.20f),
            onPrimaryContainer = neon,
            secondary = purple,
            onSecondary = Color.White,
            secondaryContainer = purple.copy(alpha = 0.22f),
            onSecondaryContainer = textPrimary,
            tertiary = pink,
            onTertiary = Color.White,
            tertiaryContainer = pink.copy(alpha = 0.22f),
            onTertiaryContainer = textPrimary,
            background = bg,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textMuted,
            outline = border,
            error = live,
            onError = Color.White,
        )
    }
}

@Composable
fun SpatialDawTheme(
    themePreset: DawThemePreset = DawThemePreset.CYBER_NEON,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val basePalette = paletteForPreset(themePreset)

    val (colorScheme, resolvedPalette) = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynScheme = if (basePalette.isLight) {
            dynamicLightColorScheme(context)
        } else {
            dynamicDarkColorScheme(context)
        }
        val dynPalette = basePalette.copy(
            bg = dynScheme.background,
            surface = dynScheme.surface,
            surfaceElevated = dynScheme.surfaceVariant,
            border = dynScheme.outlineVariant,
            neon = dynScheme.primary,
            purple = dynScheme.secondary,
            pink = dynScheme.tertiary,
            textPrimary = dynScheme.onSurface,
            textMuted = dynScheme.onSurfaceVariant,
        )
        dynScheme to dynPalette
    } else {
        basePalette.toMaterialColorScheme() to basePalette
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = resolvedPalette.isLight
            insetsController.isAppearanceLightNavigationBars = resolvedPalette.isLight
        }
    }

    CompositionLocalProvider(LocalStudioPalette provides resolvedPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudioTypography,
            content = content,
        )
    }
}
