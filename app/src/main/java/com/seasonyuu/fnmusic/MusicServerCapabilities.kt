package com.seasonyuu.fnmusic

private val serverVersionPattern = Regex("""^v?([0-9]+)\.([0-9]+)\.([0-9]+)(?:[-+][0-9A-Za-z][0-9A-Za-z.+-]*)?$""")

/** The app-auth folder entry requires server version 1.0.10 or newer. Unknown versions stay hidden. */
internal fun supportsFolderAuthorization(serverVersion: String?): Boolean {
    val parts = serverVersionPattern.matchEntire(serverVersion?.trim().orEmpty())?.groupValues ?: return false
    val major = parts[1].toIntOrNull() ?: return false
    val minor = parts[2].toIntOrNull() ?: return false
    val patch = parts[3].toIntOrNull() ?: return false
    return when {
        major != 1 -> major > 1
        minor != 0 -> minor > 0
        else -> patch >= 10
    }
}
