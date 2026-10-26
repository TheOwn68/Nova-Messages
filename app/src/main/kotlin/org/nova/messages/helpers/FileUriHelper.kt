package org.nova.messages.helpers

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun Context.getNovaFileUri(file: File): Uri {
    // Use the real package name (applicationContext) since getPackageName() is spoofed
    val authority = "${applicationContext.packageName}.provider"
    return FileProvider.getUriForFile(this, authority, file)
}