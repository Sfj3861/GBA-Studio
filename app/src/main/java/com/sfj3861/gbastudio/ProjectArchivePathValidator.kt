package com.sfj3861.gbastudio

/**
 * Conservative, cross-platform ZIP entry path validation.
 * Canonical destination containment is still checked by ProjectRepository after this check.
 */
object ProjectArchivePathValidator {
    private const val MAX_PATH_LENGTH = 240

    fun isSafe(name: String): Boolean {
        if (name.isBlank() || name.length > MAX_PATH_LENGTH) return false
        if (name.startsWith("/") || name.contains('\\') || name.contains('\u0000')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return false

        val normalized = name.removeSuffix("/")
        if (normalized.isBlank()) return false
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        return true
    }
}
