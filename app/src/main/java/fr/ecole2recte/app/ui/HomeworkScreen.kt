package fr.ecole2recte.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SectionError
import fr.ecole2recte.app.ui.components.StatePlaceholder
import java.time.LocalDateTime

private enum class HomeworkFilter(val label: String) {
    PENDING("À faire"),
    EXAMS("Évaluations"),
    DONE("Faits"),
    ALL("Tout"),
}

/**
 * Les devoirs.
 *
 * Regroupés par échéance, filtrables, cherchables, et cochables d'un geste — y compris hors
 * ligne, puisque l'état local fait foi et sera poussé au serveur dès que possible.
 */
@Composable
fun HomeworkScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    onOpen: (HomeworkItem) -> Unit,
    onToggle: (HomeworkItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val today = now.toLocalDate()
    var filter by remember { mutableStateOf(HomeworkFilter.PENDING) }
    var query by remember { mutableStateOf("") }

    val visible = snapshot.homework
        .filter { item ->
            val done = settings.isHomeworkDone(item.id, item.done)
            when (filter) {
                HomeworkFilter.PENDING -> !done
                HomeworkFilter.EXAMS -> item.exam && !done
                HomeworkFilter.DONE -> done
                HomeworkFilter.ALL -> true
            }
        }
        .filter { Insights.matches(query, it.subject, it.content) }
        .sortedWith(compareBy({ it.dueDate }, { it.subject }))

    val grouped = visible.groupBy { it.dueDate }
    val pendingCount = Insights.pendingHomework(snapshot.homework, settings, today).size

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            ScreenHeader(
                eyebrow = "Cahier de textes",
                title = "Devoirs",
                subtitle = when (pendingCount) {
                    0 -> "Rien à faire pour l'instant"
                    1 -> "1 devoir à faire"
                    else -> "$pendingCount devoirs à faire"
                },
                actions = { RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh) },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                placeholder = { Text("Rechercher une matière, un mot…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(HomeworkFilter.entries.toList(), key = { it.name }) { entry ->
                    FilterPill(entry.label, selected = filter == entry) { filter = entry }
                }
            }
        }

        sync.errorFor(DataSection.HOMEWORK)?.let { error ->
            item { SectionError(error, onRefresh) }
        }

        if (grouped.isEmpty()) {
            item {
                StatePlaceholder(
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    title = when {
                        query.isNotBlank() -> "Aucun résultat"
                        filter == HomeworkFilter.DONE -> "Aucun devoir terminé"
                        else -> "Rien à faire"
                    },
                    description = when {
                        query.isNotBlank() -> "Essaie un autre mot, ou change de filtre."
                        snapshot.homework.isEmpty() -> "Le cahier de textes n'a pas encore été synchronisé."
                        else -> "Tous les devoirs connus sont cochés."
                    },
                    actionLabel = "Synchroniser",
                    onAction = onRefresh,
                )
            }
        }

        grouped.forEach { (date, dayItems) ->
            item(key = "day-$date") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ScreenPadding + 4.dp,
                            end = ScreenPadding + 4.dp,
                            top = 18.dp,
                            bottom = 4.dp,
                        ),
                ) {
                    Text(
                        Dates.relativeDay(date, today).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (date.isBefore(today)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${dayItems.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            items(dayItems, key = { it.id }) { entry ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard {
                        HomeworkRow(
                            item = entry,
                            settings = settings,
                            today = today,
                            onToggle = onToggle,
                            onOpen = { onOpen(entry) },
                        )
                    }
                }
            }
        }
    }
}
