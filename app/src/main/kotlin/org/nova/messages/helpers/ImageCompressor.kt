package org.nova.messages.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.fossify.commons.extensions.getCompressionFormat
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.extension
import org.nova.messages.extensions.getFileSizeFromUri
import org.nova.messages.extensions.isImageMimeType
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class ImageCompressor(private val context: Context) {
    private val contentResolver = context.contentResolver
    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val minResolution = 100

    fun compressImage(uri: Uri, compressSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            try {
                val fileSize = context.getFileSizeFromUri(uri)
                if (fileSize > compressSize && compressSize > 0) {
                    val mimeType = contentResolver.getType(uri)
                    if (mimeType != null && mimeType.isImageMimeType()) {
                        
                        // Use memory-efficient decoding
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        contentResolver.openInputStream(uri)?.use { 
                            BitmapFactory.decodeStream(it, null, options)
                        }

                        var width = options.outWidth
                        var height = options.outHeight

                        // Determine target format
                        val useWebP = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                        val extension = if (useWebP) ".webp" else ".jpg"
                        val format = if (useWebP) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
                        
                        var quality = 85
                        var scale = 1.0

                        var currentFile: File? = null
                        
                        // First pass: just re-encode with high quality
                        val originalBitmap = decodeBitmap(uri) ?: throw Exception("Failed to decode")
                        currentFile = writeToFile(originalBitmap, extension, format, quality)

                        // Iterative reduction if still too big
                        while (currentFile!!.length() > compressSize && (quality > 30 || scale > 0.2)) {
                            if (quality > 40) {
                                quality -= 15
                            } else {
                                scale *= 0.7
                                quality = 60 // reset quality for smaller resolution
                            }

                            if ((width * scale).toInt() < minResolution || (height * scale).toInt() < minResolution) break

                            val scaledBitmap = if (scale < 1.0) {
                                Bitmap.createScaledBitmap(originalBitmap, (width * scale).toInt(), (height * scale).toInt(), true)
                            } else {
                                originalBitmap
                            }

                            currentFile = writeToFile(scaledBitmap, extension, format, quality)
                        }

                        callback.invoke(context.getMyFileUri(currentFile!!))
                    } else {
                        callback.invoke(uri)
                    }
                } else {
                    callback.invoke(uri)
                }
            } catch (e: Exception) {
                callback.invoke(uri) // Fallback to original on any error
            }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return contentResolver.openInputStream(uri)?.use { 
            val bitmap = BitmapFactory.decodeStream(it)
            if (bitmap != null) determineImageRotation(uri, bitmap) else null
        }
    }

    private fun writeToFile(bitmap: Bitmap, extension: String, format: Bitmap.CompressFormat, quality: Int): File {
        val file = File(outputDirectory, System.currentTimeMillis().toString().plus(extension))
        FileOutputStream(file).use {
            bitmap.compress(format, quality, it)
        }
        return file
    }

    private fun determineImageRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            contentResolver.openInputStream(uri)?.use { 
                val exif = ExifInterface(it)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {}
        return bitmap
    }
}
