package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.BuildConfig
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.core.settings.SubjectPalette
import fr.ecole2recte.app.core.settings.ThemeChoice
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.subjectColor
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Les réglages.
 *
 * Trois idées : ce que l'application sait de toi, ce que tu peux changer, et ce qu'elle
 * conserve sur l'appareil. Rien n'est caché derrière un menu obscur.
 */
@Composable
fun SettingsScreen(
    auth: AuthState,
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    onClose: () -> Unit,
    onOpenDetail: (Detail) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onSelectAccount: (Account) -> Unit,
    onDiagnostics: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signedIn = auth as? AuthState.SignedIn
    val account = signedIn?.account ?: snapshot.account
    val nowMillis = System.currentTimeMillis()

    DetailScaffold("Réglages", account?.schoolName?.takeIf { it.isNotBlank() }, onClose) {
        SettingsSection("Compte") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectBadge(
                    account?.displayName.orEmpty().ifBlank { "É" },
                    MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account?.displayName?.takeIf { it.isNotBlank() } ?: "Compte ÉcoleDirecte",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        listOfNotNull(
                            account?.className?.takeIf { it.isNotBlank() },
                            account?.schoolName?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { "Profil synchronisé" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val others = signedIn?.accounts.orEmpty().filter { it.id != account?.id }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Autres profils de cette connexion",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                others.forEach { other ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAccount(other) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectDot(MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(other.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SettingsSection("Personnalisation") {
            SettingsRow(
                title = "Composer l'accueil",
                description = "Choisir les widgets affichés et leur ordre.",
                onClick = { onOpenDetail(Detail.HomeEditor) },
            )
            SettingsRow(
                title = "Couleurs des matières",
                description = "Une couleur par matière, identique partout dans l'application.",
                onClick = { onOpenDetail(Detail.SubjectColors) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Thème",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterPill(choice.label, selected = settings.theme == choice) { onTheme(choice) }
                }
            }
        }

        SettingsSection("Synchronisation") {
            DataSection.entries.forEach { section ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(section.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            sync.errorFor(section) ?: Dates.freshness(snapshot.syncedAt[section], nowMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sync.errorFor(section) != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    SubjectDot(
                        if (sync.errorFor(section) != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            AppTheme.palette.success
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                title = "Tout synchroniser",
                description = if (sync.offline) {
                    "Hors ligne : les données affichées viennent du cache local."
                } else {
                    "Relancer une synchronisation complète maintenant."
                },
                onClick = onRefresh,
            )
        }

        SettingsSection("Données et confidentialité") {
            Text(
                "Sur cet appareil : tes identifiants chiffrés par le Keystore Android, la dernière " +
                    "synchronisation en cache, et tes réglages. Rien n'est envoyé ailleurs qu'à " +
                    "ÉcoleDirecte, et aucun mot de passe, jeton ou cookie n'apparaît dans les diagnostics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diagnostics réseau", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Affiche le détail technique en cas d'erreur de connexion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.showDiagnostics, onCheckedChange = onDiagnostics)
            }
        }

        SettingsSection("Session") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                    .clickable(onClick = onSignOut)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Se déconnecter et effacer les données locales",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Text(
            "ecole2recte ${BuildConfig.VERSION_NAME} · client non officiel d'ÉcoleDirecte. " +
                "L'API utilisée n'est pas publique et peut changer sans préavis.",
            modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(Modifier.padding(horizontal = ScreenPadding)) {
        AppCard(content = content)
    }
}

@Composable
private fun SettingsRow(title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Composer l'accueil.
 *
 * Des flèches plutôt qu'un glisser-déposer : c'est utilisable au clavier, annoncé correctement
 * par les lecteurs d'écran, et personne ne perd sa carte en la faisant tomber hors de l'écran.
 */
@Composable
fun HomeEditorScreen(
    settings: AppSettings,
    onMove: (HomeWidget, Int) -> Unit,
    onToggle: (HomeWidget) -> Unit,
    onClose: () -> Unit,
) {
    DetailScaffold("Composer l'accueil", "Ordre et visibilité des widgets", onClose) {
        val widgets = settings.orderedWidgets
        widgets.forEachIndexed { index, setting ->
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(setting.widget.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                setting.widget.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = setting.visible, onCheckedChange = { onToggle(setting.widget) })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoveButton(
                            icon = Icons.Outlined.ArrowUpward,
                            description = "Monter ${setting.widget.label}",
                            enabled = index > 0,
                        ) { onMove(setting.widget, -1) }
                        MoveButton(
                            icon = Icons.Outlined.ArrowDownward,
                            description = "Descendre ${setting.widget.label}",
                            enabled = index < widgets.lastIndex,
                        ) { onMove(setting.widget, 1) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Position ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Couleurs des matières.
 *
 * Chaque matière connue — cours, devoirs ou notes — peut recevoir sa couleur. Sans choix,
 * la couleur est dérivée du nom de la matière : jamais deux teintes différentes pour la même
 * matière d'un écran à l'autre.
 */
@Composable
fun SubjectColorsScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    onPick: (String, Long?) -> Unit,
    onClose: () -> Unit,
) {
    val subjects = Insights.knownSubjects(snapshot)
    DetailScaffold("Couleurs des matières", "${subjects.size} matières connues", onClose) {
        if (subjects.isEmpty()) {
            Text(
                "Les matières apparaîtront ici après la première synchronisation.",
                modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        subjects.forEach { subject ->
            val current = subjectColor(subject, settings)
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(current, 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            subject,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Par défaut",
                            modifier = Modifier
                                .clickable { onPick(subject, null) }
                                .padding(6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(SubjectPalette.COLORS, key = { it }) { value ->
                            val color = Color(value)
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (color == current) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                    .clickable { onPick(subject, value) },
                            )
                        }
                    }
                }
            }
        }
    }
}
