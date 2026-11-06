package fr.ecole2recte.app.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.BuildConfig
import fr.ecole2recte.app.diagnostic.DeviceInfo
import fr.ecole2recte.app.diagnostic.ProblemReport
import fr.ecole2recte.app.diagnostic.ReportCategory
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme

private const val ISSUES_URL = "https://github.com/moidefr/ecole2rect/issues/new"

/**
 * « Signaler un problème » : prépare un rapport que l'utilisateur relit, puis partage lui-même.
 * L'application n'envoie rien toute seule et n'a aucun serveur : le diagnostic joint ne contient
 * que des clés et des types, et le texte libre n'est lu que par la personne qui le reçoit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportCard(profile: ActiveProfile?, preselected: ReportCategory = ReportCategory.WrongData) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    var open by remember { mutableStateOf(false) }
    ListCard(onClick = { open = true }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Signaler un problème", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Une note absente, un plantage, une idée : le rapport est préparé, vous le relisez et vous choisissez où il part.",
                    color = AppTheme.colors.muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
    if (!open) return

    var category by remember { mutableStateOf(preselected) }
    var description by remember { mutableStateOf("") }
    var withDiagnostic by remember { mutableStateOf(true) }
    val diagnostic = remember { diagnosticText(container, profile) }
    val device = DeviceInfo(BuildConfig.VERSION_NAME, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "${Build.MANUFACTURER} ${Build.MODEL}")
    val report = ProblemReport.build(category, description, device, diagnostic.takeIf { withDiagnostic })

    ModalBottomSheet(onDismissRequest = { open = false }, containerColor = AppTheme.colors.surface) {
        Column(
            Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Signaler un problème", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportCategory.entries.forEach { entry ->
                    FilterChip(selected = category == entry, onClick = { category = entry }, label = { Text(entry.label, fontSize = 13.sp) })
                }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(ProblemReport.MAX_DESCRIPTION) },
                label = { Text("Que s'est-il passé ?") },
                supportingText = { Text("Évitez les noms, les notes et le contenu des messages : ce texte part tel quel.") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Joindre le diagnostic technique", color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Clés, types et tailles des réponses : aucune valeur personnelle.", color = AppTheme.colors.muted, fontSize = 12.sp)
                }
                Switch(checked = withDiagnostic, onCheckedChange = { withDiagnostic = it })
            }
            Button(onClick = { share(context, ProblemReport.title(category), report) }, modifier = Modifier.fillMaxWidth()) {
                Text("Envoyer…")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyReport(context, report) }, modifier = Modifier.weight(1f)) { Text("Copier", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        copyReport(context, report)
                        openIssues(context)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Ticket GitHub", fontSize = 13.sp) }
            }
            Text(
                "« Ticket GitHub » copie le rapport puis ouvre la page : collez-le dans le ticket. Rien n'est envoyé sans votre geste.",
                color = AppTheme.colors.muted,
                fontSize = 12.sp,
            )
            Text("Aperçu de ce qui partira", color = AppTheme.colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(report, color = AppTheme.colors.ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
            }
        }
    }
}

private fun share(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Envoyer le signalement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyReport(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ecole2recte - signalement", text))
}

private fun openIssues(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
