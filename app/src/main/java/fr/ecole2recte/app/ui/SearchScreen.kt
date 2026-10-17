package fr.ecole2recte.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.diag.LoggedError
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Search
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.LocalDate

/**
 * Recherche globale.
 *
 * Une seule question, toutes les sections. Chaque résultat dit d'où il vient et ouvre
 * l'écran correspondant : chercher n'est utile que si l'on peut ensuite y aller.
 */
@Composable
fun SearchScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onOpen: (Search.Hit) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val hits = Search.query(snapshot, settings, query, today)

    DetailScaffold("Rechercher", "Cours, devoirs, notes, messages, vie scolaire", onClose) {
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Un mot, une matière, un professeur…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (query.length < 2) {
            StatePlaceholder(
                icon = Icons.Outlined.Search,
                title = "Cherche ce que tu veux",
                description = "Deux lettres suffisent. La recherche parcourt l'emploi du temps, " +
                    "les devoirs, les notes, les messages et la vie scolaire.",
            )
            return@DetailScaffold
        }

        if (hits.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.Search,
                title = "Aucun résultat",
                description = "Rien ne correspond à « $query » dans les données synchronisées.",
            )
            return@DetailScaffold
        }

        hits.groupBy { it.kind }.forEach { (kind, group) ->
            Text(
                kind.label.uppercase(),
                modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.forEach { hit ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard(onClick = { onOpen(hit) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectDot(subjectColor(hit.subject, settings), 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    hit.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Journal des erreurs de la session.
 *
 * Il n'existe que pour une raison : quand l'API change, savoir laquelle des six sections a
 * cassé, et pouvoir le coller dans un ticket. Tout y est déjà expurgé.
 */
@Composable
fun ErrorLogScreen(
    entries: List<LoggedError>,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    DetailScaffold(
        title = "Journal des erreurs",
        subtitle = if (entries.isEmpty()) null else "${entries.size} depuis le lancement",
        onClose = onClose,
    ) {
        if (entries.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.Search,
                title = "Aucune erreur",
                description = "Rien n'a échoué depuis le lancement de l'application.",
            )
            return@DetailScaffold
        }
        // Le plus récent d'abord : une erreur d'il y a une heure n'intéresse plus personne.
        entries.asReversed().forEach { entry ->
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.section,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Tag(Dates.freshness(entry.at, System.currentTimeMillis()))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                    if (entry.detail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                entry.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Vider le journal",
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = ScreenPadding + 4.dp, vertical = 18.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
