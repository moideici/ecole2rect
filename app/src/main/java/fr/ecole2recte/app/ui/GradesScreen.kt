package fr.ecole2recte.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.ProgressBar
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SectionError
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.LocalDateTime

/**
 * Les notes.
 *
 * La moyenne affichée est celle de l'établissement quand il la publie ; à défaut seulement,
 * une moyenne calculée, et l'écran le dit. Faire passer une estimation pour un bulletin
 * serait la pire chose que cette page puisse faire.
 */
@Composable
fun GradesScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    onRefresh: () -> Unit,
) {
    val periods = snapshot.periods
    var selectedCode by remember(periods) {
        mutableStateOf(periods.firstOrNull { it.current }?.code ?: periods.firstOrNull()?.code)
    }
    val period = periods.firstOrNull { it.code == selectedCode }
    val grades = Insights.gradesForPeriod(snapshot.grades, selectedCode)
    val officialAverage = period?.average
    val computedAverage = Insights.weightedAverage(grades)
    val average = officialAverage ?: computedAverage

    val subjectAverages = snapshot.subjectAverages.filter { selectedCode == null || it.periodCode == selectedCode }
    val computedBySubject = Insights.averagesBySubject(grades)
    val subjects = (subjectAverages.map { it.subject } + grades.map { it.subject }).distinct().sorted()

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            ScreenHeader(
                eyebrow = "Bulletin",
                title = "Notes",
                subtitle = period?.name ?: "Toutes périodes",
                actions = { RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh) },
            )
        }

        if (periods.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(periods, key = { it.code }) { entry ->
                        FilterPill(entry.name, selected = entry.code == selectedCode) {
                            selectedCode = entry.code
                        }
                    }
                }
            }
        }

        sync.errorFor(DataSection.GRADES)?.let { error ->
            item { SectionError(error, onRefresh) }
        }

        item {
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)) {
                AppCard {
                    if (average == null) {
                        Text("Pas encore de moyenne", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Aucune note publiée pour cette période.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@AppCard
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(formatGrade(average), style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "/ 20",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        period?.classAverage?.let { classAverage ->
                            Tag("Classe ${formatGrade(classAverage)}", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (officialAverage != null) {
                            "Moyenne générale publiée par l'établissement"
                        } else {
                            "Moyenne calculée depuis les notes de la période"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    ProgressBar(fraction = (average / 20.0).toFloat())
                }
            }
        }

        if (subjects.isEmpty()) {
            item {
                StatePlaceholder(
                    icon = Icons.Outlined.ShowChart,
                    title = "Aucune note",
                    description = "Les notes apparaîtront ici dès leur publication.",
                    actionLabel = "Synchroniser",
                    onAction = onRefresh,
                )
            }
        }

        items(subjects, key = { it }) { subject ->
            SubjectCard(
                subject = subject,
                settings = settings,
                average = subjectAverages.firstOrNull { it.subject == subject }?.average
                    ?: computedBySubject[subject],
                classAverage = subjectAverages.firstOrNull { it.subject == subject }?.classAverage,
                teacher = subjectAverages.firstOrNull { it.subject == subject }?.teacher.orEmpty(),
                appreciation = subjectAverages.firstOrNull { it.subject == subject }?.appreciation.orEmpty(),
                grades = grades.filter { it.subject == subject },
            )
        }
    }
}

@Composable
private fun SubjectCard(
    subject: String,
    settings: AppSettings,
    average: Double?,
    classAverage: Double?,
    teacher: String,
    appreciation: String,
    grades: List<Grade>,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = subjectColor(subject, settings)
    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
        AppCard(onClick = { expanded = !expanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectDot(accent, 11.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        subject,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            teacher.takeIf { it.isNotBlank() },
                            classAverage?.let { "classe ${formatGrade(it)}" },
                            "${grades.size} note${if (grades.size > 1) "s" else ""}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    average?.let { formatGrade(it) } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    if (appreciation.isNotBlank()) {
                        Text(
                            appreciation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    grades.forEach { grade ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        GradeRow(grade)
                    }
                    if (grades.isEmpty()) {
                        Text(
                            "Aucune note détaillée pour cette matière.",
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
private fun GradeRow(grade: Grade) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                grade.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    grade.date?.let { Dates.dayAndMonth(it) },
                    "coef. ${formatGrade(grade.coefficient)}",
                    grade.classAverage?.let { "classe ${formatGrade(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!grade.counted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Non comptée dans la moyenne",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.palette.warning,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            grade.value?.let { "${formatGrade(it)} / ${formatGrade(grade.scale)}" } ?: "Absent",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
