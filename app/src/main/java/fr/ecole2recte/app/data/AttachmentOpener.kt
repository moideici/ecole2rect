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

    /**
     * [key] est l'identifiant de la pièce jointe côté serveur : il donne au fichier un dossier
     * à lui. Deux messages portant tous deux un « Devoir.pdf » s'écrasaient l'un l'autre, et
     * un lecteur externe qui relisait l'URI plus tard affichait le mauvais document.
     */
    fun save(key: String, name: String, bytes: ByteArray): Uri {
        val directory = File(File(context.cacheDir, DIRECTORY), sanitize(key)).apply { mkdirs() }
        val file = File(directory, sanitize(name))
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun viewIntent(uri: Uri, name: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType(name))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Le nom vient du serveur : il ne doit pas pouvoir désigner un dossier parent.
     *
     * Seuls les caractères dangereux sont retirés — séparateurs de chemin et caractères de
     * contrôle. Tout remplacer hors de l'ASCII mutilait les noms que l'élève reconnaît :
     * « Contrôle_1.pdf » devenait « Contr_le_1.pdf », et un nom entièrement accentué se
     * réduisait à une suite de soulignés.
     */
    private fun sanitize(name: String): String = name
        .map { character -> if (character in FORBIDDEN || character.code < 0x20) '_' else character }
        .joinToString("")
        .trim()
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

        /** Ce qu'un nom de fichier ne peut pas porter sans désigner autre chose que lui-même. */
        val FORBIDDEN = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
    }
}
