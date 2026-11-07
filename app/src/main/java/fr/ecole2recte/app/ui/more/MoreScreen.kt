package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.BuildConfig
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ConnectionStatus
import fr.ecole2recte.app.ui.accounts.Avatar
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.PageHeader
import fr.ecole2recte.app.ui.common.ScrollToTopOnReselect
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.search.SearchEntry
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.ThemeMode

@Composable
fun MoreScreen(
    profile: ActiveProfile?,
    connection: ConnectionStatus,
    connectionMessage: String?,
    onOpenAccounts: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenMethod: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect("plus", listState)
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Plus") }
        item { SearchEntry(onOpenSearch) }
        item {
            ListCard {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenAccounts).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(profile?.initials ?: "?")
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(profile?.displayName ?: "Aucun compte", color = AppTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { ReportCard(profile) }
        item { Section("Apparence") }
        item { AppearanceCard() }
        item { Section("Notes") }
        item {
            ListCard(onClick = onOpenMethod) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Comment la moyenne est calculée", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Section("Vie scolaire") }
        item { SchoolLifeCard() }
        item { Section("Accueil") }
        item { HomeLayoutCard() }
        item { Section("Couleurs") }
        item { ColorsCard(profile) }
        item { Section("Sécurité") }
        item { fr.ecole2recte.app.ui.common.PrivacyCard() }
        item { SecurityAndMotionCard() }
        item { Section("Écriture") }
        item { WriteAccessCard() }
        item { Section("Connexion") }
        item {
            ListCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when (connection) {
                            ConnectionStatus.Connected -> "Connecté"
                            ConnectionStatus.Connecting -> "Connexion en cours…"
                            ConnectionStatus.Offline -> connectionMessage ?: "Hors ligne"
                        },
                        color = AppTheme.colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (connection == ConnectionStatus.Offline) {
                        Text(
                            "Réessayer maintenant",
                            modifier = Modifier.clickable(onClick = onRetryConnection).padding(vertical = 10.dp),
                            color = AppTheme.colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item { NotificationsSection() }
        item { Section("À propos") }
        item { AboutCard() }
        item { Section("Diagnostic") }
        item { DiagnosticCard(profile) }
        item { SyncJournalCard() }
        item {
            Text(
                "ecole2recte ${BuildConfig.VERSION_NAME}",
                color = AppTheme.colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 22.dp, top = 22.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = AppTheme.colors.ink,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp),
    )
}
