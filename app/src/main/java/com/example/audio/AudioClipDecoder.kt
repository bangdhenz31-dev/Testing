package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.min

data class DecodedAudioClip(
    val displayName: String,
    val sampleRate: Int,
    val stereoInterleaved: FloatArray,
    val durationSeconds: Float,
)

object AudioClipDecoder {
    private const val TARGET_SAMPLE_RATE = 48000
    private const val MAX_DURATION_SECONDS = 90 // Cap clip memory at 90s stereo float (~34MB)

    suspend fun decodeFromUri(context: Context, uri: Uri): Result<DecodedAudioClip> =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                var audioTrackIdx = -1
                var trackFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIdx = i
                        trackFormat = format
                        break
                    }
                }
                if (audioTrackIdx < 0 || trackFormat == null) {
                    return@withContext Result.failure(IllegalArgumentException("Track audio tidak ditemukan pada file."))
                }

                extractor.selectTrack(audioTrackIdx)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
                val srcSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val srcChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(trackFormat, null, null, 0)
                codec.start()

                val rawSamples = ArrayList<Float>(srcSampleRate * 2 * 10)
                val maxSrcFloats = srcSampleRate * 2 * MAX_DURATION_SECONDS
                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false

                while (!sawOutputEos && rawSamples.size < maxSrcFloats) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(5000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val shorts = ShortArray(shortBuf.remaining())
                            shortBuf.get(shorts)

                            if (srcChannels == 1) {
                                for (s in shorts) {
                                    val f = s / 32768f
                                    rawSamples.add(f)
                                    rawSamples.add(f)
                                }
                            } else {
                                var i = 0
                                while (i + srcChannels - 1 < shorts.size) {
                                    rawSamples.add(shorts[i] / 32768f)
                                    rawSamples.add(shorts[i + 1] / 32768f)
                                    i += srcChannels
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                }

                val srcFrames = rawSamples.size / 2
                if (srcFrames == 0) {
                    return@withContext Result.failure(IllegalStateException("File audio kosong atau gagal didekode."))
                }

                // Linear resample to 48000 Hz stereo for zero-drift mixing
                val dstFrames = ((srcFrames.toLong() * TARGET_SAMPLE_RATE) / srcSampleRate).toInt()
                    .coerceAtLeast(1)
                val resampled = FloatArray(dstFrames * 2)
                val ratio = srcFrames.toFloat() / dstFrames.toFloat()
                for (f in 0 until dstFrames) {
                    val srcPos = f * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
                    val idx1 = min(idx0 + 1, srcFrames - 1)
                    val frac = srcPos - idx0
                    val l0 = rawSamples[idx0 * 2]; val r0 = rawSamples[idx0 * 2 + 1]
                    val l1 = rawSamples[idx1 * 2]; val r1 = rawSamples[idx1 * 2 + 1]
                    resampled[f * 2] = l0 + (l1 - l0) * frac
                    resampled[f * 2 + 1] = r0 + (r1 - r0) * frac
                }

                val name = resolveDisplayName(context, uri)
                val duration = dstFrames.toFloat() / TARGET_SAMPLE_RATE
                Result.success(DecodedAudioClip(name, TARGET_SAMPLE_RATE, resampled, duration))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor.release() }
            }
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment ?: "Audio Clip"
    }
}
