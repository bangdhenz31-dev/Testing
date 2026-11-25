package com.example.audio

import com.example.data.SpatialPattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Stereo Biquad IIR filter for 5-band parametric/graphic EQ, bass boost, and voice FX.
 */
class StereoBiquadFilter(private val sampleRate: Float = 48000f) {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1L = 0f; private var x2L = 0f; private var y1L = 0f; private var y2L = 0f
    private var x1R = 0f; private var x2R = 0f; private var y1R = 0f; private var y2R = 0f

    fun setLowShelf(freqHz: Float, gainDb: Float, slope: Float = 0.9f) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = (2.0 * PI * freqHz / sampleRate).toFloat()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = (sinW0 / 2f) * sqrt((a + 1f / a) * (1f / slope - 1f) + 2f)
        val twoSqrtAAlpha = 2f * sqrt(a) * alpha

        val a0 = (a + 1f) + (a - 1f) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1f) - (a - 1f) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (2f * a * ((a - 1f) - (a + 1f) * cosW0)) / a0
        b2 = (a * ((a + 1f) - (a - 1f) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (-2f * ((a - 1f) + (a + 1f) * cosW0)) / a0
        a2 = ((a + 1f) + (a - 1f) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun setHighShelf(freqHz: Float, gainDb: Float, slope: Float = 0.9f) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = (2.0 * PI * freqHz / sampleRate).toFloat()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = (sinW0 / 2f) * sqrt((a + 1f / a) * (1f / slope - 1f) + 2f)
        val twoSqrtAAlpha = 2f * sqrt(a) * alpha

        val a0 = (a + 1f) - (a - 1f) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1f) + (a - 1f) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (-2f * a * ((a - 1f) + (a + 1f) * cosW0)) / a0
        b2 = (a * ((a + 1f) + (a - 1f) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (2f * ((a - 1f) - (a + 1f) * cosW0)) / a0
        a2 = ((a + 1f) - (a - 1f) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun setPeaking(freqHz: Float, gainDb: Float, q: Float = 1.1f) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = (2.0 * PI * freqHz / sampleRate).toFloat()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)

        val a0 = 1f + alpha / a
        b0 = (1f + alpha * a) / a0
        b1 = (-2f * cosW0) / a0
        b2 = (1f - alpha * a) / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha / a) / a0
    }

    fun processLeft(input: Float): Float {
        val y = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = input
        y2L = y1L; y1L = y
        return y
    }

    fun processRight(input: Float): Float {
        val y = b0 * input + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R; x1R = input
        y2R = y1R; y1R = y
        return y
    }

    fun reset() {
        x1L = 0f; x2L = 0f; y1L = 0f; y2L = 0f
        x1R = 0f; x2R = 0f; y1R = 0f; y2R = 0f
    }
}

/**
 * 8D/9D Binaural Spatial Panner using ITD micro-delay, ILD stereo panning, and distance attenuation.
 */
class Spatial8DPanner(private val sampleRate: Float = 48000f) {
    private val maxItdSamples = 48 // ~1ms max interaural time difference
    private val delayL = FloatArray(maxItdSamples)
    private val delayR = FloatArray(maxItdSamples)
    private var writeIdx = 0

    // Normalized emitter coordinates in [-1..1] scaled by radiusMeters (1..10)
    @Volatile var currentX = 0f
        private set
    @Volatile var currentZ = -5f
        private set
    @Volatile var phaseRad = 0f
        private set

    fun stepAndProcess(
        inL: Float,
        inR: Float,
        pattern: SpatialPattern,
        orbitSpeedHz: Float,
        orbitRadiusMeters: Float,
        autoOrbit: Boolean,
        manualNormX: Float,
        manualNormY: Float,
    ): Pair<Float, Float> {
        if (autoOrbit) {
            phaseRad += (2.0 * PI * orbitSpeedHz / sampleRate).toFloat()
            if (phaseRad > (2.0 * PI).toFloat()) phaseRad -= (2.0 * PI).toFloat()
        }

        val (normX, normZ) = if (!autoOrbit) {
            manualNormX.coerceIn(-1f, 1f) to manualNormY.coerceIn(-1f, 1f)
        } else {
            when (pattern) {
                SpatialPattern.CIRCULAR -> sin(phaseRad) to -cos(phaseRad)
                SpatialPattern.WAVE -> sin(phaseRad) to (cos(phaseRad * 2f) * 0.55f)
                SpatialPattern.FIGURE8 -> sin(phaseRad) to (sin(phaseRad * 2f) * 0.72f)
                SpatialPattern.PULSE -> {
                    val pulse = 0.4f + 0.6f * abs(sin(phaseRad * 3f))
                    (sin(phaseRad) * pulse) to (-cos(phaseRad) * pulse)
                }
            }
        }

        currentX = normX * orbitRadiusMeters
        currentZ = normZ * orbitRadiusMeters

        // Distance attenuation (gentle so music remains loud & present)
        val dist = hypot(currentX, currentZ).coerceAtLeast(1f)
        val distGain = (1f / (1f + (dist - 1f) * 0.045f)).coerceIn(0.62f, 1.0f)

        // ILD (Interaural Level Difference): pan in [-1..1]
        val pan = normX.coerceIn(-1f, 1f)
        val anglePan = ((pan + 1f) * 0.5f) * (PI.toFloat() * 0.5f)
        val gainL = cos(anglePan) * 1.15f
        val gainR = sin(anglePan) * 1.15f

        // ITD (Interaural Time Difference): up to ~0.65ms (~31 samples)
        val itdSamples = (abs(pan) * 31f).toInt().coerceIn(0, maxItdSamples - 1)
        delayL[writeIdx] = inL
        delayR[writeIdx] = inR

        val delayedL = if (pan > 0f) {
            delayL[(writeIdx - itdSamples + maxItdSamples) % maxItdSamples]
        } else {
            inL
        }
        val delayedR = if (pan < 0f) {
            delayR[(writeIdx - itdSamples + maxItdSamples) % maxItdSamples]
        } else {
            inR
        }
        writeIdx = (writeIdx + 1) % maxItdSamples

        // Rear-head warmth simulation when source moves behind listener (normZ > 0)
        val frontBackPresence = if (normZ > 0f) 1f - normZ * 0.14f else 1f
        val outL = (delayedL * gainL * 0.85f + inR * 0.15f) * distGain * frontBackPresence
        val outR = (delayedR * gainR * 0.85f + inL * 0.15f) * distGain * frontBackPresence
        return outL to outR
    }

    fun reset() {
        delayL.fill(0f)
        delayR.fill(0f)
        writeIdx = 0
        phaseRad = 0f
    }
}

/**
 * Lightweight stereo Schroeder Reverb for 9D Cathedral Space.
 */
class CathedralReverb(sampleRate: Float = 48000f) {
    private val combDelaysL = intArrayOf(
        (0.0297f * sampleRate).toInt(),
        (0.0371f * sampleRate).toInt(),
        (0.0411f * sampleRate).toInt(),
        (0.0437f * sampleRate).toInt(),
    )
    private val combDelaysR = combDelaysL.map { it + 23 }.toIntArray()

    private val combsL = Array(4) { FloatArray(combDelaysL[it]) }
    private val combsR = Array(4) { FloatArray(combDelaysR[it]) }
    private val idxL = IntArray(4)
    private val idxR = IntArray(4)
    private val lpL = FloatArray(4)
    private val lpR = FloatArray(4)

    fun process(inL: Float, inR: Float, wetMix: Float): Pair<Float, Float> {
        if (wetMix <= 0.01f) return inL to inR
        val feedback = 0.78f
        val damp = 0.35f

        var wetL = 0f
        var wetR = 0f
        for (i in 0..3) {
            val bufL = combsL[i]
            val bufR = combsR[i]
            val pL = idxL[i]
            val pR = idxR[i]

            val outL = bufL[pL]
            val outR = bufR[pR]

            lpL[i] = outL * (1f - damp) + lpL[i] * damp
            lpR[i] = outR * (1f - damp) + lpR[i] * damp

            bufL[pL] = inL + lpL[i] * feedback
            bufR[pR] = inR + lpR[i] * feedback

            idxL[i] = (pL + 1) % bufL.size
            idxR[i] = (pR + 1) % bufR.size

            wetL += outL
            wetR += outR
        }
        wetL *= 0.25f
        wetR *= 0.25f

        val dry = 1f - wetMix * 0.45f
        return (inL * dry + wetL * wetMix) to (inR * dry + wetR * wetMix)
    }

    fun reset() {
        combsL.forEach { it.fill(0f) }
        combsR.forEach { it.fill(0f) }
        lpL.fill(0f); lpR.fill(0f)
        idxL.fill(0); idxR.fill(0)
    }
}

/**
 * Stereo Ping-Pong Delay Plugin.
 */
class StereoDelayPlugin(sampleRate: Float = 48000f) {
    private val delaySamples = (0.32f * sampleRate).toInt()
    private val bufL = FloatArray(delaySamples)
    private val bufR = FloatArray(delaySamples)
    private var idx = 0

    fun process(inL: Float, inR: Float, enabled: Boolean, mix: Float): Pair<Float, Float> {
        if (!enabled || mix <= 0.01f) return inL to inR
        val tapL = bufL[idx]
        val tapR = bufR[idx]
        val feedback = 0.42f
        bufL[idx] = inL + tapR * feedback
        bufR[idx] = inR + tapL * feedback
        idx = (idx + 1) % delaySamples
        return (inL + tapL * mix) to (inR + tapR * mix)
    }

    fun reset() {
        bufL.fill(0f); bufR.fill(0f); idx = 0
    }
}

/**
 * Real-time Stereo Compressor Plugin with gain reduction readout.
 */
class DynamicsCompressor(private val sampleRate: Float = 48000f) {
    private var envelope = 0f
    @Volatile var lastGainReductionDb = 0f
        private set

    fun process(inL: Float, inR: Float, enabled: Boolean, thresholdDb: Float): Pair<Float, Float> {
        if (!enabled) {
            lastGainReductionDb = 0f
            return inL to inR
        }
        val peak = max(abs(inL), abs(inR))
        val coeff = if (peak > envelope) 0.01f else 0.0005f
        envelope += coeff * (peak - envelope)

        val envDb = if (envelope > 1e-6f) 20f * (ln(envelope) / ln(10f)) else -96f
        val ratio = 4f
        val overDb = (envDb - thresholdDb).coerceAtLeast(0f)
        val grDb = overDb * (1f - 1f / ratio)
        lastGainReductionDb = grDb

        val makeup = 10f.pow(((-thresholdDb * 0.18f) - grDb) / 20f).coerceIn(0.25f, 2.2f)
        return (inL * makeup) to (inR * makeup)
    }
}

/**
 * Master Soft-Clip Limiter.
 */
object MasterSoftLimiter {
    fun process(inL: Float, inR: Float, enabled: Boolean): Pair<Float, Float> {
        return if (enabled) {
            tanh(inL.coerceIn(-3f, 3f) * 0.92f) to tanh(inR.coerceIn(-3f, 3f) * 0.92f)
        } else {
            inL.coerceIn(-1f, 1f) to inR.coerceIn(-1f, 1f)
        }
    }
}

/**
 * In-place Radix-2 Cooley-Tukey FFT yielding 32 smoothed frequency bars in 0..1.
 */
object FftAnalyzer {
    const val FFT_SIZE = 256
    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    fun computeSpectrumBars(monoInput: FloatArray, previousBars: FloatArray, barCount: Int = 32): FloatArray {
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        val copyLen = min(FFT_SIZE, monoInput.size)
        for (i in 0 until copyLen) {
            real[i] = monoInput[i] * hannWindow[i]
        }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until FFT_SIZE) {
            var bit = FFT_SIZE shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        // Butterfly passes
        var len = 2
        while (len <= FFT_SIZE) {
            val halfLen = len shr 1
            val angleStep = (-2.0 * PI / len).toFloat()
            for (start in 0 until FFT_SIZE step len) {
                for (k in 0 until halfLen) {
                    val angle = angleStep * k
                    val wr = cos(angle)
                    val wi = sin(angle)
                    val evenIdx = start + k
                    val oddIdx = evenIdx + halfLen
                    val tr = wr * real[oddIdx] - wi * imag[oddIdx]
                    val ti = wr * imag[oddIdx] + wi * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tr
                    imag[oddIdx] = imag[evenIdx] - ti
                    real[evenIdx] += tr
                    imag[evenIdx] += ti
                }
            }
            len = len shl 1
        }

        val halfBins = FFT_SIZE / 2
        val result = FloatArray(barCount)
        for (b in 0 until barCount) {
            val binStart = ((b.toFloat() / barCount).pow(1.45f) * (halfBins - 1)).toInt()
                .coerceIn(1, halfBins - 1)
            val binEnd = (((b + 1).toFloat() / barCount).pow(1.45f) * (halfBins - 1)).toInt()
                .coerceIn(binStart + 1, halfBins)
            var maxMag = 0f
            for (k in binStart until binEnd) {
                val mag = hypot(real[k], imag[k]) / (FFT_SIZE * 0.25f)
                if (mag > maxMag) maxMag = mag
            }
            val dbNorm = (ln(1f + maxMag * 12f) / ln(13f)).coerceIn(0f, 1f)
            val prev = if (b < previousBars.size) previousBars[b] else 0f
            result[b] = if (dbNorm >= prev) dbNorm else (prev * 0.86f + dbNorm * 0.14f)
        }
        return result
    }
}
