package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Process
import com.example.data.DawProject
import com.example.data.SpatialPattern
import com.example.data.VoiceEffectPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class DawTelemetry(
    val isPlaying: Boolean = false,
    val activeStep: Int = 0,
    val spectrumBars: FloatArray = FloatArray(32),
    val synthSpectrumBars: FloatArray = FloatArray(32),
    val clipSpectrumBars: FloatArray = FloatArray(32),
    val micSpectrumBars: FloatArray = FloatArray(32),
    val youtubeSpectrumBars: FloatArray = FloatArray(32),
    val synthTrackActive: Boolean = false,
    val clipTrackActive: Boolean = false,
    val micTrackActive: Boolean = false,
    val youtubeTrackActive: Boolean = false,
    val emitterNormX: Float = 0f,
    val emitterNormY: Float = -0.5f,
    val synthPeak: Float = 0f,
    val clipPeak: Float = 0f,
    val micPeak: Float = 0f,
    val youtubePeak: Float = 0f,
    val masterPeak: Float = 0f,
    val compressorGrDb: Float = 0f,
    val estimatedLatencyMs: Float = 5.3f,
    val isMicActive: Boolean = false,
    val isYouTubeCaptureActive: Boolean = false,
    val isYouTubeSampling: Boolean = false,
    val youtubeSampleSeconds: Float = 0f,
    val youtubeRouteToDsp: Boolean = false,
    val loadedClipName: String = "Belum ada klip audio",
    val loadedClipDurationSec: Float = 0f,
)

/**
 * Low-latency multi-track DAW & 8D Spatial DSP Audio Engine.
 */
class DawAudioEngine {
    companion object {
        const val SAMPLE_RATE = 48000
    }

    private val _telemetry = MutableStateFlow(DawTelemetry())
    val telemetry: StateFlow<DawTelemetry> = _telemetry.asStateFlow()

    // Live configuration mirrored from ViewModel state
    @Volatile var projectState = DawProject()
    @Volatile var autoOrbit: Boolean = true
    @Volatile var manualNormX: Float = 0f
    @Volatile var manualNormY: Float = -0.5f

    // Track Mute / Solo / Spatial toggles
    @Volatile var synthMuted = false
    @Volatile var synthSolo = false
    @Volatile var synth8DEnabled = true

    @Volatile var clipMuted = false
    @Volatile var clipSolo = false
    @Volatile var clip8DEnabled = true

    @Volatile var micMuted = false
    @Volatile var micSolo = false
    @Volatile var micCenterLock = true
    @Volatile var micAutoDucking = true
    @Volatile var voiceEffect = VoiceEffectPreset.NORMAL

    @Volatile var youtubeMuted = false
    @Volatile var youtubeSolo = false
    @Volatile var youtube8DEnabled = true
    @Volatile var youtubeRouteToDsp = false

    @Volatile private var loadedClip: DecodedAudioClip? = null
    @Volatile private var clipCursorFrame = 0

    @Volatile private var running = false
    @Volatile private var micActive = false
    @Volatile private var ytCaptureActive = false
    @Volatile private var ytSampling = false
    private var audioThread: Thread? = null
    private var micRecord: AudioRecord? = null
    private var ytRecord: AudioRecord? = null

    init {
        YouTubeCaptureBridge.registerEngine(this)
    }

    // DSP instances (audio-thread confined during playback)
    private val bassFilter = StereoBiquadFilter(SAMPLE_RATE.toFloat())
    private val eqFilters = Array(5) { StereoBiquadFilter(SAMPLE_RATE.toFloat()) }
    private val micFilter = StereoBiquadFilter(SAMPLE_RATE.toFloat())
    private val spatialPanner = Spatial8DPanner(SAMPLE_RATE.toFloat())
    private val reverb = CathedralReverb(SAMPLE_RATE.toFloat())
    private val delay = StereoDelayPlugin(SAMPLE_RATE.toFloat())
    private val compressor = DynamicsCompressor(SAMPLE_RATE.toFloat())

    private val eqFreqs = floatArrayOf(60f, 230f, 910f, 3600f, 12000f)

    // Polyphonic Cyber Synth state
    private var stepSampleCounter = 0
    private var currentStep = 0
    private var kickPhase = 0f; private var kickEnv = 0f
    private var snareEnv = 0f; private var hatEnv = 0f
    private var bassPhase = 0f; private var bassEnv = 0f; private var bassFreq = 55f
    private var arpPhase = 0f; private var arpEnv = 0f; private var arpFreq = 220f
    private var rngState = 0x12345678

    // Mic ring buffer
    private val micRing = FloatArray(8192)
    @Volatile private var micWritePos = 0
    private var micReadPos = 0

    // YouTube AudioPlaybackCapture stereo ring buffer & 30s sampler buffer
    private val ytRingSize = 16384
    private val ytRingL = FloatArray(ytRingSize)
    private val ytRingR = FloatArray(ytRingSize)
    @Volatile private var ytWritePos = 0
    private var ytReadPos = 0

    private val ytSampleLock = Any()
    private val maxYtSampleFrames = SAMPLE_RATE * 30
    private var ytSampleBuffer: FloatArray? = null
    private var ytSampleFramesRecorded = 0

    fun loadClip(clip: DecodedAudioClip) {
        loadedClip = clip
        clipCursorFrame = 0
        _telemetry.update {
            it.copy(
                loadedClipName = clip.displayName,
                loadedClipDurationSec = clip.durationSeconds
            )
        }
    }

    fun clearClip() {
        loadedClip = null
        clipCursorFrame = 0
        _telemetry.update {
            it.copy(
                loadedClipName = "Belum ada klip audio",
                loadedClipDurationSec = 0f
            )
        }
    }

    fun startPlayback() {
        if (running) return
        running = true
        _telemetry.update { it.copy(isPlaying = true) }
        audioThread = Thread(::runAudioLoop, "DawLowLatencyAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stopPlayback() {
        running = false
        runCatching { audioThread?.join(500) }
        audioThread = null
        stopMicCapture()
        _telemetry.update {
            it.copy(
                isPlaying = false,
                spectrumBars = FloatArray(32),
                synthSpectrumBars = FloatArray(32),
                clipSpectrumBars = FloatArray(32),
                micSpectrumBars = FloatArray(32),
                youtubeSpectrumBars = if (ytCaptureActive) it.youtubeSpectrumBars else FloatArray(32),
                synthTrackActive = false,
                clipTrackActive = false,
                micTrackActive = false,
                youtubeTrackActive = ytCaptureActive,
                synthPeak = 0f,
                clipPeak = 0f,
                micPeak = 0f,
                youtubePeak = if (ytCaptureActive) it.youtubePeak else 0f,
                masterPeak = 0f
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startMicCapture(): Boolean {
        if (micActive) return true
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(1024)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                minBuf * 2
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }
            record.startRecording()
            micRecord = record
            micActive = true
            _telemetry.update { it.copy(isMicActive = true) }

            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buf = FloatArray(256)
                while (micActive && running) {
                    val read = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        for (i in 0 until read) {
                            micRing[micWritePos] = buf[i]
                            micWritePos = (micWritePos + 1) % micRing.size
                        }
                    }
                }
            }, "DawMicCapture").start()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopMicCapture() {
        micActive = false
        runCatching { micRecord?.stop() }
        runCatching { micRecord?.release() }
        micRecord = null
        _telemetry.update { it.copy(isMicActive = false, micPeak = 0f) }
    }

    @SuppressLint("MissingPermission")
    fun startYouTubeAudioCapture(projection: MediaProjection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        stopYouTubeAudioCapture()
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(2048)

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            record.startRecording()
            ytRecord = record
            ytCaptureActive = true
            ytWritePos = 0
            ytReadPos = 0
            _telemetry.update {
                it.copy(
                    isYouTubeCaptureActive = true,
                    youtubeTrackActive = true,
                    youtubeRouteToDsp = youtubeRouteToDsp,
                )
            }

            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val shortBuf = ShortArray(512) // 256 stereo frames
                val standbyFft = FloatArray(FftAnalyzer.FFT_SIZE)
                var standbyIdx = 0
                var standbyBars = FloatArray(32)
                var lastStandbyPushNs = System.nanoTime()

                while (ytCaptureActive) {
                    val read = record.read(shortBuf, 0, shortBuf.size, AudioRecord.READ_BLOCKING)
                    if (read > 1) {
                        val frames = read / 2
                        var pk = 0f
                        for (f in 0 until frames) {
                            val l = shortBuf[f * 2] / 32768f
                            val r = shortBuf[f * 2 + 1] / 32768f
                            pk = max(pk, max(abs(l), abs(r)))

                            ytRingL[ytWritePos] = l
                            ytRingR[ytWritePos] = r
                            ytWritePos = (ytWritePos + 1) % ytRingSize

                            if (ytSampling) {
                                synchronized(ytSampleLock) {
                                    val sb = ytSampleBuffer
                                    if (ytSampling && sb != null && ytSampleFramesRecorded < maxYtSampleFrames) {
                                        val outPos = ytSampleFramesRecorded * 2
                                        sb[outPos] = l
                                        sb[outPos + 1] = r
                                        ytSampleFramesRecorded++
                                        if (ytSampleFramesRecorded >= maxYtSampleFrames) {
                                            ytSampling = false
                                        }
                                    }
                                }
                            }

                            standbyFft[standbyIdx] = (l + r) * 0.5f
                            standbyIdx = (standbyIdx + 1) % FftAnalyzer.FFT_SIZE
                        }

                        // When main DAW audio loop is stopped, still feed FFT & Peak meters for reference monitoring
                        if (!running) {
                            val nowNs = System.nanoTime()
                            if (nowNs - lastStandbyPushNs > 33_000_000L) {
                                lastStandbyPushNs = nowNs
                                standbyBars = FftAnalyzer.computeSpectrumBars(standbyFft, standbyBars, 32)
                                val sec = synchronized(ytSampleLock) { ytSampleFramesRecorded / SAMPLE_RATE.toFloat() }
                                _telemetry.update {
                                    it.copy(
                                        spectrumBars = standbyBars,
                                        youtubeSpectrumBars = standbyBars,
                                        youtubeTrackActive = true,
                                        youtubePeak = (pk * projectState.youtubeGain.toFloat()).coerceIn(0f, 1f),
                                        isYouTubeSampling = ytSampling,
                                        youtubeSampleSeconds = sec,
                                    )
                                }
                            }
                        }
                    }
                }
            }, "DawYouTubeCapture").start()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopYouTubeAudioCapture() {
        ytCaptureActive = false
        ytSampling = false
        runCatching { ytRecord?.stop() }
        runCatching { ytRecord?.release() }
        ytRecord = null
        _telemetry.update {
            it.copy(
                isYouTubeCaptureActive = false,
                isYouTubeSampling = false,
                youtubeTrackActive = false,
                youtubePeak = 0f,
                youtubeSpectrumBars = FloatArray(32),
            )
        }
    }

    fun startYouTubeSampling(): Boolean {
        if (!ytCaptureActive) return false
        synchronized(ytSampleLock) {
            ytSampleBuffer = FloatArray(maxYtSampleFrames * 2)
            ytSampleFramesRecorded = 0
            ytSampling = true
        }
        _telemetry.update { it.copy(isYouTubeSampling = true, youtubeSampleSeconds = 0f) }
        return true
    }

    fun stopAndCommitYouTubeSample(videoTitle: String): DecodedAudioClip? {
        val captured: FloatArray
        val frames: Int
        synchronized(ytSampleLock) {
            ytSampling = false
            frames = ytSampleFramesRecorded
            val src = ytSampleBuffer
            captured = if (src != null && frames > SAMPLE_RATE / 4) {
                src.copyOf(frames * 2)
            } else {
                FloatArray(0)
            }
            ytSampleBuffer = null
            ytSampleFramesRecorded = 0
        }
        _telemetry.update { it.copy(isYouTubeSampling = false, youtubeSampleSeconds = 0f) }
        if (captured.isEmpty()) return null
        val shortTitle = videoTitle.trim().take(28).ifBlank { "YouTube Stream" }
        val clip = DecodedAudioClip(
            displayName = "YT Sample: $shortTitle",
            sampleRate = SAMPLE_RATE,
            stereoInterleaved = captured,
            durationSeconds = frames / SAMPLE_RATE.toFloat(),
        )
        loadClip(clip)
        return clip
    }

    fun setYoutubeRouteToDspEnabled(enabled: Boolean) {
        youtubeRouteToDsp = enabled
        _telemetry.update { it.copy(youtubeRouteToDsp = enabled) }
    }

    fun release() {
        stopYouTubeAudioCapture()
        stopPlayback()
    }

    private fun runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        var activeFrames = projectState.latencyFrames.toInt().coerceIn(64, 2048)
        var track = createLowLatencyTrack(activeFrames) ?: return
        track.play()

        var stereoOut = FloatArray(activeFrames * 2)
        val fftInput = FloatArray(FftAnalyzer.FFT_SIZE)
        val synthFftInput = FloatArray(FftAnalyzer.FFT_SIZE)
        val clipFftInput = FloatArray(FftAnalyzer.FFT_SIZE)
        val micFftInput = FloatArray(FftAnalyzer.FFT_SIZE)
        val ytFftInput = FloatArray(FftAnalyzer.FFT_SIZE)
        var fftWriteIdx = 0
        var lastUiPushNs = System.nanoTime()
        var smoothedBars = FloatArray(32)
        var smoothedSynthBars = FloatArray(32)
        var smoothedClipBars = FloatArray(32)
        var smoothedMicBars = FloatArray(32)
        var smoothedYtBars = FloatArray(32)
        var duckFactor = 1f

        resetDspState()

        while (running) {
            val cfg = projectState
            val requestedFrames = cfg.latencyFrames.toInt().coerceIn(64, 2048)
            if (requestedFrames != activeFrames) {
                runCatching { track.stop(); track.release() }
                activeFrames = requestedFrames
                track = createLowLatencyTrack(activeFrames) ?: break
                track.play()
                stereoOut = FloatArray(activeFrames * 2)
            }

            syncFilterCoefficients(cfg)

            val anySolo = synthSolo || clipSolo || micSolo || (youtubeSolo && ytCaptureActive && youtubeRouteToDsp)
            val playSynth = !synthMuted && (!anySolo || synthSolo)
            val playClip = !clipMuted && (!anySolo || clipSolo)
            val playMic = micActive && !micMuted && (!anySolo || micSolo)
            val playYt = ytCaptureActive && !youtubeMuted && (!anySolo || youtubeSolo)

            val samplesPerStep = ((SAMPLE_RATE * 60f) / (cfg.bpm.coerceIn(40L, 240L) * 4f)).toInt()
            val clipData = loadedClip?.stereoInterleaved
            val clipFrames = (clipData?.size ?: 0) / 2
            val pattern = SpatialPattern.fromRaw(cfg.spatialPattern)

            var synthPk = 0f; var clipPk = 0f; var micPk = 0f; var ytPk = 0f; var masterPk = 0f

            for (i in 0 until activeFrames) {
                // 1. Sequencer & Cyber Synth sample
                if (stepSampleCounter >= samplesPerStep) {
                    stepSampleCounter = 0
                    currentStep = (currentStep + 1) % 16
                    triggerSynthStep(cfg.stepPattern.getOrElse(currentStep) { 0L }.toInt(), currentStep)
                }
                stepSampleCounter++

                val synthMono = if (playSynth) renderSynthSample() * cfg.synthGain.toFloat() else 0f
                synthPk = max(synthPk, abs(synthMono))

                // 2. Imported Audio Clip sample
                var clipL = 0f; var clipR = 0f
                if (playClip && clipData != null && clipFrames > 0) {
                    val idx = (clipCursorFrame % clipFrames) * 2
                    clipL = clipData[idx] * cfg.clipGain.toFloat()
                    clipR = clipData[idx + 1] * cfg.clipGain.toFloat()
                    clipCursorFrame = (clipCursorFrame + 1) % clipFrames
                    clipPk = max(clipPk, max(abs(clipL), abs(clipR)))
                }

                // 3. Live Microphone sample + Voice FX + Auto-Ducking
                var micSample = 0f
                if (playMic) {
                    if (micReadPos != micWritePos) {
                        micSample = micRing[micReadPos]
                        micReadPos = (micReadPos + 1) % micRing.size
                    }
                    micSample = micFilter.processLeft(micSample) * cfg.micGain.toFloat()
                    micPk = max(micPk, abs(micSample))
                }

                // 4. Captured YouTube WebView Stream (AudioPlaybackCapture)
                var ytL = 0f; var ytR = 0f
                if (ytCaptureActive && ytReadPos != ytWritePos) {
                    val gain = cfg.youtubeGain.toFloat()
                    ytL = ytRingL[ytReadPos] * gain
                    ytR = ytRingR[ytReadPos] * gain
                    ytReadPos = (ytReadPos + 1) % ytRingSize
                    if (playYt) {
                        ytPk = max(ytPk, max(abs(ytL), abs(ytR)))
                    } else {
                        ytL = 0f; ytR = 0f
                    }
                }
                val ytToBusL = if (youtubeRouteToDsp) ytL else 0f
                val ytToBusR = if (youtubeRouteToDsp) ytR else 0f

                val targetDuck = if (micAutoDucking && micPk > 0.05f) 0.30f else 1.0f
                duckFactor += (targetDuck - duckFactor) * 0.002f

                // Separate 8D Spatial vs Center-Locked routing
                var bus8DL = (if (synth8DEnabled) synthMono else 0f) * duckFactor +
                    (if (clip8DEnabled) clipL else 0f) * duckFactor +
                    (if (youtube8DEnabled) ytToBusL else 0f) * duckFactor
                var bus8DR = (if (synth8DEnabled) synthMono else 0f) * duckFactor +
                    (if (clip8DEnabled) clipR else 0f) * duckFactor +
                    (if (youtube8DEnabled) ytToBusR else 0f) * duckFactor

                if (!micCenterLock && playMic) {
                    bus8DL += micSample
                    bus8DR += micSample
                }

                // Apply Deep Bass Enhancer + 5-Band Equalizer
                bus8DL = bassFilter.processLeft(bus8DL)
                bus8DR = bassFilter.processRight(bus8DR)
                for (band in 0..4) {
                    bus8DL = eqFilters[band].processLeft(bus8DL)
                    bus8DR = eqFilters[band].processRight(bus8DR)
                }

                // Apply 8D Binaural Spatial Panner
                val (spatL, spatR) = spatialPanner.stepAndProcess(
                    bus8DL,
                    bus8DR,
                    pattern,
                    cfg.orbitSpeedHz.toFloat(),
                    cfg.orbitRadiusMeters.toFloat(),
                    autoOrbit,
                    manualNormX,
                    manualNormY,
                )

                // Sum Center-Locked channels
                var mixL = spatL +
                    (if (!synth8DEnabled) synthMono else 0f) * duckFactor +
                    (if (!clip8DEnabled) clipL else 0f) * duckFactor +
                    (if (!youtube8DEnabled) ytToBusL else 0f) * duckFactor +
                    (if (micCenterLock && playMic) micSample else 0f)
                var mixR = spatR +
                    (if (!synth8DEnabled) synthMono else 0f) * duckFactor +
                    (if (!clip8DEnabled) clipR else 0f) * duckFactor +
                    (if (!youtube8DEnabled) ytToBusR else 0f) * duckFactor +
                    (if (micCenterLock && playMic) micSample else 0f)

                // Master Insert Plugins: Compressor -> Delay -> Cathedral Reverb -> Soft Limiter
                val (compL, compR) = compressor.process(
                    mixL, mixR, cfg.compressorEnabled, cfg.compressorThresholdDb.toFloat()
                )
                val (delL, delR) = delay.process(
                    compL, compR, cfg.delayEnabled, cfg.delayMix.toFloat()
                )
                val (revL, revR) = reverb.process(delL, delR, cfg.reverbMix.toFloat())

                val masterGain = cfg.masterGain.toFloat()
                val (limL, limR) = MasterSoftLimiter.process(
                    revL * masterGain, revR * masterGain, cfg.limiterEnabled
                )

                stereoOut[i * 2] = limL
                stereoOut[i * 2 + 1] = limR

                val monoOut = (limL + limR) * 0.5f
                masterPk = max(masterPk, abs(monoOut))
                fftInput[fftWriteIdx] = if (!youtubeRouteToDsp && ytCaptureActive) {
                    (monoOut + (ytL + ytR) * 0.5f)
                } else {
                    monoOut
                }
                synthFftInput[fftWriteIdx] = synthMono
                clipFftInput[fftWriteIdx] = (clipL + clipR) * 0.5f
                micFftInput[fftWriteIdx] = micSample
                ytFftInput[fftWriteIdx] = (ytL + ytR) * 0.5f
                fftWriteIdx = (fftWriteIdx + 1) % FftAnalyzer.FFT_SIZE
            }

            track.write(stereoOut, 0, stereoOut.size, AudioTrack.WRITE_BLOCKING)

            val nowNs = System.nanoTime()
            if (nowNs - lastUiPushNs > 33_000_000L) {
                lastUiPushNs = nowNs
                smoothedBars = FftAnalyzer.computeSpectrumBars(fftInput, smoothedBars, 32)
                val isClipActiveNow = playClip && clipData != null && clipFrames > 0
                smoothedSynthBars = if (playSynth) {
                    FftAnalyzer.computeSpectrumBars(synthFftInput, smoothedSynthBars, 32)
                } else {
                    FloatArray(32) { idx -> smoothedSynthBars[idx] * 0.72f }
                }
                smoothedClipBars = if (isClipActiveNow) {
                    FftAnalyzer.computeSpectrumBars(clipFftInput, smoothedClipBars, 32)
                } else {
                    FloatArray(32) { idx -> smoothedClipBars[idx] * 0.72f }
                }
                smoothedMicBars = if (playMic) {
                    FftAnalyzer.computeSpectrumBars(micFftInput, smoothedMicBars, 32)
                } else {
                    FloatArray(32) { idx -> smoothedMicBars[idx] * 0.72f }
                }
                smoothedYtBars = if (playYt) {
                    FftAnalyzer.computeSpectrumBars(ytFftInput, smoothedYtBars, 32)
                } else {
                    FloatArray(32) { idx -> smoothedYtBars[idx] * 0.72f }
                }

                val latencyMs = (activeFrames.toFloat() / SAMPLE_RATE) * 1000f
                val normX = (spatialPanner.currentX / cfg.orbitRadiusMeters.toFloat().coerceAtLeast(1f))
                val normY = (spatialPanner.currentZ / cfg.orbitRadiusMeters.toFloat().coerceAtLeast(1f))
                val ytSampleSec = synchronized(ytSampleLock) { ytSampleFramesRecorded / SAMPLE_RATE.toFloat() }

                _telemetry.update {
                    it.copy(
                        isPlaying = true,
                        activeStep = currentStep,
                        spectrumBars = smoothedBars,
                        synthSpectrumBars = smoothedSynthBars,
                        clipSpectrumBars = smoothedClipBars,
                        micSpectrumBars = smoothedMicBars,
                        youtubeSpectrumBars = smoothedYtBars,
                        synthTrackActive = playSynth,
                        clipTrackActive = isClipActiveNow,
                        micTrackActive = playMic,
                        youtubeTrackActive = playYt,
                        emitterNormX = normX,
                        emitterNormY = normY,
                        synthPeak = synthPk.coerceIn(0f, 1f),
                        clipPeak = clipPk.coerceIn(0f, 1f),
                        micPeak = micPk.coerceIn(0f, 1f),
                        youtubePeak = ytPk.coerceIn(0f, 1f),
                        masterPeak = masterPk.coerceIn(0f, 1f),
                        compressorGrDb = compressor.lastGainReductionDb,
                        estimatedLatencyMs = latencyMs,
                        isYouTubeSampling = ytSampling,
                        youtubeSampleSeconds = ytSampleSec,
                    )
                }
            }
        }

        runCatching { track.stop(); track.release() }
    }

    private fun createLowLatencyTrack(bufferFrames: Int): AudioTrack? {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val requestedBytes = bufferFrames * 2 * 4
        val bufBytes = max(minBytes, requestedBytes)

        val audioAttrsBuilder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Prevent AudioPlaybackCapture from re-capturing DawAudioEngine's own output (no feedback loop)
            audioAttrsBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
        }

        val builder = AudioTrack.Builder()
            .setAudioAttributes(audioAttrsBuilder.build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return runCatching { builder.build() }.getOrNull()
    }

    private fun syncFilterCoefficients(cfg: DawProject) {
        bassFilter.setLowShelf(90f, cfg.bassBoostDb.toFloat())
        for (b in 0..4) {
            val gain = cfg.eqBandsDb.getOrElse(b) { 0.0 }.toFloat().coerceIn(-12f, 12f)
            when (b) {
                0 -> eqFilters[b].setLowShelf(eqFreqs[b], gain)
                4 -> eqFilters[b].setHighShelf(eqFreqs[b], gain)
                else -> eqFilters[b].setPeaking(eqFreqs[b], gain)
            }
        }
        when (voiceEffect) {
            VoiceEffectPreset.NORMAL -> micFilter.setPeaking(1000f, 0f)
            VoiceEffectPreset.RADIO -> micFilter.setLowShelf(180f, 8.5f)
            VoiceEffectPreset.STUDIO -> micFilter.setPeaking(3200f, 6.5f, 1.2f)
            VoiceEffectPreset.CYBER -> micFilter.setPeaking(1450f, 9.0f, 2.8f)
        }
    }

    private fun resetDspState() {
        bassFilter.reset()
        eqFilters.forEach { it.reset() }
        micFilter.reset()
        spatialPanner.reset()
        reverb.reset()
        delay.reset()
        stepSampleCounter = 0
        currentStep = 0
    }

    private fun triggerSynthStep(intensity: Int, stepIdx: Int) {
        if (intensity <= 0) return
        val scale = floatArrayOf(110f, 130.81f, 146.83f, 164.81f, 196f, 220f, 261.63f, 329.63f)
        if (stepIdx % 4 == 0 || intensity == 3) {
            kickEnv = 0.85f * (intensity / 3f)
            kickPhase = 0f
        }
        if (stepIdx % 8 == 4) {
            snareEnv = 0.45f
        }
        if (stepIdx % 2 == 1) {
            hatEnv = 0.22f
        }
        bassFreq = scale[(stepIdx / 2) % 4] * 0.5f
        bassEnv = 0.38f * (intensity / 3f)
        arpFreq = scale[(stepIdx + intensity) % scale.size] * 2f
        arpEnv = 0.26f * (intensity / 3f)
    }

    private fun nextNoise(): Float {
        rngState = rngState * 1664525 + 1013904223
        return ((rngState ushr 16) and 0xFFFF) / 32768f - 1f
    }

    private fun renderSynthSample(): Float {
        var out = 0f
        if (kickEnv > 0.001f) {
            val freq = 42f + 110f * kickEnv
            kickPhase += (2.0 * PI * freq / SAMPLE_RATE).toFloat()
            out += sin(kickPhase) * kickEnv * 0.65f
            kickEnv *= 0.9982f
        }
        if (snareEnv > 0.001f) {
            out += nextNoise() * snareEnv * 0.35f
            snareEnv *= 0.9975f
        }
        if (hatEnv > 0.001f) {
            out += nextNoise() * hatEnv * 0.22f
            hatEnv *= 0.9935f
        }
        if (bassEnv > 0.001f) {
            bassPhase += (bassFreq / SAMPLE_RATE)
            if (bassPhase >= 1f) bassPhase -= 1f
            val saw = bassPhase * 2f - 1f
            out += saw * bassEnv * 0.35f
            bassEnv *= 0.9994f
        }
        if (arpEnv > 0.001f) {
            arpPhase += (2.0 * PI * arpFreq / SAMPLE_RATE).toFloat()
            out += sin(arpPhase) * arpEnv * 0.30f
            arpEnv *= 0.9995f
        }
        return out
    }

    /**
     * Offline high-quality DSP bounce to 16-bit or 24-bit PCM WAV (48 kHz stereo).
     */
    suspend fun exportMixToWav(
        context: Context,
        targetUri: Uri?,
        bitDepth: Int = 24,
        durationSeconds: Int = 16,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cfg = projectState
            val clipData = loadedClip?.stereoInterleaved
            val clipFrames = (clipData?.size ?: 0) / 2
            val totalSec = if (clipFrames > 0) {
                (clipFrames / SAMPLE_RATE).coerceIn(8, 60)
            } else {
                durationSeconds.coerceIn(8, 30)
            }
            val totalFrames = totalSec * SAMPLE_RATE

            // Local isolated DSP chain for deterministic offline render
            val offBass = StereoBiquadFilter(SAMPLE_RATE.toFloat()).apply {
                setLowShelf(90f, cfg.bassBoostDb.toFloat())
            }
            val offEq = Array(5) { b ->
                StereoBiquadFilter(SAMPLE_RATE.toFloat()).apply {
                    val gain = cfg.eqBandsDb.getOrElse(b) { 0.0 }.toFloat()
                    when (b) {
                        0 -> setLowShelf(eqFreqs[b], gain)
                        4 -> setHighShelf(eqFreqs[b], gain)
                        else -> setPeaking(eqFreqs[b], gain)
                    }
                }
            }
            val offSpatial = Spatial8DPanner(SAMPLE_RATE.toFloat())
            val offReverb = CathedralReverb(SAMPLE_RATE.toFloat())
            val offDelay = StereoDelayPlugin(SAMPLE_RATE.toFloat())
            val offComp = DynamicsCompressor(SAMPLE_RATE.toFloat())
            val pattern = SpatialPattern.fromRaw(cfg.spatialPattern)
            val samplesPerStep = ((SAMPLE_RATE * 60f) / (cfg.bpm.coerceIn(40L, 240L) * 4f)).toInt()

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = cfg.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32)
                .ifBlank { "SpatialDawMix" }
            val outFile = File(exportDir, "${safeName}_${bitDepth}bit_48kHz.wav")

            val bytesPerSample = if (bitDepth == 24) 3 else 2
            val dataBytes = totalFrames.toLong() * 2L * bytesPerSample

            BufferedOutputStream(FileOutputStream(outFile), 65536).use { bos ->
                writeWavHeader(bos, SAMPLE_RATE, 2, bitDepth, dataBytes)

                // Reset synth envelope state for clean render from Bar 1
                var stepCounter = samplesPerStep
                var step = -1
                var kEnv = 0f; var kPhase = 0f
                var sEnv = 0f; var hEnv = 0f
                var bEnv = 0f; var bPhase = 0f; var bFreq = 55f
                var aEnv = 0f; var aPhase = 0f; var aFreq = 220f
                var seed = 0x5A17C0DE
                val scale = floatArrayOf(110f, 130.81f, 146.83f, 164.81f, 196f, 220f, 261.63f, 329.63f)

                val chunk = ByteArray(4096 * 2 * bytesPerSample)
                var chunkPos = 0

                for (f in 0 until totalFrames) {
                    if (stepCounter >= samplesPerStep) {
                        stepCounter = 0
                        step = (step + 1) % 16
                        val intensity = cfg.stepPattern.getOrElse(step) { 0L }.toInt()
                        if (intensity > 0) {
                            if (step % 4 == 0 || intensity == 3) { kEnv = 0.85f * (intensity / 3f); kPhase = 0f }
                            if (step % 8 == 4) sEnv = 0.45f
                            if (step % 2 == 1) hEnv = 0.22f
                            bFreq = scale[(step / 2) % 4] * 0.5f; bEnv = 0.38f * (intensity / 3f)
                            aFreq = scale[(step + intensity) % scale.size] * 2f; aEnv = 0.26f * (intensity / 3f)
                        }
                    }
                    stepCounter++

                    var synth = 0f
                    if (!synthMuted) {
                        if (kEnv > 0.001f) {
                            kPhase += (2.0 * PI * (42f + 110f * kEnv) / SAMPLE_RATE).toFloat()
                            synth += sin(kPhase) * kEnv * 0.65f; kEnv *= 0.9982f
                        }
                        if (sEnv > 0.001f) {
                            seed = seed * 1664525 + 1013904223
                            synth += (((seed ushr 16) and 0xFFFF) / 32768f - 1f) * sEnv * 0.35f; sEnv *= 0.9975f
                        }
                        if (hEnv > 0.001f) {
                            seed = seed * 1664525 + 1013904223
                            synth += (((seed ushr 16) and 0xFFFF) / 32768f - 1f) * hEnv * 0.22f; hEnv *= 0.9935f
                        }
                        if (bEnv > 0.001f) {
                            bPhase += bFreq / SAMPLE_RATE; if (bPhase >= 1f) bPhase -= 1f
                            synth += (bPhase * 2f - 1f) * bEnv * 0.35f; bEnv *= 0.9994f
                        }
                        if (aEnv > 0.001f) {
                            aPhase += (2.0 * PI * aFreq / SAMPLE_RATE).toFloat()
                            synth += sin(aPhase) * aEnv * 0.30f; aEnv *= 0.9995f
                        }
                        synth *= cfg.synthGain.toFloat()
                    }

                    var cL = 0f; var cR = 0f
                    if (!clipMuted && clipData != null && clipFrames > 0) {
                        val idx = (f % clipFrames) * 2
                        cL = clipData[idx] * cfg.clipGain.toFloat()
                        cR = clipData[idx + 1] * cfg.clipGain.toFloat()
                    }

                    var sigL = offBass.processLeft(synth + cL)
                    var sigR = offBass.processRight(synth + cR)
                    for (b in 0..4) {
                        sigL = offEq[b].processLeft(sigL)
                        sigR = offEq[b].processRight(sigR)
                    }

                    val (spL, spR) = offSpatial.stepAndProcess(
                        sigL, sigR, pattern,
                        cfg.orbitSpeedHz.toFloat(), cfg.orbitRadiusMeters.toFloat(),
                        true, 0f, -0.5f
                    )
                    val (cpL, cpR) = offComp.process(spL, spR, cfg.compressorEnabled, cfg.compressorThresholdDb.toFloat())
                    val (dlL, dlR) = offDelay.process(cpL, cpR, cfg.delayEnabled, cfg.delayMix.toFloat())
                    val (rvL, rvR) = offReverb.process(dlL, dlR, cfg.reverbMix.toFloat())

                    val mg = cfg.masterGain.toFloat()
                    val (outL, outR) = MasterSoftLimiter.process(rvL * mg, rvR * mg, cfg.limiterEnabled)

                    if (bitDepth == 24) {
                        val lInt = (outL.coerceIn(-1f, 1f) * 8388607f).toInt()
                        val rInt = (outR.coerceIn(-1f, 1f) * 8388607f).toInt()
                        chunk[chunkPos++] = (lInt and 0xFF).toByte()
                        chunk[chunkPos++] = ((lInt shr 8) and 0xFF).toByte()
                        chunk[chunkPos++] = ((lInt shr 16) and 0xFF).toByte()
                        chunk[chunkPos++] = (rInt and 0xFF).toByte()
                        chunk[chunkPos++] = ((rInt shr 8) and 0xFF).toByte()
                        chunk[chunkPos++] = ((rInt shr 16) and 0xFF).toByte()
                    } else {
                        val lShort = (outL.coerceIn(-1f, 1f) * 32767f).toInt()
                        val rShort = (outR.coerceIn(-1f, 1f) * 32767f).toInt()
                        chunk[chunkPos++] = (lShort and 0xFF).toByte()
                        chunk[chunkPos++] = ((lShort shr 8) and 0xFF).toByte()
                        chunk[chunkPos++] = (rShort and 0xFF).toByte()
                        chunk[chunkPos++] = ((rShort shr 8) and 0xFF).toByte()
                    }

                    if (chunkPos >= chunk.size) {
                        bos.write(chunk, 0, chunkPos)
                        chunkPos = 0
                    }
                }
                if (chunkPos > 0) {
                    bos.write(chunk, 0, chunkPos)
                }
            }

            // Copy to user-selected SAF URI if provided
            if (targetUri != null) {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    outFile.inputStream().use { inp -> inp.copyTo(out) }
                }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeWavHeader(
        out: OutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataBytes: Long,
    ) {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // PCM linear quantization
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())
        out.write(header.array())
    }
}
