package fr.ecole2recte.app.diagnostic

import fr.ecole2recte.app.domain.FrenchFormat
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Garde sur le téléphone le dernier plantage de l'application, pour qu'on puisse le signaler.
 *
 * Seuls les noms de classes et de méthodes sont retenus : jamais le message de l'exception, qui
 * pourrait citer une réponse d'ÉcoleDirecte (nom, note…). Rien n'est envoyé : c'est le bouton
 * « Signaler un problème » qui le joint, si l'utilisateur le veut.
 */
class CrashLog(private val file: File) {
    fun record(error: Throwable, at: Long = System.currentTimeMillis()) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(describe(error, at))
        }
    }

    fun last(): String? = runCatching { file.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    companion object {
        private const val MAX_FRAMES = 30
        private const val MAX_CAUSES = 4

        fun describe(error: Throwable, at: Long): String = buildString {
            val moment = FrenchFormat.isoTime(at)
            appendLine("plantage du $moment")
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth <= MAX_CAUSES) {
                appendLine((if (depth == 0) "" else "causé par ") + current.javaClass.name)
                current.stackTrace.take(MAX_FRAMES).forEach { appendLine("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
                current = current.cause?.takeIf { it !== current }
                depth++
            }
        }
    }
}
