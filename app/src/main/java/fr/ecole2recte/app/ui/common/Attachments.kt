package fr.ecole2recte.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.domain.Attachment
import fr.ecole2recte.app.domain.AttachmentFile
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/**
 * Les règles de prudence sur un fichier venu d'ailleurs : on n'ouvre jamais ce qui s'exécute ou
 * s'interprète (programmes, scripts, pages web), et un nom de fichier ne sort jamais du dossier prévu.
 */
object AttachmentSafety {
    private val refused = setOf(
        "apk", "xapk", "apks", "aab", "dex", "jar", "exe", "msi", "bat", "cmd", "com", "scr", "dll", "so",
        "sh", "bash", "ps1", "vbs", "js", "jse", "wsf", "lnk", "html", "htm", "xhtml", "mht", "svg", "swf",
    )

    private val known = mapOf(
        "pdf" to "application/pdf",
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp",
        "txt" to "text/plain", "csv" to "text/csv",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "zip" to "application/zip",
        "mp3" to "audio/mpeg", "mp4" to "video/mp4",
    )

    private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase()

    /** Pourquoi on refuse d'ouvrir ce fichier, ou null s'il peut être proposé. */
    fun blockedReason(name: String): String? =
        if (extensionOf(name) in refused) "Ce type de fichier (.${extensionOf(name)}) n'est jamais ouvert par sécurité." else null

    /** Un nom sans chemin ni caractère de contrôle, de longueur raisonnable. */
    fun safeFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 32 && it !in "<>:\"|?*" }
            .trim().trim('.')
        val shortened = if (cleaned.length > 80) cleaned.takeLast(80) else cleaned
        return shortened.ifBlank { "document" }
    }

    /** Le type annoncé par le serveur, sauf s'il est vague : alors l'extension décide. */
    fun mimeType(announced: String?, name: String): String {
        val generic = announced.isNullOrBlank() || announced == "application/octet-stream" || announced.startsWith("text/html")
        return if (!generic) announced!! else known[extensionOf(name)] ?: "application/octet-stream"
    }
}

/** Le dossier privé des pièces jointes ouvertes : vidé à chaque lancement et quand l'application se ferme. */
class AttachmentFiles(private val directory: File) {
    /** Écrit le fichier dans un sous-dossier unique : deux pièces du même nom ne se mélangent pas. */
    fun write(bytes: ByteArray, name: String): File {
        val folder = File(directory, System.nanoTime().toString()).also { it.mkdirs() }
        return File(folder, AttachmentSafety.safeFileName(name)).also { it.writeBytes(bytes) }
    }

    fun clear() {
        directory.deleteRecursively()
    }

    companion object {
        fun at(context: Context) = AttachmentFiles(File(context.cacheDir, "attachments"))
    }
}

/**
 * Les pièces jointes d'un devoir ou d'un message. Rien ne se télécharge sans un geste explicite
 * suivi d'une confirmation ; le fichier est ouvert par l'application que l'utilisateur choisit.
 */
@Composable
fun AttachmentList(files: List<Attachment>, download: suspend (Attachment) -> AttachmentFile) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf<Attachment?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    Column {
        files.forEach { file ->
            val blocked = AttachmentSafety.blockedReason(file.name)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = busy == null) {
                    problem = blocked
                    if (blocked == null) asked = file
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = AppTheme.colors.muted)
                Text(file.name, color = AppTheme.colors.ink, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                if (busy == file.id) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                } else if (blocked == null) {
                    Text("Ouvrir", color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        problem?.let { Text(it, color = AppTheme.colors.error, fontSize = 13.sp) }
    }

    asked?.let { file ->
        AlertDialog(
            onDismissRequest = { asked = null },
            title = { Text("Ouvrir ce fichier ?") },
            text = {
                Text(
                    "« ${file.name} » vient d'ÉcoleDirecte. Il sera téléchargé sur ton téléphone, ouvert avec l'application de " +
                        "ton choix, puis effacé quand tu fermes ecole2recte. N'ouvre que les fichiers que tu attends.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asked = null
                    problem = null
                    busy = file.id
                    scope.launch {
                        try {
                            open(context, download(file))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: EcoleDirecteException) {
                            problem = error.message
                        } catch (error: ActivityNotFoundException) {
                            problem = "Aucune application ne sait ouvrir ce type de fichier."
                        } catch (error: Exception) {
                            problem = "Impossible de télécharger ce fichier pour le moment."
                        } finally {
                            busy = null
                        }
                    }
                }) { Text("Télécharger et ouvrir") }
            },
            dismissButton = { TextButton(onClick = { asked = null }) { Text("Annuler") } },
        )
    }
}

private fun open(context: Context, file: AttachmentFile) {
    AttachmentSafety.blockedReason(file.fileName)?.let { throw EcoleDirecteException(it) }
    val saved = AttachmentFiles.at(context).write(file.bytes, file.fileName)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", saved)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, AttachmentSafety.mimeType(file.mimeType, file.fileName))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(view, file.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
