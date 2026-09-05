package fr.ecole2recte.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Écriture et ouverture d'une pièce jointe.
 *
 * Le fichier est déposé dans le cache privé de l'application, pas dans les téléchargements
 * publics : il n'a rien à faire dans la galerie de l'appareil, et le système saura le
 * supprimer si la place manque. L'ouverture passe par un `FileProvider`, seule façon
 * d'autoriser une autre application à lire ce fichier sans le rendre public.
 */
class AttachmentOpener(private val context: Context) {

    fun save(name: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, sanitize(name))
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun viewIntent(uri: Uri, name: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType(name))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Le nom vient du serveur : il ne doit pas pouvoir désigner un dossier parent. */
    private fun sanitize(name: String): String = name
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .takeIf { it.isNotBlank() && it != "." && it != ".." }
        ?.take(120)
        ?: "piece-jointe"

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "txt", "csv" -> "text/plain"
        "html", "htm" -> "text/html"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "zip" -> "application/zip"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        // Type inconnu : laisser le système proposer, plutôt que d'imposer un mauvais choix.
        else -> "*/*"
    }

    private companion object {
        const val DIRECTORY = "pieces-jointes"
    }
}
