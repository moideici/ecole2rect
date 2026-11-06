package fr.ecole2recte.app.export

import java.io.File
import java.text.Normalizer
import java.time.LocalDate

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    Pdf("PDF", "pdf", "application/pdf"),
    Ics("Calendrier (ICS)", "ics", "text/calendar"),
}

/** Ce que l'utilisateur a choisi dans l'écran d'export. */
data class ExportChoices(
    val format: ExportFormat = ExportFormat.Pdf,
    /** Les semaines à exporter quand l'emploi du temps alterne (1, 2 ou les deux). */
    val weeks: Set<Int> = setOf(1, 2),
    val from: LocalDate,
    val until: LocalDate,
    val grayscale: Boolean = false,
)

/** Les règles de l'export qui ne dépendent pas d'Android : avertissements et nom de fichier. */
object ScheduleExport {
    /**
     * Ce que l'utilisateur doit savoir avant d'exporter. Rien n'est inventé : une semaine inconnue est
     * dite inconnue, et l'on rappelle que les vacances ne sont pas connues de l'application.
     */
    fun warnings(schedule: TypicalSchedule, choices: ExportChoices): List<String> = buildList {
        if (schedule.isEmpty) {
            add("Aucun cours n'est enregistré sur le téléphone : ouvre l'agenda avec du réseau, puis réessaie.")
            return@buildList
        }
        if (schedule.alternating) {
            (choices.weeks.sorted()).filter { it !in schedule.knownCycles }.forEach {
                add("La semaine $it n'est pas connue de l'application (aucun cours enregistré pour elle) : elle sera incomplète.")
            }
        }
        if (choices.format == ExportFormat.Ics) {
            if (choices.until.isBefore(choices.from)) add("La date de fin est avant la date de début : le fichier sera vide.")
            add("Les vacances et jours fériés ne sont pas connus : les cours reviendront aussi pendant ces semaines.")
        }
    }

    /** `emploi-du-temps-jean-semaines-1-et-2.pdf` : sans accent, sans espace, sans caractère interdit. */
    fun fileName(format: ExportFormat, ownerLabel: String, alternating: Boolean, weeks: Set<Int>): String {
        val owner = slug(ownerLabel)
        val part = when {
            !alternating -> null
            weeks.size >= 2 || weeks.isEmpty() -> "semaines-1-et-2"
            else -> "semaine-${weeks.single()}"
        }
        return listOfNotNull("emploi-du-temps", owner.takeIf { it.isNotEmpty() }, part).joinToString("-") + "." + format.extension
    }

    internal fun slug(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(30)
}

/**
 * Le dossier privé des fichiers exportés (PDF, ICS) : vidé à chaque lancement et quand l'application se
 * ferme, comme celui des pièces jointes. Rien n'est envoyé nulle part : le fichier est seulement remis à
 * l'application que l'utilisateur choisit (partage ou ouverture).
 */
class ExportFiles(private val directory: File) {
    fun write(bytes: ByteArray, name: String): File {
        val folder = File(directory, System.nanoTime().toString()).also { it.mkdirs() }
        val safe = name.substringAfterLast('/').substringAfterLast('\\').filter { it.code >= 32 && it !in "<>:\"|?*" }.ifBlank { "export" }
        return File(folder, safe).also { it.writeBytes(bytes) }
    }

    fun clear() {
        directory.deleteRecursively()
    }

    companion object {
        fun at(context: android.content.Context) = ExportFiles(File(context.cacheDir, "exports"))
    }
}
