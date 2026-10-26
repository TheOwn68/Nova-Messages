package org.nova.messages.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import org.nova.messages.helpers.getNovaFileUri
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.isImageMimeType
import org.nova.messages.helpers.FILE_SIZE_1_MB
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageCompressor(private val context: Context) {
    private val contentResolver = context.contentResolver
    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val MAX_RESOLUTION = 1024 // Reduced from 1280: 37% fewer pixels, faster encode/decode
    private val MIN_RESOLUTION = 256 // Slightly higher floor for better quality at small sizes
    private val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    private val MAX_CACHE_BYTES = 50L * 1024 * 1024 // 50 MB
    private var lastEvictionTime = 0L
    private val EVICTION_INTERVAL_MS = 5 * 60 * 1000L // Only evict every 5 minutes

    fun compressImage(uri: Uri, compressSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            val now = System.currentTimeMillis()
            if (now - lastEvictionTime > EVICTION_INTERVAL_MS) {
                lastEvictionTime = now
                evictStaleCache()
            }
            var targetSize = compressSize
            // Cap at 1MB: no carrier supports MMS above this, and a 100MB default
            // would cause the compressor to skip compression entirely.
            if (targetSize <= 0 || targetSize > FILE_SIZE_1_MB) {
                targetSize = FILE_SIZE_1_MB
            }
            var bitmap: Bitmap? = null
            try {
                evictStaleCache()

                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                if (!mimeType.isImageMimeType()) {
                    callback.invoke(uri)
                    return@ensureBackgroundThread
                }

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    throw Exception("Failed to decode bounds")
                }

                // Downsample if EITHER side exceeds the cap (was &&, which skipped
                // panoramas/portraits oversized on one side only).
                var inSampleSize = 1
                if (options.outHeight > MAX_RESOLUTION || options.outWidth > MAX_RESOLUTION) {
                    val halfHeight: Int = options.outHeight / 2
                    val halfWidth: Int = options.outWidth / 2
                    while (halfHeight / inSampleSize >= MAX_RESOLUTION || halfWidth / inSampleSize >= MAX_RESOLUTION) {
                        inSampleSize *= 2
                    }
                }

                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                // Halve memory for opaque photos; decoded config is upgraded below if alpha is present.
                if (mimeType.equals("image/jpeg", ignoreCase = true) || mimeType.equals("image/jpg", ignoreCase = true)) {
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                }

                bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: throw Exception("Failed to decode")

                bitmap = applyExifOrientation(uri, bitmap!!)

                // Further scaling if still above MAX_RESOLUTION after inSampleSize
                if (bitmap!!.width > MAX_RESOLUTION || bitmap!!.height > MAX_RESOLUTION) {
                    val ratio = bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                    val newWidth: Int
                    val newHeight: Int
                    if (ratio > 1) {
                        newWidth = MAX_RESOLUTION
                        newHeight = (MAX_RESOLUTION / ratio).toInt()
                    } else {
                        newHeight = MAX_RESOLUTION
                        newWidth = (MAX_RESOLUTION * ratio).toInt()
                    }
                    val scaled = Bitmap.createScaledBitmap(bitmap!!, newWidth, newHeight, true)
                    recycleQuietly(bitmap)
                    bitmap = scaled
                }

                val useWebP = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                val hasAlpha = bitmap!!.hasAlpha()
                // JPEG / lossy WebP drop alpha (transparent PNGs would turn black).
                // Flatten onto white, or fall back to PNG when transparency must be kept.
                val flattenAlpha = hasAlpha && (mimeType.equals("image/png", ignoreCase = true))
                // Track whether flatten succeeded (for format selection below)
                var flattenSucceeded = true
                val output: Bitmap
                if (hasAlpha && !flattenAlpha) {
                    output = bitmap!!
                    bitmap = null // ownership transferred, don't recycle below
                } else if (hasAlpha) {
                    val flat = try {
                        Bitmap.createBitmap(bitmap!!.width, bitmap!!.height, Bitmap.Config.ARGB_8888)
                    } catch (e: OutOfMemoryError) {
                        null
                    }
                    if (flat == null) {
                        // OOM: keep original bitmap with alpha (will be saved as PNG)
                        flattenSucceeded = false
                        output = bitmap!!
                        bitmap = null // ownership transferred
                    } else {
                        Canvas(flat).apply {
                            drawColor(Color.WHITE)
                            drawBitmap(bitmap!!, 0f, 0f, null)
                        }
                        recycleQuietly(bitmap)
                        bitmap = null
                        output = flat
                    }
                } else {
                    output = bitmap!!
                    bitmap = null
                }

                val extension: String
                val format: Bitmap.CompressFormat
                if (!hasAlpha && useWebP) {
                    extension = ".webp"
                    format = Bitmap.CompressFormat.WEBP_LOSSY
                } else if (hasAlpha && (!flattenAlpha || !flattenSucceeded)) {
                    extension = ".png"
                    format = Bitmap.CompressFormat.PNG
                } else {
                    extension = ".jpg"
                    format = Bitmap.CompressFormat.JPEG
                }

                // Encode to memory per step, write the file once — avoids rewriting
                // full files and deleting oversized attempts on disk.
                // Resolution ladder: if min quality still exceeds the target,
                // halve the max dimension and retry, down to MIN_RESOLUTION.
                var maxRes = MAX_RESOLUTION
                var best: Pair<ByteArray, Int>? = null
                var fitted: Bitmap? = null
                while (true) {
                    recycleQuietly(fitted)
                    fitted = scaleToFit(output, maxRes)
                    best = if (format == Bitmap.CompressFormat.PNG) {
                        encode(fitted, format, 100) to 100
                    } else {
                        var quality = 75
                        var step: Pair<ByteArray, Int>? = null
                        while (quality >= 30) {
                            val bytes = encode(fitted, format, quality)
                            step = bytes to quality
                            if (targetSize <= 0 || bytes.size <= targetSize) {
                                break
                            }
                            quality = if (quality == 75) 50 else 30
                        }
                        step
                    }
                    val size = best?.first?.size ?: Int.MAX_VALUE
                    if (targetSize <= 0 || size <= targetSize || maxRes <= MIN_RESOLUTION) {
                        break
                    }
                    maxRes /= 2
                }
                recycleQuietly(if (output !== bitmap) output else null)
                recycleQuietly(fitted)

                val (bytes, _) = best ?: throw Exception("Failed to encode")
                val file = File(outputDirectory, "nova_comp_${UUID.randomUUID()}$extension")
                FileOutputStream(file).use { it.write(bytes) }

                callback.invoke(context.getNovaFileUri(file))
            } catch (e: Exception) {
                android.util.Log.e("ImageCompressor", "Nova Compression failed", e)
                callback.invoke(uri)
            } finally {
                recycleQuietly(bitmap)
            }
        }
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }

    /** Returns output scaled so its longest side is <= maxRes, or output itself if already small. */
    private fun scaleToFit(output: Bitmap, maxRes: Int): Bitmap {
        if (output.width <= maxRes && output.height <= maxRes) {
            return Bitmap.createBitmap(output)
        }
        val ratio = output.width.toFloat() / output.height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxRes
            newHeight = (maxRes / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxRes
            newWidth = (maxRes * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(output, newWidth, newHeight, true)
    }

    private fun recycleQuietly(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (_: Exception) {}
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            contentResolver.openInputStream(uri)?.use {
                val exif = ExifInterface(it)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                    else -> return bitmap
                }
                val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                recycleQuietly(bitmap)
                return transformed
            }
        } catch (_: Exception) {}
        return bitmap
    }

    /** Delete compressed files older than MAX_CACHE_AGE_MS; then trim oldest until under MAX_CACHE_BYTES. */
    private fun evictStaleCache() {
        try {
            val now = System.currentTimeMillis()
            val files = outputDirectory.listFiles() ?: return
            for (file in files) {
                if (now - file.lastModified() > MAX_CACHE_AGE_MS) {
                    file.delete()
                }
            }
            var remaining = (outputDirectory.listFiles() ?: return)
                .sortedBy { it.lastModified() }
            var total = remaining.sumOf { it.length() }
            for (file in remaining) {
                if (total <= MAX_CACHE_BYTES) break
                total -= file.length()
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
