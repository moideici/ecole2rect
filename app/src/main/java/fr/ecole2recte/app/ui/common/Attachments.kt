package fr.ecole2recte.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import fr.ecole2recte.app.domain.AttachmentSafety
import fr.ecole2recte.app.domain.AttachmentFile
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    var notice by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<Attachment?>(null) }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = saving
        saving = null
        val uri = result.data?.data
        if (file != null && result.resultCode == Activity.RESULT_OK && uri != null) {
            busy = file.id
            scope.launch {
                try {
                    val downloaded = download(file)
                    AttachmentSafety.blockedReason(downloaded.fileName, downloaded.mimeType)?.let { throw EcoleDirecteException(it) }
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: throw java.io.IOException("Destination indisponible")
                        output.use { it.write(downloaded.bytes) }
                    }
                    notice = "Fichier enregistré à l'emplacement choisi."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: EcoleDirecteException) {
                    problem = error.message
                } catch (error: Exception) {
                    problem = "Enregistrement impossible. Vérifie l'espace disponible et l'emplacement choisi."
                } finally {
                    busy = null
                }
            }
        }
    }

    Column {
        files.forEach { file ->
            val blocked = AttachmentSafety.blockedReason(file.name, file.mimeType)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = busy == null && saving == null) {
                    problem = blocked
                    notice = null
                    if (blocked == null) asked = file
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = AppTheme.colors.muted)
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(file.name, color = AppTheme.colors.ink, fontSize = 14.sp)
                    Text(AttachmentSafety.description(file.name, file.mimeType, file.sizeBytes), color = AppTheme.colors.muted, fontSize = 12.sp)
                }
                if (busy == file.id) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = AppTheme.colors.accent, strokeWidth = 2.dp)
                } else if (blocked == null) {
                    Text("Ouvrir", color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        problem?.let { Text(it, color = AppTheme.colors.error, fontSize = 13.sp) }
        notice?.let { Text(it, color = AppTheme.colors.muted, fontSize = 13.sp) }
    }

    asked?.let { file ->
        AlertDialog(
            onDismissRequest = { asked = null },
            title = { Text("Télécharger ce fichier ?") },
            text = {
                Text(
                    "« ${file.name} »\n${AttachmentSafety.description(file.name, file.mimeType, file.sizeBytes)}\n\nLe fichier ouvert est effacé à la fermeture de l'app. Une copie enregistrée reste à l'emplacement choisi. N'ouvre que les fichiers que tu attends.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
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
                                problem = "Aucune application pour ce fichier."
                            } catch (error: Exception) {
                                problem = "Téléchargement impossible."
                            } finally {
                                busy = null
                            }
                        }
                    }) { Text("Télécharger et ouvrir") }
                    TextButton(onClick = {
                        asked = null
                        problem = null
                        saving = file
                        try {
                            saveDocument.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(AttachmentSafety.mimeType(file.mimeType, file.name))
                                    .putExtra(Intent.EXTRA_TITLE, AttachmentSafety.safeFileName(file.name))
                                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.providers.downloads.documents/root/downloads")),
                            )
                        } catch (error: ActivityNotFoundException) {
                            saving = null
                            problem = "Le sélecteur de fichiers Android n'est pas disponible."
                        }
                    }) { Text("Enregistrer dans Téléchargements") }
                }
            },
            dismissButton = { TextButton(onClick = { asked = null }) { Text("Annuler") } },
        )
    }
}

private fun open(context: Context, file: AttachmentFile) {
    AttachmentSafety.blockedReason(file.fileName, file.mimeType)?.let { throw EcoleDirecteException(it) }
    val saved = AttachmentFiles.at(context).write(file.bytes, file.fileName)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", saved)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, AttachmentSafety.mimeType(file.mimeType, file.fileName))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(view, file.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
