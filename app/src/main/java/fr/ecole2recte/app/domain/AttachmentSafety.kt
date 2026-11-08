package fr.ecole2recte.app.domain

import java.util.Locale

/** Même contrôle pour les devoirs, messages et documents, avant téléchargement puis avant écriture. */
object AttachmentSafety {
    private val refused = setOf(
        "apk", "xapk", "apks", "aab", "dex", "jar", "exe", "msi", "bat", "cmd", "com", "scr", "dll", "so",
        "sh", "bash", "ps1", "vbs", "js", "jse", "wsf", "lnk", "html", "htm", "xhtml", "mht", "svg", "swf",
    )
    private val known = mapOf(
        "pdf" to "application/pdf",
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp",
        "txt" to "text/plain", "csv" to "text/csv", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation", "zip" to "application/zip",
        "mp3" to "audio/mpeg", "mp4" to "video/mp4",
    )
    private val refusedMime = setOf(
        "text/html", "application/xhtml+xml", "image/svg+xml", "application/javascript", "text/javascript",
        "application/x-javascript", "application/vnd.android.package-archive", "application/x-msdownload",
        "application/x-msdos-program", "application/x-executable", "application/x-sh", "text/x-shellscript",
        "application/java-archive", "application/x-bat", "application/x-powershell",
    )

    private fun extensionOf(name: String) = safeFileName(name).substringAfterLast('.', "").lowercase(Locale.ROOT)
    private fun normalizedMime(announced: String?) = announced?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        ?.takeUnless { it.isBlank() || it in setOf("application/octet-stream", "binary/octet-stream") }

    fun blockedReason(name: String, announced: String? = null): String? {
        val extension = extensionOf(name)
        if (extension in refused) return "Ce type de fichier (.$extension) n'est jamais ouvert par sécurité."
        val mime = normalizedMime(announced) ?: return null
        if (mime in refusedMime) return "Ce type de fichier ($mime) est refusé par sécurité."
        val expected = known[extension]
        if (expected != null && mime != expected) return "Le type annoncé ($mime) ne correspond pas au fichier .$extension."
        return null
    }

    /** Sans chemin ni caractères de contrôle, en conservant l'extension pendant le raccourcissement. */
    fun safeFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .filter { !it.isISOControl() && it !in "<>:\"|?*" }
            .trim().trim('.')
        return (if (cleaned.length > 80) cleaned.takeLast(80) else cleaned).ifBlank { "document" }
    }

    /** L'extension connue décide du type transmis à Android ; un type actif n'est jamais proposé. */
    fun mimeType(announced: String?, name: String): String = known[extensionOf(name)]
        ?: normalizedMime(announced)?.takeUnless { it in refusedMime } ?: "application/octet-stream"

    fun description(name: String, announced: String?, sizeBytes: Long?): String {
        val type = mimeType(announced, name).takeUnless { it == "application/octet-stream" } ?: "Type inconnu"
        val size = when {
            sizeBytes == null || sizeBytes < 0 -> "Taille inconnue"
            sizeBytes >= 1024 * 1024 -> "${FrenchFormat.decimal(sizeBytes / (1024.0 * 1024))} Mo"
            sizeBytes >= 1024 -> "${FrenchFormat.decimal(sizeBytes / 1024.0)} Ko"
            else -> "$sizeBytes octets"
        }
        return "$type · $size"
    }
}
