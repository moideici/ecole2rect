package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme

/** Les deux seules choses que l'application peut écrire chez ÉcoleDirecte. Éteintes par défaut. */
enum class WriteFeature(val title: String, val description: String, val confirmTitle: String, val confirmText: String) {
    HomeworkSync(
        title = "Devoirs faits sur ÉcoleDirecte",
        description = "",
        confirmTitle = "Écrire sur ÉcoleDirecte ?",
        confirmText = "Cocher un devoir le coche aussi sur ton compte ÉcoleDirecte. Rien d'autre n'est envoyé.",
    ),
    MessageSending(
        title = "Envoi de messages",
        description = "",
        confirmTitle = "Autoriser l'envoi ?",
        confirmText = "Chaque message est relu avant l'envoi : rien ne part sans ton toucher. Un message envoyé ne peut pas être repris.",
    ),
}

/** Faut-il demander confirmation en activant ? Oui, la première fois seulement. */
fun needsWriteConfirmation(turningOn: Boolean, acknowledged: Boolean): Boolean = turningOn && !acknowledged

@Composable
fun WriteAccessCard() {
    val settings = rememberAppContainer().settings
    val syncHomework by settings.syncHomework.collectAsStateWithLifecycle()
    val sendMessages by settings.sendMessages.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<WriteFeature?>(null) }

    fun apply(feature: WriteFeature, enabled: Boolean) = when (feature) {
        WriteFeature.HomeworkSync -> settings.setSyncHomework(enabled)
        WriteFeature.MessageSending -> settings.setSendMessages(enabled)
    }

    fun toggle(feature: WriteFeature, current: Boolean) {
        val acknowledged = when (feature) {
            WriteFeature.HomeworkSync -> settings.syncHomeworkAcknowledged
            WriteFeature.MessageSending -> settings.sendMessagesAcknowledged
        }
        if (needsWriteConfirmation(!current, acknowledged)) asking = feature else apply(feature, !current)
    }

    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(WriteFeature.HomeworkSync to syncHomework, WriteFeature.MessageSending to sendMessages).forEach { (feature, enabled) ->
                Row(Modifier.fillMaxWidth().clickable { toggle(feature, enabled) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(feature.title, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(checked = enabled, onCheckedChange = { toggle(feature, enabled) })
                }
            }
        }
    }

    asking?.let { feature ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(feature.confirmTitle) },
            text = { Text(feature.confirmText) },
            confirmButton = {
                TextButton(onClick = {
                    apply(feature, true)
                    asking = null
                }) { Text("Activer") }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Annuler") } },
        )
    }
}
