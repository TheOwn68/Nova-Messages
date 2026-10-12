package org.nova.messages.extensions

fun String.getExtensionFromMimeType(): String {
    return when (lowercase()) {
        "image/png" -> ".png"
        "image/apng" -> ".apng"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}

fun String.isImageMimeType(): Boolean {
    return lowercase().startsWith("image")
}

fun String.isGifMimeType(): Boolean {
    return lowercase().endsWith("gif")
}

fun String.isVideoMimeType(): Boolean {
    return lowercase().startsWith("video")
}

fun String.isVCardMimeType(): Boolean {
    val lowercase = lowercase()
    return lowercase.endsWith("x-vcard") || lowercase.endsWith("vcard")
}

fun String.isAudioMimeType(): Boolean {
    return lowercase().startsWith("audio")
}

fun String.isCalendarMimeType(): Boolean {
    return lowercase().endsWith("calendar")
}

fun String.isPdfMimeType(): Boolean {
    return lowercase().endsWith("pdf")
}

fun String.isZipMimeType(): Boolean {
    return lowercase().endsWith("zip")
}

fun String.isPlainTextMimeType(): Boolean {
    return lowercase() == "text/plain"
}

fun String.stripCountryCodePrefix(): String {
    if (this.isEmpty()) return this
    if (this.length < 5) return this // Short codes or very short numbers
    
    // If it's a name, don't touch it.
    if (!this.any { it.isDigit() }) return this
    
    // Strip leading + and following country code (1-3 digits)
    // Most users wanting this just want the local part.
    // We'll strip +1 specifically if it exists, or + and up to 3 digits.
    return if (this.startsWith("+1")) {
        this.substring(2).trim()
    } else if (this.startsWith("+")) {
        // Generic: strip + and up to 3 digits if there's enough left
        val stripped = this.replaceFirst("^\\+\\d{1,3}".toRegex(), "").trim()
        if (stripped.length >= 7) stripped else this.substring(1).trim()
    } else {
        this
    }
}
