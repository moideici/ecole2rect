package fr.ecole2recte.app.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.MessageDraft
import fr.ecole2recte.app.domain.Recipient
import fr.ecole2recte.app.data.api.Recipients
import fr.ecole2recte.app.ui.common.MessagesViewModel
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Rédiger un message. Rien ne part sans deux gestes : « Relire et envoyer », puis « Envoyer » dans la
 * confirmation qui montre les destinataires, l'objet et le texte. Un envoi qui échoue n'est jamais
 * rejoué tout seul : le message reste ici, et c'est à l'utilisateur de retoucher « Envoyer ».
 */
@Composable
fun ComposeSheet(viewModel: MessagesViewModel, initialDraft: MessageDraft? = null, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var recipients by remember { mutableStateOf<List<Recipient>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf(initialDraft?.recipients.orEmpty().toSet()) }
    var filter by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(initialDraft?.subject.orEmpty()) }
    var body by remember { mutableStateOf(initialDraft?.body.orEmpty()) }
    var confirming by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            recipients = (initialDraft?.recipients.orEmpty() + viewModel.recipients()).distinctBy { it.id to it.type }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = error.message ?: "Destinataires indisponibles."
        }
    }

    val draft = MessageDraft(chosen.toList(), subject, body)

    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nouveau message", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("À", color = AppTheme.colors.muted, fontSize = 13.sp)
        when {
            loadError != null -> Text(loadError!!, color = AppTheme.colors.error, fontSize = 14.sp)
            recipients == null -> CircularProgressIndicator(Modifier.size(24.dp), color = AppTheme.colors.accent)
            recipients!!.isEmpty() -> Text("Aucun destinataire.", color = AppTheme.colors.muted, fontSize = 14.sp)
            else -> {
                OutlinedTextField(filter, { filter = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Chercher un nom") })
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    Recipients.search(recipients!!, filter).forEach { person ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (person in chosen) chosen - person else chosen + person },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = person in chosen, onCheckedChange = { chosen = if (it) chosen + person else chosen - person })
                            Column {
                                Text(person.name, color = AppTheme.colors.ink, fontSize = 14.sp)
                                Text(person.group, color = AppTheme.colors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(subject, { subject = it.take(MessageDraft.MAX_SUBJECT + 1) }, label = { Text("Objet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it.take(MessageDraft.MAX_BODY + 1) }, label = { Text("Message") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        draft.problems().firstOrNull()?.takeIf { subject.isNotEmpty() || body.isNotEmpty() || chosen.isNotEmpty() }?.let {
            Text(it, color = AppTheme.colors.muted, fontSize = 13.sp)
        }
        sendError?.let { Text(it, color = AppTheme.colors.error, fontSize = 13.sp) }
        Text(
            "Relire et envoyer",
            color = if (draft.isReady && !sending) AppTheme.colors.accent else AppTheme.colors.muted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable(enabled = draft.isReady && !sending) {
                sendError = null
                confirming = true
            }.padding(vertical = 12.dp),
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Envoyer ce message ?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(draft.confirmation(), fontSize = 14.sp)
                    Text("Un message envoyé ne peut pas être repris.", color = AppTheme.colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = !sending, onClick = {
                    confirming = false
                    sending = true
                    scope.launch {
                        try {
                            viewModel.send(draft)
                            onSent()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            sendError = MessageDraft.failureNotice(error.message)
                        } finally {
                            sending = false
                        }
                    }
                }) { Text("Envoyer") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Modifier") } },
        )
    }
}
