package org.nova.messages.helpers

import android.content.Context
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

    fun compressVideo(uri: Uri, targetSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            evictStaleCache()
            try {
                val originalSize = context.getFileSizeFromUri(uri)
                if (originalSize <= 0 || (targetSize > 0 && originalSize <= targetSize)) {
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                val effectiveTarget = if (targetSize <= 0 || targetSize > 2 * 1024 * 1024) {
                    1024 * 1024L
                } else {
                    targetSize
                }

                val probe = probeVideo(uri) ?: run {
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                if (probe.durationUs <= 0) {
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                val durationSec = probe.durationUs / 1_000_000.0
                val targetBitrate = ((effectiveTarget * 8 * 0.9) / durationSec).toInt().coerceIn(100_000, 2_000_000)

                // Determine target resolution
                val scale = if (probe.width > probe.height) {
                    MAX_RESOLUTION.toFloat() / probe.width
                } else {
                    MAX_RESOLUTION.toFloat() / probe.height
                }.coerceAtMost(1f)
                val targetWidth = (probe.width * scale).toInt().coerceAtLeast(MIN_RESOLUTION) and -2
                val targetHeight = (probe.height * scale).toInt().coerceAtLeast(MIN_RESOLUTION) and -2

                val outputFile = File(outputDirectory, "nova_vid_${UUID.randomUUID()}.mp4")

                val success = transcode(uri, outputFile, probe, targetWidth, targetHeight, targetBitrate)
                if (success && outputFile.exists() && outputFile.length() > 0) {
                    val resultSize = outputFile.length()
                    if (resultSize <= effectiveTarget || resultSize < originalSize) {
                        callback.invoke(context.getNovaFileUri(outputFile))
                        return@ensureBackgroundThread
                    }
                    // Fallback: lower resolution
                    outputFile.delete()
                    val fbW = (targetWidth * 0.6).toInt() and -2
                    val fbH = (targetHeight * 0.6).toInt() and -2
                    val fbBitrate = (targetBitrate * 0.6).toInt()
                    if (fbW >= MIN_RESOLUTION && fbH >= MIN_RESOLUTION) {
                        val fbFile = File(outputDirectory, "nova_vid_${UUID.randomUUID()}.mp4")
                        val fbSuccess = transcode(uri, fbFile, probe, fbW, fbH, fbBitrate)
                        if (fbSuccess && fbFile.exists() && fbFile.length() > 0 && fbFile.length() < originalSize) {
                            callback.invoke(context.getNovaFileUri(fbFile))
                            return@ensureBackgroundThread
                        }
                        fbFile.delete()
                    }
                }
                outputFile.delete()
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
        val rotation: Int,
        val hasAudio: Boolean
    )

    private fun probeVideo(uri: Uri): VideoProbe? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var width = 0; var height = 0; var durationUs = 0L
            var mime = ""; var rotation = 0; var hasAudio = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("video/")) {
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    mime = m
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                } else if (m.startsWith("audio/")) {
                    hasAudio = true
                }
            }
            if (width <= 0 || height <= 0 || durationUs <= 0) null
            else VideoProbe(width, height, durationUs, mime, rotation, hasAudio)
        } catch (e: Exception) {
            Log.e(TAG, "Probe failed", e)
            null
        } finally {
            extractor.release()
        }
    }

    private fun transcode(uri: Uri, outputFile: File, probe: VideoProbe, targetW: Int, targetH: Int, targetBitrate: Int): Boolean {
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

            // Encoder
            val encoderFormat = MediaFormat.createVideoFormat("video/avc", targetW, targetH)
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 24)
            encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            val encoderCodec = MediaCodec.createEncoderByType("video/avc")
            encoderCodec.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderCodec.start()
            encoder = encoderCodec

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var muxerStarted = false
            var videoTrackMuxerIndex = -1
            val timeoutUs = 10_000L

            // Get stride info from decoder output format
            var srcStride = probe.width
            var srcSliceHeight = probe.height

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inIndex = decoderCodec.dequeueInputBuffer(timeoutUs)
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
                val outIndex = decoderCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    val decodedBuf = decoderCodec.getOutputBuffer(outIndex)
                    val decodedFormat = decoderCodec.getOutputFormat(outIndex)
                    if (decodedFormat.containsKey(MediaFormat.KEY_STRIDE)) {
                        srcStride = decodedFormat.getInteger(MediaFormat.KEY_STRIDE)
                    }
                    if (decodedFormat.containsKey(MediaFormat.KEY_SLICE_HEIGHT)) {
                        srcSliceHeight = decodedFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT)
                    }
                    val srcW = decodedFormat.getInteger(MediaFormat.KEY_WIDTH)
                    val srcH = decodedFormat.getInteger(MediaFormat.KEY_HEIGHT)

                    if (bufferInfo.size > 0 && decodedBuf != null) {
                        val encInIndex = encoderCodec.dequeueInputBuffer(timeoutUs)
                        if (encInIndex >= 0) {
                            val encBuf = encoderCodec.getInputBuffer(encInIndex)!!
                            // Scale YUV from src to target dimensions
                            scaleNV12(decodedBuf, encBuf, srcW, srcH, srcStride, srcSliceHeight, targetW, targetH)
                            encoderCodec.queueInputBuffer(encInIndex, 0, encBuf.capacity(), bufferInfo.presentationTimeUs, 0)
                        }
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        val encInIndex = encoderCodec.dequeueInputBuffer(timeoutUs)
                        if (encInIndex >= 0) {
                            encoderCodec.queueInputBuffer(encInIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                    decoderCodec.releaseOutputBuffer(outIndex, false)
                }

                // Get encoded output
                val encOutIndex = encoderCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (encOutIndex >= 0) {
                    val encBuf = encoderCodec.getOutputBuffer(encOutIndex)
                    if (bufferInfo.size > 0 && encBuf != null) {
                        if (!muxerStarted) {
                            val newFormat = encoderCodec.outputFormat
                            videoTrackMuxerIndex = muxer!!.addTrack(newFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        muxer!!.writeSampleData(videoTrackMuxerIndex, encBuf, bufferInfo)
                    }
                    encoderCodec.releaseOutputBuffer(encOutIndex, false)
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

    /**
     * Scales NV12 (YUV420 semi-planar) data from source dimensions to target dimensions.
     * Y plane: srcW x srcH -> dstW x dstH
     * UV plane: srcW/2 x srcH/2 -> dstW/2 x dstH/2
     */
    private fun scaleNV12(src: ByteBuffer, dst: ByteBuffer, srcW: Int, srcH: Int, srcStride: Int, srcSliceH: Int, dstW: Int, dstH: Int) {
        src.position(0)
        dst.position(0)
        val uvSrcW = srcW / 2
        val uvSrcH = srcH / 2
        val uvDstW = dstW / 2
        val uvDstH = dstH / 2
        val ySrcSize = srcStride * srcSliceH
        val yDstSize = dstW * dstH
        val uvSrcSize = srcStride * srcSliceH / 2  // approximate
        val uvDstSize = uvDstW * uvDstH * 2

        // Scale Y plane
        for (dy in 0 until dstH) {
            val sy = (dy * srcH / dstH).coerceIn(0, srcH - 1)
            for (dx in 0 until dstW) {
                val sx = (dx * srcW / dstW).coerceIn(0, srcW - 1)
                val srcIdx = sy * srcStride + sx
                if (srcIdx < ySrcSize) {
                    dst.put(src.get(srcIdx.coerceIn(0, ySrcSize - 1)))
                } else {
                    dst.put(0)
                }
            }
        }

        // Scale UV plane (interleaved U,V)
        val yPlaneEnd = ySrcSize
        for (dy in 0 until uvDstH) {
            val sy = (dy * uvSrcH / uvDstH).coerceIn(0, uvSrcH - 1)
            for (dx in 0 until uvDstW) {
                val sx = (dx * uvSrcW / uvDstW).coerceIn(0, uvSrcW - 1)
                val srcIdx = yPlaneEnd + sy * srcStride + sx * 2
                if (srcIdx + 1 < src.capacity()) {
                    dst.put(src.get(srcIdx.coerceIn(0, src.capacity() - 2)))
                    dst.put(src.get((srcIdx + 1).coerceIn(0, src.capacity() - 1)))
                } else {
                    dst.put(128.toByte())
                    dst.put(128.toByte())
                }
            }
        }
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