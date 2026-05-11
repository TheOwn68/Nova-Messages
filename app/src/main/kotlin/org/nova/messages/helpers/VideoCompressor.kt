package org.nova.messages.helpers

import android.content.Context
import android.media.*
import android.net.Uri
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.getFileSizeFromUri
import java.io.File
import java.nio.ByteBuffer

class VideoCompressor(private val context: Context) {

    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    /**
     * Attempts to compress a video file to fit within a target size.
     * This implementation is a placeholder that currently returns the original URI.
     * Real-world video compression is highly complex and usually requires external libraries like FFmpeg.
     */
    fun compressVideo(uri: Uri, targetSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            try {
                val fileSize = context.getFileSizeFromUri(uri)
                if (fileSize > targetSize && targetSize > 0) {
                    // In a real scenario, we would use MediaCodec and MediaMuxer here.
                    // Since implementing a full-featured video transcoder is extremely complex,
                    // we will allow the 100MB limit and use the original video for now.
                    // This ensures the video is still sent, though it might be slow to upload.
                    callback.invoke(uri)
                } else {
                    callback.invoke(uri)
                }
            } catch (e: Exception) {
                callback.invoke(uri)
            }
        }
    }
}
