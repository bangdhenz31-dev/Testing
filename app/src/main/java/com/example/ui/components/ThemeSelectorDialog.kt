package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.ThemeSettings
import com.example.ui.theme.DawThemePreset
import com.example.ui.theme.StudioTheme
import com.example.ui.theme.paletteForPreset

@Composable
fun ThemeSelectorDialog(
    themeSettings: ThemeSettings,
    onSelectPreset: (DawThemePreset) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = StudioTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.neon.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = colors.neon)
                    Text(
                        text = "TEMA STUDIO DAW",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.neon,
                    )
                }

                Text(
                    text = "Pilih tampilan konsol studio yang sesuai dengan suasana produksi Anda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )

                ThemePickerOptions(
                    themeSettings = themeSettings,
                    onSelectPreset = onSelectPreset,
                    onToggleDynamicColor = onToggleDynamicColor,
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("theme_dialog_close_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.neon,
                        contentColor = if (colors.neon.luminance() > 0.45f) Color.Black else Color.White,
                    ),
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ThemePickerOptions(
    themeSettings: ThemeSettings,
    onSelectPreset: (DawThemePreset) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
) {
    val colors = StudioTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DawThemePreset.entries.forEach { preset ->
            val preview = paletteForPreset(preset)
            val selected = themeSettings.preset == preset

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) colors.surfaceElevated else colors.bg,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) colors.neon else colors.border,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPreset(preset) }
                    .testTag("theme_option_${preset.name.lowercase()}"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Color Swatch Preview Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(preview.bg)
                            .border(1.dp, preview.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SwatchDot(preview.neon)
                        SwatchDot(preview.purple)
                        SwatchDot(preview.pink)
                        SwatchDot(preview.green)
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) colors.neon else colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }

                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Tema Aktif",
                            tint = colors.neon,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.bg,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Material You (Warna Sistem)",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Ikuti palet wallpaper perangkat Android 12+",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                    Switch(
                        checked = themeSettings.useDynamicColor,
                        onCheckedChange = onToggleDynamicColor,
                        modifier = Modifier.testTag("dynamic_color_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.neon),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
