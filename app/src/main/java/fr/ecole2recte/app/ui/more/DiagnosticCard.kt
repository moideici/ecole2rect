package fr.ecole2recte.app.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Row
import fr.ecole2recte.app.data.TokenReplayPolicy
import fr.ecole2recte.app.data.active
import fr.ecole2recte.app.settings.DEFAULT_API_VERSION
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.diagnostic.DataDiagnostic
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * « Diagnostic des données » : la forme des réponses d'ÉcoleDirecte enregistrées sur le téléphone
 * (clés, types, tailles — jamais une valeur), le nombre d'entrées que les lecteurs ont écartées,
 * et l'état de la session. On peut tout copier sans exposer le moindre nom, note ou message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticCard(profile: ActiveProfile?) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    var open by remember { mutableStateOf(false) }
    ListCard(onClick = { open = true }) {
        Column(Modifier.padding(16.dp)) {
            Text("Diagnostic des données", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "La forme des réponses d'ÉcoleDirecte, sans aucune valeur personnelle : pour comprendre un affichage vide.",
                color = AppTheme.colors.muted,
                fontSize = 13.sp,
            )
        }
    }
    if (open) {
        val report = remember { diagnosticText(container, profile) }
        ModalBottomSheet(onDismissRequest = { open = false }, containerColor = AppTheme.colors.surface) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostic des données", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Copier ce texte pour le coller dans un ticket : il ne contient aucune valeur, seulement des clés et des types.",
                    color = AppTheme.colors.muted,
                    fontSize = 13.sp,
                )
                Text(
                    "Copier",
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { copy(context, report) }.padding(vertical = 8.dp),
                )
                var version by remember { mutableStateOf(container.settings.apiVersion.value) }
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text("Version d'API envoyée à ÉcoleDirecte") },
                    supportingText = { Text("À changer seulement si le service la refuse (code 517). Prise en compte à la prochaine connexion.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "Appliquer",
                        color = AppTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { container.settings.setApiVersion(version) }.padding(vertical = 8.dp),
                    )
                    Text(
                        "Rétablir $DEFAULT_API_VERSION",
                        color = AppTheme.colors.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            container.settings.setApiVersion(DEFAULT_API_VERSION)
                            version = DEFAULT_API_VERSION
                        }.padding(vertical = 8.dp),
                    )
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(report, color = AppTheme.colors.ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

/** Le diagnostic complet (session + forme des réponses), partagé par la carte Diagnostic et le signalement. */
internal fun diagnosticText(container: fr.ecole2recte.app.AppContainer, profile: ActiveProfile?): String = buildString {
    appendLine(sessionLines(container, profile))
    val sections = profile?.let { DataDiagnostic(container.responseCache).sections(CacheOwner(it.identifier, it.studentId)) }.orEmpty()
    if (sections.isEmpty()) appendLine("\nAucune réponse enregistrée pour l'instant.")
    sections.forEach { section ->
        appendLine()
        appendLine("== ${section.name} (${section.ageLabel})" + (section.rejected?.takeIf { it > 0 }?.let { " — $it entrée(s) écartée(s)" } ?: ""))
        append(section.shape)
    }
}

/** L'état de la session, sans aucun identifiant : version de l'application, de l'API et du jeton. */
private fun sessionLines(container: fr.ecole2recte.app.AppContainer, profile: ActiveProfile?): String = buildString {
    appendLine("ecole2recte ${fr.ecole2recte.app.BuildConfig.VERSION_NAME}")
    appendLine("version d'API : ${container.settings.apiVersion.value}")
    val known = container.tokenLog.load()
    appendLine("durée de vie du jeton, mesurée sur ce téléphone :")
    appendLine("  expiré après une inactivité de : ${duration(known.expiredAfterIdleMs)}")
    appendLine("  expiré à l'âge de : ${duration(known.expiredAfterAgeMs)}")
    appendLine("  rejoué avec succès après : ${duration(known.replayedAfterIdleMs)}")
    val window = TokenReplayPolicy.idleWindowMs(known)
    appendLine("  fenêtre de rejeu au lancement : " + if (window == 0L) "aucune (le jeton expire en quelques minutes)" else duration(window))
    val stored = container.credentialStore.active()?.session
    appendLine("session gardée : " + if (stored == null) "non" else "oui, dernier appel réussi il y a ${duration(System.currentTimeMillis() - stored.lastOkAt)}")
    appendLine("profil : " + if (profile == null) "aucun" else if (profile.studentId != null) "compte famille (élève choisi)" else "compte élève")
}

private fun duration(ms: Long?): String = when {
    ms == null -> "inconnu"
    ms < 90_000 -> "${ms / 1000} s"
    ms < 90 * 60_000 -> "${ms / 60_000} min"
    else -> "%.1f h".format(java.util.Locale.FRANCE, ms / 3_600_000.0)
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ecole2recte - diagnostic des données", text))
}
