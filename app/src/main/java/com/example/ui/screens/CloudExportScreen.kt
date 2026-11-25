package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.DawProject
import com.example.data.ProjectNote
import com.example.data.ThemeSettings
import com.example.ui.DawViewModel
import com.example.ui.StudioUiState
import com.example.ui.UiState
import com.example.ui.components.ThemePickerOptions
import com.example.ui.theme.DawThemePreset
import com.example.ui.theme.StudioTheme

@Composable
fun CloudExportScreen(
    currentUserId: String,
    uiState: StudioUiState,
    projectsState: UiState<List<DawProject>>,
    notesState: UiState<List<ProjectNote>>,
    viewModel: DawViewModel,
    themeSettings: ThemeSettings,
    onSelectPreset: (DawThemePreset) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = StudioTheme.colors
    val onNeon = if (colors.neon.luminance() > 0.45f) Color.Black else Color.White
    val onGreen = if (colors.green.luminance() > 0.45f) Color.Black else Color.White

    var selectedBitDepth by remember { mutableIntStateOf(24) }
    var newProjectTitle by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var noteBar by remember { mutableIntStateOf(1) }

    val saveWavLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportAudioMix(context, uri, selectedBitDepth)
            }
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Studio Theme Settings Card
        item {
            CyberStudioCard(borderColor = colors.purple.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = colors.purple)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "TEMA STUDIO & TAMPILAN",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.purple,
                        )
                    }
                    Text(
                        text = themeSettings.preset.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.neon,
                        fontWeight = FontWeight.Bold,
                    )
                }

                ThemePickerOptions(
                    themeSettings = themeSettings,
                    onSelectPreset = onSelectPreset,
                    onToggleDynamicColor = onToggleDynamicColor,
                )
            }
        }

        // High-Quality Audio Export Card
        item {
            CyberStudioCard(borderColor = colors.green.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "EKSPOR AUDIO KUALITAS TINGGI",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.green,
                    )
                    Text(
                        text = "48 kHz Stereo",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.yellow,
                    )
                }

                Text(
                    text = "Render offline melalui rantai DSP lengkap (EQ 5-Band, Bass Boost, Compressor, Delay, 8D Spatial & Limiter).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = selectedBitDepth == 16,
                        onClick = { selectedBitDepth = 16 },
                        label = { Text("16-bit PCM WAV") },
                    )
                    FilterChip(
                        selected = selectedBitDepth == 24,
                        onClick = { selectedBitDepth = 24 },
                        label = { Text("24-bit Studio WAV") },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            val cleanName = uiState.activeProject.title
                                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                .ifBlank { "SpatialDawMix" }
                            saveWavLauncher.launch("${cleanName}_${selectedBitDepth}bit.wav")
                        },
                        enabled = !uiState.isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_save_wav_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.green,
                            contentColor = onGreen,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = onGreen, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Simpan WAV", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val file = uiState.lastExportedFile
                            if (file != null && file.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/wav"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Audio WAV"))
                            } else {
                                viewModel.exportAudioMix(context, null, selectedBitDepth)
                            }
                        },
                        enabled = !uiState.isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_render_share_button"),
                        border = BorderStroke(1.dp, colors.neon),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = colors.neon, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (uiState.lastExportedFile != null) "Bagikan WAV" else "Render Master",
                            color = colors.neon,
                        )
                    }
                }
            }
        }

        // Cloud Project Creation & Invite Join
        item {
            CyberStudioCard(borderColor = colors.neon.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = colors.neon)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "KOLABORASI PROYEK CLOUD",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.neon,
                    )
                }

                if (uiState.activeProject.id.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bg)
                            .border(1.dp, colors.purple.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kode Undangan Kolaborasi (${uiState.activeProject.memberUids.size} anggota)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                            Text(
                                text = uiState.activeProject.inviteCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.yellow,
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(uiState.activeProject.inviteCode))
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Kode Undangan", tint = colors.neon)
                        }
                    }
                }

                // New Cloud Project Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newProjectTitle,
                        onValueChange = { newProjectTitle = it },
                        label = { Text("Nama Proyek Baru") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_project_title_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.neon,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                    Button(
                        onClick = {
                            viewModel.createCloudProject(newProjectTitle)
                            newProjectTitle = ""
                        },
                        enabled = !uiState.isSyncingCloud,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("create_project_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.neon, contentColor = onNeon),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Buat Proyek")
                    }
                }

                // Join Invite Code Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase() },
                        label = { Text("Gabung Kode Undangan") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("join_invite_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.purple,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                    Button(
                        onClick = {
                            viewModel.joinProjectByCode(inviteCodeInput)
                            inviteCodeInput = ""
                        },
                        enabled = inviteCodeInput.trim().length >= 6 && !uiState.isSyncingCloud,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("join_project_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.purple, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = "Gabung Proyek")
                    }
                }
            }
        }

        // Cloud Projects List
        item {
            Text(
                text = "PROYEK CLOUD TERHUBUNG",
                style = MaterialTheme.typography.titleMedium,
                color = colors.yellow,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        when (projectsState) {
            UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.neon)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    CyberStudioCard(borderColor = colors.live) {
                        Text(projectsState.message, color = colors.live)
                    }
                }
            }
            is UiState.Success -> {
                if (projectsState.data.isEmpty()) {
                    item {
                        CyberStudioCard {
                            Text(
                                text = "Belum ada proyek tersimpan. Buat proyek baru di atas untuk menyinkronkan mix dan berkolaborasi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                        }
                    }
                } else {
                    items(projectsState.data, key = { it.id }) { proj ->
                        val isActive = proj.id == uiState.activeProject.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) colors.surfaceElevated else colors.bg,
                            border = BorderStroke(1.dp, if (isActive) colors.neon else colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.loadProjectIntoEngine(proj) },
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = proj.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isActive) colors.neon else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${proj.bpm} BPM · ${proj.spatialPattern} · Kode: ${proj.inviteCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMuted,
                                    )
                                }
                                if (proj.ownerId == currentUserId) {
                                    IconButton(
                                        onClick = { viewModel.deleteCloudProject(proj) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Hapus Proyek", tint = colors.live)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Producer Notes for Active Cloud Project
        if (uiState.activeProject.id.isNotBlank()) {
            item {
                CyberStudioCard(borderColor = colors.pink.copy(alpha = 0.5f)) {
                    Text(
                        text = "CATATAN KOLABORASI PRODUSER",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.pink,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text("Tulis catatan mix (Bar $noteBar)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("collab_note_input"),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Button(
                            onClick = {
                                viewModel.addCollaborationNote(noteInput, noteBar.toLong())
                                noteInput = ""
                                noteBar = (noteBar % 16) + 1
                            },
                            enabled = noteInput.isNotBlank(),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("send_note_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.pink, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Kirim Catatan")
                        }
                    }

                    when (notesState) {
                        is UiState.Success -> {
                            if (notesState.data.isEmpty()) {
                                Text(
                                    text = "Belum ada catatan produser pada proyek ini.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textMuted,
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    notesState.data.take(10).forEach { note ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.bg)
                                                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = "Bar ${note.barPosition}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.yellow,
                                            )
                                            Column {
                                                Text(note.authorName, style = MaterialTheme.typography.labelMedium, color = colors.neon)
                                                Text(note.message, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is UiState.Error -> {
                            Text(notesState.message, color = colors.live, style = MaterialTheme.typography.labelMedium)
                        }
                        UiState.Loading -> {
                            CircularProgressIndicator(Modifier.size(20.dp), color = colors.pink)
                        }
                    }
                }
            }
        }
    }
}
