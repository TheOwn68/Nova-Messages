package org.nova.messages.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.isImageMimeType
import java.io.File
import java.io.FileOutputStream

class ImageCompressor(private val context: Context) {
    private val contentResolver = context.contentResolver
    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val MAX_RESOLUTION = 1280 // Standard "efficient" mobile resolution

    fun compressImage(uri: Uri, compressSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            try {
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                
                if (mimeType.isImageMimeType()) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    contentResolver.openInputStream(uri)?.use { 
                        BitmapFactory.decodeStream(it, null, options)
                    }

                    var inSampleSize = 1
                    if (options.outHeight > MAX_RESOLUTION || options.outWidth > MAX_RESOLUTION) {
                        val halfHeight: Int = options.outHeight / 2
                        val halfWidth: Int = options.outWidth / 2
                        while (halfHeight / inSampleSize >= MAX_RESOLUTION && halfWidth / inSampleSize >= MAX_RESOLUTION) {
                            inSampleSize *= 2
                        }
                    }

                    options.inJustDecodeBounds = false
                    options.inSampleSize = inSampleSize
                    
                    var bitmap = contentResolver.openInputStream(uri)?.use { 
                        BitmapFactory.decodeStream(it, null, options)
                    } ?: throw Exception("Failed to decode")

                    bitmap = determineImageRotation(uri, bitmap)

                    // Further scaling if still above MAX_RESOLUTION after inSampleSize
                    if (bitmap.width > MAX_RESOLUTION || bitmap.height > MAX_RESOLUTION) {
                        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val newWidth: Int
                        val newHeight: Int
                        if (ratio > 1) {
                            newWidth = MAX_RESOLUTION
                            newHeight = (MAX_RESOLUTION / ratio).toInt()
                        } else {
                            newHeight = MAX_RESOLUTION
                            newWidth = (MAX_RESOLUTION * ratio).toInt()
                        }
                        bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    }

                    val useWebP = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    val extension = if (useWebP) ".webp" else ".jpg"
                    val format = if (useWebP) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
                    
                    var quality = 80
                    var currentFile: File? = null
                    
                    // Iterative compression to hit target size if specified
                    while (quality > 30) {
                        val file = File(outputDirectory, "nova_comp_${System.currentTimeMillis()}$extension")
                        FileOutputStream(file).use {
                            bitmap.compress(format, quality, it)
                        }
                        currentFile = file
                        
                        if (compressSize > 0 && file.length() > compressSize) {
                            quality -= 15
                            file.delete()
                        } else {
                            break
                        }
                    }
                    
                    // Fallback to one last attempt at min quality if we broke out and currentFile doesn't exist or is still too big
                    if (currentFile == null || (compressSize > 0 && currentFile.length() > compressSize && quality <= 30)) {
                        val file = File(outputDirectory, "nova_comp_${System.currentTimeMillis()}$extension")
                        FileOutputStream(file).use {
                            bitmap.compress(format, 30, it)
                        }
                        currentFile = file
                    }

                    callback.invoke(context.getMyFileUri(currentFile))
                } else {
                    callback.invoke(uri)
                }
            } catch (e: Exception) {
                android.util.Log.e("ImageCompressor", "Nova Compression failed", e)
                callback.invoke(uri)
            }
        }
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
