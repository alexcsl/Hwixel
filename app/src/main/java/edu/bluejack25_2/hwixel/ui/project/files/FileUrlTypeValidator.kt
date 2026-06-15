package edu.bluejack25_2.hwixel.ui.project.files

import java.net.URI

/**
 * Enforces that a link's URL matches the chosen file type.
 *
 * - "drive"  -> host must be a Google Drive / Docs host (*.google.com)
 * - "github" -> host must be a GitHub host (github.com, *.github.com, *.githubusercontent.com, *.github.io)
 * - anything else ("other") -> any valid URL is accepted
 */
object FileUrlTypeValidator {

    fun matches(url: String, type: String): Boolean {
        val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase().orEmpty()
        if (host.isBlank()) return false
        return when (type) {
            TYPE_DRIVE -> host.endsWith(".google.com")
            TYPE_GITHUB -> host == "github.com" ||
                host.endsWith(".github.com") ||
                host.endsWith("githubusercontent.com") ||
                host.endsWith(".github.io")
            else -> true
        }
    }

    private const val TYPE_DRIVE = "drive"
    private const val TYPE_GITHUB = "github"
}
