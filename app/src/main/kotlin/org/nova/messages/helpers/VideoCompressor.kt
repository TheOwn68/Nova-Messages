package org.nova.messages.helpers

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.util.Log
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.getFileSizeFromUri
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

class VideoCompressor(private val context: Context) {

    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        if (!exists()) mkdirs()
    }

    private val TAG = "VideoCompressor"
    private val MAX_RESOLUTION = 480
    private val MIN_RESOLUTION = 160
    private val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private val TIMEOUT_US = 100_000L

    fun compressVideo(uri: Uri, targetSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            evictStaleCache()
            try {
                val originalSize = context.getFileSizeFromUri(uri)
                if (originalSize <= 0 || (targetSize > 0 && originalSize <= targetSize)) {
                    Log.d(TAG, "Already small enough ($originalSize <= $targetSize), returning original")
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                val effectiveTarget = if (targetSize <= 0 || targetSize > 2 * 1024 * 1024) {
                    1024 * 1024L
                } else {
                    targetSize
                }

                val probe = probeVideo(uri) ?: run {
                    Log.w(TAG, "Probe failed")
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                if (probe.durationUs <= 0) {
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                val durationSec = probe.durationUs / 1_000_000.0
                Log.d(TAG, "Original: ${probe.width}x${probe.height}, ${durationSec}s, ${originalSize} bytes")

                // Strategy 1: Try at calculated bitrate + scaled resolution
                val targetBitrate = ((effectiveTarget * 8 * 0.85) / durationSec).toInt().coerceIn(100_000, 2_000_000)
                val scale = (MAX_RESOLUTION.toFloat() / maxOf(probe.width, probe.height)).coerceAtMost(1f)
                val targetW = (probe.width * scale).toInt().coerceAtLeast(MIN_RESOLUTION) and -2
                val targetH = (probe.height * scale).toInt().coerceAtLeast(MIN_RESOLUTION) and -2

                Log.d(TAG, "Strategy 1: ${targetW}x${targetH} @ ${targetBitrate}bps")

                val outputFile = File(outputDirectory, "nova_vid_${UUID.randomUUID()}.mp4")
                if (tryTranscode(uri, outputFile, probe, targetW, targetH, targetBitrate)) {
                    val resultSize = outputFile.length()
                    Log.d(TAG, "Strategy 1 result: $resultSize bytes")
                    if (resultSize in 1 until effectiveTarget || resultSize < originalSize * 0.5) {
                        callback.invoke(context.getNovaFileUri(outputFile))
                        return@ensureBackgroundThread
                    }
                    outputFile.delete()
                }

                // Strategy 2: Lower resolution + lower bitrate
                val fbW = (targetW * 0.7).toInt().coerceAtLeast(MIN_RESOLUTION) and -2
                val fbH = (targetH * 0.7).toInt().coerceAtLeast(MIN_RESOLUTION) and -2
                val fbBitrate = (targetBitrate * 0.5).toInt().coerceAtLeast(80_000)
                Log.d(TAG, "Strategy 2: ${fbW}x${fbH} @ ${fbBitrate}bps")

                val fbFile = File(outputDirectory, "nova_vid_${UUID.randomUUID()}.mp4")
                if (tryTranscode(uri, fbFile, probe, fbW, fbH, fbBitrate)) {
                    val resultSize = fbFile.length()
                    Log.d(TAG, "Strategy 2 result: $resultSize bytes")
                    if (resultSize < originalSize) {
                        callback.invoke(context.getNovaFileUri(fbFile))
                        return@ensureBackgroundThread
                    }
                    fbFile.delete()
                }

                // Strategy 3: Audio-only strip (re-encode at very low bitrate, original res)
                // This is a last resort - just reduce bitrate drastically
                val lowBitrate = 200_000
                Log.d(TAG, "Strategy 3: ${probe.width}x${probe.height} @ ${lowBitrate}bps (no resize)")
                val lowFile = File(outputDirectory, "nova_vid_${UUID.randomUUID()}.mp4")
                if (tryTranscode(uri, lowFile, probe, probe.width and -2, probe.height and -2, lowBitrate)) {
                    val resultSize = lowFile.length()
                    Log.d(TAG, "Strategy 3 result: $resultSize bytes")
                    if (resultSize < originalSize) {
                        callback.invoke(context.getNovaFileUri(lowFile))
                        return@ensureBackgroundThread
                    }
                    lowFile.delete()
                }

                Log.w(TAG, "All strategies failed, returning original")
                callback.invoke(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Video compression failed", e)
                callback.invoke(uri)
            }
        }
    }

    private data class VideoProbe(
        val width: Int,
        val height: Int,
        val durationUs: Long,
        val mime: String,
        val rotation: Int
    )

    private fun probeVideo(uri: Uri): VideoProbe? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var width = 0; var height = 0; var durationUs = 0L
            var mime = ""; var rotation = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("video/")) {
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    mime = m
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                }
            }
            if (width <= 0 || height <= 0 || durationUs <= 0) null
            else VideoProbe(width, height, durationUs, mime, rotation)
        } catch (e: Exception) {
            Log.e(TAG, "Probe failed", e)
            null
        } finally {
            extractor.release()
        }
    }

    private fun tryTranscode(uri: Uri, outputFile: File, probe: VideoProbe, targetW: Int, targetH: Int, targetBitrate: Int): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)

            var videoTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val m = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("video/")) { videoTrackIndex = i; break }
            }
            if (videoTrackIndex < 0) return false

            extractor.selectTrack(videoTrackIndex)
            val inputFormat = extractor.getTrackFormat(videoTrackIndex)

            // Decoder
            val decoderCodec = MediaCodec.createDecoderByType(probe.mime)
            decoderCodec.configure(inputFormat, null, null, 0)
            decoderCodec.start()
            decoder = decoderCodec

            val colorFormats = intArrayOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar
            )

            var encoderCodec: MediaCodec? = null
            var encoderStarted = false
            for (colorFormat in colorFormats) {
                try {
                    val encoderFormat = MediaFormat.createVideoFormat("video/avc", targetW, targetH)
                    encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                    encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                    encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                    encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                    val ec = MediaCodec.createEncoderByType("video/avc")
                    ec.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    ec.start()
                    encoderCodec = ec
                    encoderStarted = true
                    Log.d(TAG, "Encoder configured with color format $colorFormat")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Color format $colorFormat not supported: ${e.message}")
                }
            }

            if (!encoderStarted || encoderCodec == null) {
                Log.e(TAG, "No encoder color format supported")
                return false
            }

            encoder = encoderCodec
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var muxerStarted = false
            var videoTrackMuxerIndex = -1

            while (!outputDone) {
                // Feed input to decoder
                if (!inputDone) {
                    val inIndex = decoderCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = decoderCodec.getInputBuffer(inIndex)
                        val sampleSize = extractor.readSampleData(buf!!, 0)
                        if (sampleSize < 0) {
                            decoderCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoderCodec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Get decoded frame
                val outIndex = decoderCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val image = decoderCodec.getOutputImage(outIndex)
                    if (image != null && bufferInfo.size > 0) {
                        val planes = image.planes
                        val yPlane = planes[0]
                        val uPlane = planes[1]
                        val vPlane = planes[2]
                        val yBuf = yPlane.buffer
                        val uBuf = uPlane.buffer
                        val vBuf = vPlane.buffer
                        val yRowStride = yPlane.rowStride
                        val uRowStride = uPlane.rowStride
                        val uPixelStride = uPlane.pixelStride
                        val vRowStride = vPlane.rowStride
                        val vPixelStride = vPlane.pixelStride
                        val srcW = image.width
                        val srcH = image.height

                        val encInIndex = encoder!!.dequeueInputBuffer(TIMEOUT_US)
                        if (encInIndex >= 0) {
                            val encBuf = encoder!!.getInputBuffer(encInIndex)!!
                            val encFormat = encoder!!.inputFormat
                            val dstW = if (encFormat.containsKey(MediaFormat.KEY_WIDTH)) encFormat.getInteger(MediaFormat.KEY_WIDTH) else targetW
                            val dstH = if (encFormat.containsKey(MediaFormat.KEY_HEIGHT)) encFormat.getInteger(MediaFormat.KEY_HEIGHT) else targetH

                            // Scale YUV from source to target
                            scaleYUV(yBuf, uBuf, vBuf, encBuf, srcW, srcH, yRowStride, uRowStride, uPixelStride, vRowStride, vPixelStride, dstW, dstH)

                            encoder!!.queueInputBuffer(encInIndex, 0, encBuf.capacity(), bufferInfo.presentationTimeUs, 0)
                        }
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        val encInIndex = encoder!!.dequeueInputBuffer(TIMEOUT_US)
                        if (encInIndex >= 0) {
                            encoder!!.queueInputBuffer(encInIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                    image?.close()
                    decoderCodec.releaseOutputBuffer(outIndex, false)
                }

                // Get encoder output
                val encOutIndex = encoder!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (encOutIndex >= 0) {
                    val encBuf = encoder!!.getOutputBuffer(encOutIndex)
                    if (bufferInfo.size > 0 && encBuf != null) {
                        if (!muxerStarted) {
                            val newFormat = encoder!!.outputFormat
                            videoTrackMuxerIndex = muxer!!.addTrack(newFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        muxer!!.writeSampleData(videoTrackMuxerIndex, encBuf, bufferInfo)
                    }
                    encoder!!.releaseOutputBuffer(encOutIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Transcode failed", e)
            outputFile.delete()
            return false
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun scaleYUV(
        ySrc: ByteBuffer, uSrc: ByteBuffer, vSrc: ByteBuffer,
        dst: ByteBuffer,
        srcW: Int, srcH: Int,
        yRowStride: Int, uRowStride: Int, uPixelStride: Int, vRowStride: Int, vPixelStride: Int,
        dstW: Int, dstH: Int
    ) {
        dst.position(0)
        val uvSrcW = srcW / 2
        val uvSrcH = srcH / 2
        val uvDstW = dstW / 2
        val uvDstH = dstH / 2
        val yDstSize = dstW * dstH
        val uvDstSize = uvDstW * uvDstH * 2

        // Scale Y plane (nearest-neighbor)
        for (dy in 0 until dstH) {
            val sy = (dy * srcH / dstH).coerceIn(0, srcH - 1)
            for (dx in 0 until dstW) {
                val sx = (dx * srcW / dstW).coerceIn(0, srcW - 1)
                val srcIdx = sy * yRowStride + sx
                if (srcIdx in 0 until ySrc.capacity()) {
                    dst.put(ySrc.get(srcIdx))
                } else {
                    dst.put(0)
                }
            }
        }

        // Scale UV plane (semi-planar: interleaved U,V)
        for (dy in 0 until uvDstH) {
            val sy = (dy * uvSrcH / uvDstH).coerceIn(0, uvSrcH - 1)
            for (dx in 0 until uvDstW) {
                val sx = (dx * uvSrcW / uvDstW).coerceIn(0, uvSrcW - 1)
                val uIdx = sy * uRowStride + sx * uPixelStride
                val vIdx = sy * vRowStride + sx * vPixelStride
                if (uIdx < uSrc.capacity() && vIdx < vSrc.capacity()) {
                    dst.put(uSrc.get(uIdx.coerceIn(0, uSrc.capacity() - 1)))
                    dst.put(vSrc.get(vIdx.coerceIn(0, vSrc.capacity() - 1)))
                } else {
                    dst.put(128.toByte())
                    dst.put(128.toByte())
                }
            }
        }
        dst.flip()
    }

    private fun evictStaleCache() {
        try {
            val now = System.currentTimeMillis()
            val files = outputDirectory.listFiles() ?: return
            for (file in files) {
                if (now - file.lastModified() > MAX_CACHE_AGE_MS) file.delete()
            }
            var remaining = (outputDirectory.listFiles() ?: return).sortedBy { it.lastModified() }
            var total = remaining.sumOf { it.length() }
            for (file in remaining) {
                if (total <= 50L * 1024 * 1024) break
                total -= file.length()
                file.delete()
            }
        } catch (_: Exception) {}
    }
}