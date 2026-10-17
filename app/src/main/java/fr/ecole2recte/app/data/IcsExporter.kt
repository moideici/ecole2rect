package fr.ecole2recte.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.ecole2recte.app.core.export.Ics
import fr.ecole2recte.app.core.model.Lesson
import java.io.File

/**
 * Export de l'emploi du temps vers l'agenda du téléphone.
 *
 * Le fichier passe par le même dossier partageable que les pièces jointes, et par un
 * `Intent` de partage plutôt qu'un import silencieux : c'est à l'utilisateur de choisir
 * l'agenda de destination, pas à l'application d'écrire dans son calendrier.
 */
class IcsExporter(private val context: Context) {

    fun share(lessons: List<Lesson>, calendarName: String = "ecole2recte"): Intent? {
        if (lessons.isEmpty()) return null
        val directory = File(context.cacheDir, "pieces-jointes").apply { mkdirs() }
        val file = File(directory, "emploi-du-temps.ics")
        runCatching { file.writeText(Ics.export(lessons, calendarName)) }.onFailure { return null }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Emploi du temps")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
