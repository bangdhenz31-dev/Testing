package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioClipDecoder
import com.example.audio.DawAudioEngine
import com.example.audio.DawTelemetry
import com.example.data.DawProject
import com.example.data.DawRepository
import com.example.data.ProjectNote
import com.example.data.SpatialPattern
import com.example.data.VoiceEffectPreset
import com.example.data.YouTubeCatalog
import com.example.data.YouTubePresetVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

enum class YouTubeDeckMode {
    PLAYER,
    LIVE_SEARCH,
}

sealed interface YouTubePlayerCommand {
    data class LoadVideo(val videoId: String) : YouTubePlayerCommand
    data object Play : YouTubePlayerCommand
    data object Pause : YouTubePlayerCommand
    data class SeekTo(val seconds: Float) : YouTubePlayerCommand
    data class SetPlaybackRate(val rate: Float) : YouTubePlayerCommand
    data class SetVolume(val percent: Int) : YouTubePlayerCommand
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class StudioUiState(
    val activeProject: DawProject = DawProject(),
    val autoOrbit: Boolean = true,
    val synthMuted: Boolean = false,
    val synthSolo: Boolean = false,
    val synth8DEnabled: Boolean = true,
    val clipMuted: Boolean = false,
    val clipSolo: Boolean = false,
    val clip8DEnabled: Boolean = true,
    val micMuted: Boolean = false,
    val micSolo: Boolean = false,
    val micCenterLock: Boolean = true,
    val micAutoDucking: Boolean = true,
    val voiceEffect: VoiceEffectPreset = VoiceEffectPreset.NORMAL,
    val youtubeMuted: Boolean = false,
    val youtubeSolo: Boolean = false,
    val youtube8DEnabled: Boolean = true,
    val youtubeVolume: Float = 0.85f,
    val youtubeSearchQuery: String = "",
    val youtubeResults: List<YouTubePresetVideo> = YouTubeCatalog.presets,
    val youtubeDeckMode: YouTubeDeckMode = YouTubeDeckMode.PLAYER,
    val youtubeLiveSearchUrl: String = "https://m.youtube.com/results?search_query=lofi+beats+sample",
    val youtubePlaybackState: Int = -1,
    val youtubeCurrentSec: Float = 0f,
    val youtubeDurationSec: Float = 0f,
    val youtubePlaybackRate: Float = 1.0f,
    val youtubeLoopStartSec: Float? = null,
    val youtubeLoopEndSec: Float? = null,
    val youtubeLoopEnabled: Boolean = false,
    val syncDawWithYoutube: Boolean = false,
    val isSavingProject: Boolean = false,
    val isExporting: Boolean = false,
    val lastExportedFile: File? = null,
    val statusMessage: String? = null,
)

class DawViewModel(
    private val repository: DawRepository,
    val audioEngine: DawAudioEngine,
) : ViewModel() {

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _youtubeCommands = MutableSharedFlow<YouTubePlayerCommand>(extraBufferCapacity = 24)
    val youtubeCommands: SharedFlow<YouTubePlayerCommand> = _youtubeCommands.asSharedFlow()

    val telemetry: StateFlow<DawTelemetry> = audioEngine.telemetry

    val projectsState: StateFlow<UiState<List<DawProject>>> =
        repository.observeProjects()
            .map<List<DawProject>, UiState<List<DawProject>>> { list ->
                if (list.isNotEmpty() && _studioState.value.activeProject.id.isBlank()) {
                    loadProjectIntoEngine(list.first())
                }
                UiState.Success(list)
            }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing local projects", error)
                emit(UiState.Error(error.message ?: "Gagal memuat proyek lokal"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val activeProjectIdFlow = _studioState.map { it.activeProject.id }

    @OptIn(ExperimentalCoroutinesApi::class)
    val notesState: StateFlow<UiState<List<ProjectNote>>> =
        activeProjectIdFlow.flatMapLatest { projectId ->
            if (projectId.isBlank()) {
                flowOf<UiState<List<ProjectNote>>>(UiState.Success(emptyList()))
            } else {
                repository.observeProjectNotes(projectId)
                    .map<List<ProjectNote>, UiState<List<ProjectNote>>> { UiState.Success(it) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Error observing project notes", error)
                        emit(UiState.Error(error.message ?: "Gagal memuat catatan produksi"))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(emptyList())
        )

    fun loadProjectIntoEngine(project: DawProject) {
        audioEngine.projectState = project
        _studioState.update {
            it.copy(
                activeProject = project,
                youtubeVolume = project.youtubeGain.toFloat().coerceIn(0f, 1f),
            )
        }
    }

    fun updateActiveProject(transform: (DawProject) -> DawProject) {
        _studioState.update { state ->
            val updated = transform(state.activeProject)
            audioEngine.projectState = updated
            state.copy(activeProject = updated)
        }
    }

    fun togglePlayback() {
        if (audioEngine.telemetry.value.isPlaying) {
            audioEngine.stopPlayback()
        } else {
            audioEngine.startPlayback()
        }
    }

    fun toggleStep(index: Int) {
        if (index !in 0..15) return
        updateActiveProject { project ->
            val steps = project.stepPattern.toMutableList()
            while (steps.size < 16) steps.add(0L)
            steps[index] = (steps[index] + 1L) % 4L
            project.copy(stepPattern = steps)
        }
    }

    fun setSpatialPattern(pattern: SpatialPattern) {
        updateActiveProject { it.copy(spatialPattern = pattern.name) }
    }

    fun toggleAutoOrbit() {
        val next = !_studioState.value.autoOrbit
        audioEngine.autoOrbit = next
        _studioState.update { it.copy(autoOrbit = next) }
    }

    fun setManualRadarPosition(normX: Float, normY: Float) {
        audioEngine.autoOrbit = false
        audioEngine.manualNormX = normX.coerceIn(-1f, 1f)
        audioEngine.manualNormY = normY.coerceIn(-1f, 1f)
        _studioState.update { it.copy(autoOrbit = false) }
    }

    fun updateEqBand(bandIndex: Int, gainDb: Double) {
        if (bandIndex !in 0..4) return
        updateActiveProject { project ->
            val bands = project.eqBandsDb.toMutableList()
            while (bands.size < 5) bands.add(0.0)
            bands[bandIndex] = gainDb.coerceIn(-12.0, 12.0)
            project.copy(eqBandsDb = bands)
        }
    }

    fun applyEqPreset(presetName: String) {
        val bands = when (presetName) {
            "Flat" -> listOf(0.0, 0.0, 0.0, 0.0, 0.0)
            "Bass Club" -> listOf(7.5, 4.0, -1.0, 1.5, 3.5)
            "Vocal 8D" -> listOf(-2.0, 1.5, 4.5, 5.5, 4.0)
            "Studio Master" -> listOf(2.5, 0.5, -0.5, 2.0, 3.5)
            else -> listOf(3.0, 1.0, 0.0, 1.5, 2.5)
        }
        updateActiveProject { it.copy(eqBandsDb = bands) }
    }

    // Track Mute / Solo / 8D routing
    fun toggleSynthMute() {
        val next = !_studioState.value.synthMuted
        audioEngine.synthMuted = next
        _studioState.update { it.copy(synthMuted = next) }
    }

    fun toggleSynthSolo() {
        val next = !_studioState.value.synthSolo
        audioEngine.synthSolo = next
        _studioState.update { it.copy(synthSolo = next) }
    }

    fun toggleSynth8D() {
        val next = !_studioState.value.synth8DEnabled
        audioEngine.synth8DEnabled = next
        _studioState.update { it.copy(synth8DEnabled = next) }
    }

    fun toggleClipMute() {
        val next = !_studioState.value.clipMuted
        audioEngine.clipMuted = next
        _studioState.update { it.copy(clipMuted = next) }
    }

    fun toggleClipSolo() {
        val next = !_studioState.value.clipSolo
        audioEngine.clipSolo = next
        _studioState.update { it.copy(clipSolo = next) }
    }

    fun toggleClip8D() {
        val next = !_studioState.value.clip8DEnabled
        audioEngine.clip8DEnabled = next
        _studioState.update { it.copy(clip8DEnabled = next) }
    }

    fun toggleMicMute() {
        val next = !_studioState.value.micMuted
        audioEngine.micMuted = next
        _studioState.update { it.copy(micMuted = next) }
    }

    fun toggleMicSolo() {
        val next = !_studioState.value.micSolo
        audioEngine.micSolo = next
        _studioState.update { it.copy(micSolo = next) }
    }

    fun toggleMicCenterLock() {
        val next = !_studioState.value.micCenterLock
        audioEngine.micCenterLock = next
        _studioState.update { it.copy(micCenterLock = next) }
    }

    fun toggleMicAutoDucking() {
        val next = !_studioState.value.micAutoDucking
        audioEngine.micAutoDucking = next
        _studioState.update { it.copy(micAutoDucking = next) }
    }

    fun setVoiceEffect(preset: VoiceEffectPreset) {
        audioEngine.voiceEffect = preset
        _studioState.update { it.copy(voiceEffect = preset) }
    }

    fun toggleMicCapture(hasPermission: Boolean) {
        if (!hasPermission) {
            postMessage("Izin mikrofon diperlukan untuk menyalakan Channel Mic.")
            return
        }
        if (audioEngine.telemetry.value.isMicActive) {
            audioEngine.stopMicCapture()
            postMessage("Mikrofon dimatikan.")
        } else {
            if (!audioEngine.telemetry.value.isPlaying) {
                audioEngine.startPlayback()
            }
            val started = audioEngine.startMicCapture()
            postMessage(if (started) "Mikrofon aktif di Mixer DSP!" else "Gagal menginisialisasi AudioRecord.")
        }
    }

    fun importAudioClip(context: Context, uri: Uri) {
        viewModelScope.launch {
            postMessage("Mendekode klip audio ke 48 kHz PCM...")
            val result = AudioClipDecoder.decodeFromUri(context, uri)
            result.onSuccess { clip ->
                audioEngine.loadClip(clip)
                postMessage("Klip '${clip.displayName}' dimuat (${String.format("%.1f", clip.durationSeconds)} dtk).")
            }.onFailure { err ->
                postMessage("Gagal memuat klip: ${err.localizedMessage ?: "Format tidak didukung"}")
            }
        }
    }

    fun toggleYoutubeMute() {
        val next = !_studioState.value.youtubeMuted
        audioEngine.youtubeMuted = next
        _studioState.update { it.copy(youtubeMuted = next) }
    }

    fun toggleYoutubeSolo() {
        val next = !_studioState.value.youtubeSolo
        audioEngine.youtubeSolo = next
        _studioState.update { it.copy(youtubeSolo = next) }
    }

    fun toggleYoutube8D() {
        val next = !_studioState.value.youtube8DEnabled
        audioEngine.youtube8DEnabled = next
        _studioState.update { it.copy(youtube8DEnabled = next) }
    }

    fun setYoutubeRouteToDsp(enabled: Boolean) {
        audioEngine.setYoutubeRouteToDspEnabled(enabled)
        if (enabled && !audioEngine.telemetry.value.isPlaying) {
            audioEngine.startPlayback()
        }
        postMessage(
            if (enabled) "Audio YouTube diarahkan ke bus 8D Spatial & EQ DSP."
            else "Mode Monitor Reference: audio YouTube memantau spektrum FFT & siap disampling."
        )
    }

    fun toggleYoutubeSampleRecording() {
        val telemetryNow = audioEngine.telemetry.value
        if (!telemetryNow.isYouTubeCaptureActive) {
            postMessage("Aktifkan 'Tangkapan Stream Audio' terlebih dahulu untuk merekam sample.")
            return
        }
        if (telemetryNow.isYouTubeSampling) {
            val title = _studioState.value.activeProject.youtubeTitle
            val clip = audioEngine.stopAndCommitYouTubeSample(title)
            if (clip != null) {
                postMessage("Sample YouTube dimuat ke CH 2 (${String.format("%.1f", clip.durationSeconds)} dtk)!")
            } else {
                postMessage("Durasi sample terlalu pendek, coba rekam minimal 0.5 detik.")
            }
        } else {
            audioEngine.startYouTubeSampling()
            postMessage("Merekam audio stream YouTube untuk sample CH 2...")
        }
    }

    // YouTube Deck controls
    fun setYoutubeDeckMode(mode: YouTubeDeckMode) {
        _studioState.update { it.copy(youtubeDeckMode = mode) }
    }

    fun updateYoutubeQuery(query: String) {
        val trimmed = query.trim()
        val extractedId = extractYouTubeId(trimmed)
        if (extractedId != null) {
            loadYouTubeVideoById(extractedId, "Custom YouTube ($extractedId)")
            _studioState.update { it.copy(youtubeSearchQuery = query) }
            postMessage("Video YouTube dimuat: $extractedId")
            return
        }

        val filtered = if (trimmed.isEmpty()) {
            YouTubeCatalog.presets
        } else {
            YouTubeCatalog.presets.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.channel.contains(trimmed, ignoreCase = true) ||
                    it.category.contains(trimmed, ignoreCase = true)
            }.ifEmpty { YouTubeCatalog.presets }
        }
        _studioState.update {
            it.copy(youtubeSearchQuery = query, youtubeResults = filtered)
        }
    }

    fun searchYouTubeInWebView(queryOverride: String? = null) {
        val raw = (queryOverride ?: _studioState.value.youtubeSearchQuery).trim()
        if (raw.isBlank()) {
            _studioState.update { it.copy(youtubeDeckMode = YouTubeDeckMode.LIVE_SEARCH) }
            return
        }
        val extractedId = extractYouTubeId(raw)
        if (extractedId != null) {
            loadYouTubeVideoById(extractedId, "Custom YouTube ($extractedId)")
            postMessage("Video YouTube dimuat: $extractedId")
            return
        }
        val encoded = URLEncoder.encode(raw, "UTF-8")
        _studioState.update {
            it.copy(
                youtubeSearchQuery = raw,
                youtubeLiveSearchUrl = "https://m.youtube.com/results?search_query=$encoded",
                youtubeDeckMode = YouTubeDeckMode.LIVE_SEARCH,
            )
        }
    }

    fun selectYouTubeVideo(video: YouTubePresetVideo) {
        loadYouTubeVideoById(video.id, video.title)
        postMessage("Memuat YouTube: ${video.title}")
    }

    fun onYouTubeVideoPickedFromBrowser(videoId: String, title: String?) {
        if (!videoId.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return
        val cleanTitle = title
            ?.replace(" - YouTube", "", ignoreCase = true)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "YouTube Track ($videoId)"
        loadYouTubeVideoById(videoId, cleanTitle)
        postMessage("Video dimuat ke Deck DAW: $cleanTitle")
    }

    private fun loadYouTubeVideoById(videoId: String, title: String) {
        updateActiveProject {
            it.copy(youtubeVideoId = videoId, youtubeTitle = title)
        }
        _studioState.update {
            it.copy(
                youtubeDeckMode = YouTubeDeckMode.PLAYER,
                youtubeCurrentSec = 0f,
                youtubeDurationSec = 0f,
                youtubeLoopStartSec = null,
                youtubeLoopEndSec = null,
                youtubeLoopEnabled = false,
            )
        }
        _youtubeCommands.tryEmit(YouTubePlayerCommand.LoadVideo(videoId))
    }

    fun toggleYoutubePlayPause() {
        val isYtPlaying = _studioState.value.youtubePlaybackState == 1
        _youtubeCommands.tryEmit(if (isYtPlaying) YouTubePlayerCommand.Pause else YouTubePlayerCommand.Play)
    }

    fun seekYoutubeTo(seconds: Float) {
        val clamped = seconds.coerceAtLeast(0f)
        _studioState.update { it.copy(youtubeCurrentSec = clamped) }
        _youtubeCommands.tryEmit(YouTubePlayerCommand.SeekTo(clamped))
    }

    fun setYoutubePlaybackRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 1.5f)
        _studioState.update { it.copy(youtubePlaybackRate = clamped) }
        _youtubeCommands.tryEmit(YouTubePlayerCommand.SetPlaybackRate(clamped))
    }

    fun setYoutubeVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _studioState.update { it.copy(youtubeVolume = clamped) }
        updateActiveProject { it.copy(youtubeGain = clamped.toDouble()) }
        _youtubeCommands.tryEmit(YouTubePlayerCommand.SetVolume((clamped * 100).toInt()))
    }

    fun setYoutubeLoopA() {
        val cur = _studioState.value.youtubeCurrentSec
        _studioState.update { state ->
            val end = state.youtubeLoopEndSec?.takeIf { it > cur + 0.5f }
            state.copy(
                youtubeLoopStartSec = cur,
                youtubeLoopEndSec = end,
                youtubeLoopEnabled = end != null,
            )
        }
    }

    fun setYoutubeLoopB() {
        val cur = _studioState.value.youtubeCurrentSec
        _studioState.update { state ->
            val start = (state.youtubeLoopStartSec ?: 0f).coerceAtMost((cur - 0.5f).coerceAtLeast(0f))
            val validEnd = cur.coerceAtLeast(start + 0.5f)
            state.copy(
                youtubeLoopStartSec = start,
                youtubeLoopEndSec = validEnd,
                youtubeLoopEnabled = true,
            )
        }
    }

    fun toggleYoutubeLoopEnabled() {
        _studioState.update { state ->
            val canEnable = state.youtubeLoopStartSec != null && state.youtubeLoopEndSec != null
            state.copy(youtubeLoopEnabled = if (canEnable) !state.youtubeLoopEnabled else false)
        }
    }

    fun clearYoutubeLoop() {
        _studioState.update {
            it.copy(youtubeLoopStartSec = null, youtubeLoopEndSec = null, youtubeLoopEnabled = false)
        }
    }

    fun toggleSyncDawWithYoutube() {
        val next = !_studioState.value.syncDawWithYoutube
        _studioState.update { it.copy(syncDawWithYoutube = next) }
        postMessage(
            if (next) "Sinkronisasi Transport aktif: DAW mengikuti Play/Pause YouTube."
            else "Sinkronisasi Transport dimatikan."
        )
    }

    fun onYoutubePlayerStateChanged(state: Int) {
        _studioState.update { it.copy(youtubePlaybackState = state) }
        if (_studioState.value.syncDawWithYoutube) {
            when (state) {
                1 -> if (!audioEngine.telemetry.value.isPlaying) audioEngine.startPlayback()
                2, 0 -> if (audioEngine.telemetry.value.isPlaying) audioEngine.stopPlayback()
            }
        }
    }

    fun onYoutubeProgress(currentSec: Float, durationSec: Float) {
        val state = _studioState.value
        if (state.youtubeLoopEnabled) {
            val start = state.youtubeLoopStartSec
            val end = state.youtubeLoopEndSec
            if (start != null && end != null && end > start && currentSec >= end) {
                _youtubeCommands.tryEmit(YouTubePlayerCommand.SeekTo(start))
                _studioState.update { it.copy(youtubeCurrentSec = start, youtubeDurationSec = durationSec) }
                return
            }
        }
        _studioState.update {
            it.copy(
                youtubeCurrentSec = currentSec.coerceAtLeast(0f),
                youtubeDurationSec = durationSec.coerceAtLeast(0f),
            )
        }
    }

    fun onYoutubeMetaTitle(title: String) {
        val clean = title.trim()
        if (clean.isNotBlank() && _studioState.value.activeProject.youtubeTitle.startsWith("Custom YouTube")) {
            updateActiveProject { it.copy(youtubeTitle = clean.take(120)) }
        }
    }

    // Local Room Project Operations
    fun createLocalProject(title: String) {
        viewModelScope.launch {
            _studioState.update { it.copy(isSavingProject = true) }
            val result = repository.createProject(title, _studioState.value.activeProject)
            result.onSuccess { created ->
                loadProjectIntoEngine(created)
                postMessage("Proyek '${created.title}' tersimpan di perangkat!")
            }.onFailure { err ->
                postMessage("Gagal membuat proyek: ${err.localizedMessage ?: "Kesalahan penyimpanan"}")
            }
            _studioState.update { it.copy(isSavingProject = false) }
        }
    }

    fun saveActiveProject() {
        val current = _studioState.value.activeProject
        if (current.id.isBlank()) {
            createLocalProject(current.title)
            return
        }
        viewModelScope.launch {
            _studioState.update { it.copy(isSavingProject = true) }
            repository.saveProjectState(current)
                .onSuccess { postMessage("Mix & pengaturan DSP tersimpan di perangkat!") }
                .onFailure { err -> postMessage("Gagal menyimpan: ${err.localizedMessage}") }
            _studioState.update { it.copy(isSavingProject = false) }
        }
    }

    fun addProjectNote(message: String, barPosition: Long) {
        val active = _studioState.value.activeProject
        if (active.id.isBlank()) {
            postMessage("Simpan proyek terlebih dahulu untuk menulis catatan produksi.")
            return
        }
        viewModelScope.launch {
            repository.addProjectNote(active, message, barPosition)
                .onSuccess { postMessage("Catatan produksi disimpan.") }
                .onFailure { err -> postMessage("Gagal menyimpan catatan: ${err.localizedMessage}") }
        }
    }

    fun deleteProjectNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
                .onSuccess { postMessage("Catatan dihapus.") }
                .onFailure { err -> postMessage("Gagal menghapus catatan: ${err.localizedMessage}") }
        }
    }

    fun deleteLocalProject(project: DawProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
                .onSuccess {
                    if (_studioState.value.activeProject.id == project.id) {
                        loadProjectIntoEngine(DawProject())
                    }
                    postMessage("Proyek dihapus.")
                }
                .onFailure { err -> postMessage("Gagal menghapus: ${err.localizedMessage}") }
        }
    }

    fun exportAudioMix(context: Context, targetUri: Uri?, bitDepth: Int) {
        viewModelScope.launch {
            _studioState.update { it.copy(isExporting = true) }
            val result = audioEngine.exportMixToWav(context, targetUri, bitDepth = bitDepth)
            result.onSuccess { file ->
                _studioState.update {
                    it.copy(
                        isExporting = false,
                        lastExportedFile = file,
                        statusMessage = "Ekspor WAV $bitDepth-bit selesai (${file.length() / 1024} KB)."
                    )
                }
            }.onFailure { err ->
                _studioState.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = "Ekspor gagal: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _studioState.update { it.copy(statusMessage = null) }
    }

    private fun postMessage(msg: String) {
        _studioState.update { it.copy(statusMessage = msg) }
    }

    private fun extractYouTubeId(input: String): String? {
        val regex = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/))([A-Za-z0-9_-]{11})""")
        regex.find(input)?.groupValues?.getOrNull(1)?.let { return it }
        if (input.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return input
        return null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }

    private companion object {
        const val TAG = "DawViewModel"
    }
}
