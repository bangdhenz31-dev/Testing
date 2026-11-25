package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SpatialPattern(val label: String) {
    CIRCULAR("8D Circular"),
    WAVE("9D Wave"),
    FIGURE8("Figure 8"),
    PULSE("Binaural Pulse");

    companion object {
        fun fromRaw(raw: String): SpatialPattern =
            entries.firstOrNull { it.name == raw } ?: CIRCULAR
    }
}

enum class VoiceEffectPreset(val label: String) {
    NORMAL("Normal"),
    RADIO("Radio DJ"),
    STUDIO("Studio Clear"),
    CYBER("Cyber Pitch")
}

@Keep
@IgnoreExtraProperties
data class DawProject(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val title: String = "Midnight 8D Session",
    val bpm: Long = 124L,
    val spatialPattern: String = SpatialPattern.CIRCULAR.name,
    val orbitSpeedHz: Double = 0.25,
    val orbitRadiusMeters: Double = 5.0,
    val reverbMix: Double = 0.45,
    val bassBoostDb: Double = 5.0,
    val eqBandsDb: List<Double> = listOf(3.0, 1.0, 0.0, 1.5, 2.5),
    val compressorEnabled: Boolean = true,
    val compressorThresholdDb: Double = -14.0,
    val delayEnabled: Boolean = false,
    val delayMix: Double = 0.25,
    val limiterEnabled: Boolean = true,
    val latencyFrames: Long = 256L,
    val youtubeVideoId: String = "jfKfPfyJRdk",
    val youtubeTitle: String = "lofi hip hop radio - beats to relax/study to",
    val synthGain: Double = 0.75,
    val clipGain: Double = 0.85,
    val micGain: Double = 0.90,
    val youtubeGain: Double = 0.85,
    val masterGain: Double = 0.85,
    val stepPattern: List<Long> = listOf(3L, 0L, 2L, 0L, 3L, 0L, 1L, 2L, 3L, 0L, 2L, 0L, 3L, 1L, 2L, 1L),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ProjectInvite(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val projectTitle: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ProjectNote(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val message: String = "",
    val barPosition: Long = 1L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class YouTubePresetVideo(
    val id: String,
    val title: String,
    val channel: String,
    val category: String,
)

object YouTubeCatalog {
    val presets = listOf(
        YouTubePresetVideo(
            id = "jfKfPfyJRdk",
            title = "lofi hip hop radio - beats to relax/study to",
            channel = "Lofi Girl",
            category = "Lofi & Chill"
        ),
        YouTubePresetVideo(
            id = "4xDzrJKXOOY",
            title = "SYNTHWAVE / CYBERPUNK Mix - Retrowave 80s Sound",
            channel = "Nightride FM",
            category = "Synthwave"
        ),
        YouTubePresetVideo(
            id = "1ZYbU85NO6w",
            title = "8D Audio - Celestial Sound Experience (Headphones)",
            channel = "8D Audio World",
            category = "8D Audio"
        ),
        YouTubePresetVideo(
            id = "fJ9rUzIMcZQ",
            title = "Queen - Bohemian Rhapsody (8D Spatial Mix)",
            channel = "8D Music",
            category = "8D Audio"
        ),
        YouTubePresetVideo(
            id = "UeaS_O006d0",
            title = "Orchestral Epic Soundtrack Reference",
            channel = "Gaming OST",
            category = "Gaming OST"
        ),
        YouTubePresetVideo(
            id = "5qap5aO4i9A",
            title = "Chillhop Beats & Drum Groove Reference",
            channel = "Chillhop Music",
            category = "Lofi & Chill"
        ),
    )
}
