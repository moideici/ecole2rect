package fr.ecole2recte.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Document
import fr.ecole2recte.app.core.model.SchoolLifeEvent
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * La vie scolaire.
 *
 * Absences, retards, sanctions et encouragements dans une seule liste chronologique, parce
 * que c'est ainsi qu'on les relit — et un compteur en tête, parce que c'est la première
 * question qu'on se pose.
 */
@Composable
fun SchoolLifeScreen(snapshot: SchoolSnapshot, onClose: () -> Unit) {
    val events = snapshot.schoolLife
    val absences = events.count { it.kind == SchoolLifeKind.ABSENCE }
    val late = events.count { it.kind == SchoolLifeKind.LATE }
    val unjustified = events.count { it.kind == SchoolLifeKind.ABSENCE && !it.justified }

    DetailScaffold(
        title = "Vie scolaire",
        subtitle = if (events.isEmpty()) null else "${events.size} événements cette année",
        onClose = onClose,
    ) {
        if (events.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.EventBusy,
                title = "Rien à signaler",
                description = "Aucune absence, aucun retard ni sanction n'a été enregistré, " +
                    "ou la vie scolaire n'a pas encore été synchronisée.",
            )
            return@DetailScaffold
        }

        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Counter("Absences", absences, Modifier.weight(1f))
                    Counter("Retards", late, Modifier.weight(1f))
                    Counter("Non justifiées", unjustified, Modifier.weight(1f), alert = unjustified > 0)
                }
            }
        }

        events.forEach { event ->
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard { SchoolLifeRow(event) }
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, modifier: Modifier = Modifier, alert: Boolean = false) {
    Column(modifier) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SchoolLifeRow(event: SchoolLifeEvent) {
    val color = when (event.kind) {
        SchoolLifeKind.ABSENCE -> MaterialTheme.colorScheme.error
        SchoolLifeKind.LATE -> AppTheme.palette.warning
        SchoolLifeKind.SANCTION -> MaterialTheme.colorScheme.error
        SchoolLifeKind.ENCOURAGEMENT -> AppTheme.palette.success
        SchoolLifeKind.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Spacer(Modifier.height(2.dp))
        SubjectDot(color, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.kind.label, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(Dates.dayAndMonth(event.date))
                    append(" · ")
                    append(Dates.time(event.start))
                    event.end?.let {
                        append(" — ")
                        append(Dates.time(it))
                    }
                    // La durée telle que l'établissement la compte : c'est elle qui figure
                    // sur le bulletin, pas l'écart entre les deux heures affichées.
                    if (event.duration.isNotBlank()) {
                        append(" · ")
                        append(event.duration)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (event.reason.isNotBlank()) {
                Text(event.reason, style = MaterialTheme.typography.bodyMedium)
            }
            if (event.comment.isNotBlank()) {
                Text(
                    event.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (event.kind == SchoolLifeKind.ABSENCE || event.kind == SchoolLifeKind.LATE) {
            Tag(
                text = if (event.justified) "Justifié" else "Non justifié",
                color = if (event.justified) AppTheme.palette.success else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Les documents de l'espace numérique.
 *
 * Regroupés par catégorie, tels que l'établissement les range : bulletins, pièces
 * administratives, factures. Ils s'ouvrent comme n'importe quelle pièce jointe.
 */
@Composable
fun DocumentsScreen(
    snapshot: SchoolSnapshot,
    attachmentState: fr.ecole2recte.app.AttachmentState,
    onOpen: (Document) -> Unit,
    onClose: () -> Unit,
) {
    val documents = snapshot.documents
    DetailScaffold(
        title = "Documents",
        subtitle = if (documents.isEmpty()) null else "${documents.size} documents",
        onClose = onClose,
    ) {
        if (documents.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.Folder,
                title = "Aucun document",
                description = "L'établissement n'a publié aucun document, " +
                    "ou l'espace documentaire n'est pas ouvert pour ce compte.",
            )
            return@DetailScaffold
        }
        documents.groupBy { it.category.ifBlank { "Documents" } }.forEach { (category, items) ->
            Text(
                category.uppercase(),
                modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { document ->
                val working = (attachmentState as? fr.ecole2recte.app.AttachmentState.Working)?.id == document.id
                val failure = (attachmentState as? fr.ecole2recte.app.AttachmentState.Failed)
                    ?.takeIf { it.id == document.id }
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard(onClick = { onOpen(document) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    document.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    document.date?.let { Dates.dayAndMonth(it) } ?: "Date inconnue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (working) Tag("Ouverture…")
                        }
                        if (failure != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                failure.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
