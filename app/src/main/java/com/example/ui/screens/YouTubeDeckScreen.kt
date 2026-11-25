package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.audio.DawTelemetry
import com.example.data.YouTubePresetVideo
import com.example.ui.DawViewModel
import com.example.ui.StudioUiState
import com.example.ui.components.YouTubePlayerDeck
import com.example.ui.theme.StudioTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouTubeDeckScreen(
    uiState: StudioUiState,
    telemetry: DawTelemetry,
    viewModel: DawViewModel,
) {
    val colors = StudioTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val activeVideoId = uiState.activeProject.youtubeVideoId
        .takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
        ?: "jfKfPfyJRdk"

    var selectedCategory by remember { mutableStateOf("Semua") }

    val filteredVideos = remember(uiState.youtubeResults, selectedCategory) {
        if (selectedCategory == "Semua") {
            uiState.youtubeResults
        } else {
            uiState.youtubeResults.filter { it.category == selectedCategory }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Integrated WebView YouTube Player, Live Search Browser & Audio Stream Sampler
        item {
            YouTubePlayerDeck(
                uiState = uiState,
                telemetry = telemetry,
                viewModel = viewModel,
            )
        }

        // YouTube URL / Live Search Bar + Category Filter
        item {
            CyberStudioCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.youtubeSearchQuery,
                        onValueChange = viewModel::updateYoutubeQuery,
                        label = { Text("Cari lagu atau tempel Link/ID YouTube") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.neon)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                viewModel.searchYouTubeInWebView()
                            },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("youtube_search_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.neon,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.bg,
                            unfocusedContainerColor = colors.bg,
                        ),
                    )

                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.searchYouTubeInWebView()
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("youtube_search_submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.live,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Cari di YouTube")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Semua", "Lofi & Chill", "Synthwave", "8D Audio", "Gaming OST").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                        )
                    }
                }
            }
        }

        // Preset Reference Track Cards
        items(filteredVideos, key = { it.id }) { video ->
            YouTubeTrackItemCard(
                video = video,
                isActive = video.id == activeVideoId,
                onSelect = { viewModel.selectYouTubeVideo(video) },
            )
        }
    }
}

@Composable
private fun YouTubeTrackItemCard(
    video: YouTubePresetVideo,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    val colors = StudioTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) colors.surfaceElevated else colors.bg,
        border = BorderStroke(1.dp, if (isActive) colors.neon else colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("youtube_card_${video.id}"),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 104.dp, height = 60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) colors.neon else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${video.channel} · ${video.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Putar Video",
                tint = if (isActive) colors.neon else colors.textMuted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
