package fr.ecole2recte.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.model.SubjectAverage
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.Simulator
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

                    // Les extrêmes de la classe situent la moyenne bien mieux qu'un chiffre
                    // seul : 12 dans une classe qui va de 8 à 13 n'est pas 12 sur 4 à 19.
                    val range = listOfNotNull(
                        period?.minimum?.let { "min. ${formatGrade(it)}" },
                        period?.maximum?.let { "max. ${formatGrade(it)}" },
                        period?.headTeacher?.takeIf { it.isNotBlank() }?.let { "prof. principal $it" },
                    )
                    if (range.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            range.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    period?.appreciation?.takeIf { it.isNotBlank() }?.let { appreciation ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "APPRÉCIATION DU CONSEIL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(appreciation, style = MaterialTheme.typography.bodyMedium)
                        period?.councilDate?.let { date ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(
                                    "Conseil du ${Dates.dayAndMonth(date)}",
                                    period.councilTime.takeIf { it.isNotBlank() },
                                ).joinToString(" à "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (grades.isNotEmpty()) {
            item("history") { HistoryCard(Insights.averageHistory(grades)) }
            item("simulator") { SimulatorCard(snapshot, settings, subjects, now.toLocalDate()) }
        }

        if (subjects.isEmpty()) {
            item {
                StatePlaceholder(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = "Aucune note",
                    description = "Les notes apparaîtront ici dès leur publication.",
                    actionLabel = "Synchroniser",
                    onAction = onRefresh,
                )
            }
        }

        items(subjects, key = { it }) { subject ->
            val detail = subjectAverages.firstOrNull { it.subject == subject }
            SubjectCard(
                subject = subject,
                settings = settings,
                average = detail?.average ?: computedBySubject[subject],
                detail = detail,
                grades = grades.filter { it.subject == subject },
            )
        }
    }
}

/**
 * L'évolution de la moyenne, note après note.
 *
 * Une moyenne de 13 qui monte et une moyenne de 13 qui descend ne se vivent pas pareil : la
 * courbe dit en un regard ce qu'un chiffre seul cache.
 */
@Composable
private fun HistoryCard(history: List<Pair<java.time.LocalDate, Double>>) {
    if (history.size < 3) return
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = history.map { it.second }
    val minimum = (values.min() - 1).coerceAtLeast(0.0)
    val maximum = (values.max() + 1).coerceAtMost(20.0)
    val span = (maximum - minimum).coerceAtLeast(1.0)

    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
        AppCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ÉVOLUTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val delta = values.last() - values.first()
                Tag(
                    text = (if (delta >= 0) "↗ +" else "↘ ") + formatGrade(delta),
                    color = if (delta >= 0) AppTheme.palette.success else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(14.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
                fun y(value: Double) = (size.height * (1 - (value - minimum) / span)).toFloat()

                // Deux repères horizontaux suffisent : la courbe compte, pas la grille.
                listOf(0.25f, 0.75f).forEach { fraction ->
                    drawLine(
                        color = grid,
                        start = Offset(0f, size.height * fraction),
                        end = Offset(size.width, size.height * fraction),
                        strokeWidth = 1f,
                    )
                }
                values.forEachIndexed { index, value ->
                    if (index == 0) return@forEachIndexed
                    drawLine(
                        color = line,
                        start = Offset((index - 1) * stepX, y(values[index - 1])),
                        end = Offset(index * stepX, y(value)),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(
                    color = line,
                    radius = 7f,
                    center = Offset((values.size - 1) * stepX, y(values.last())),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Dates.dayAndMonth(history.first().first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatGrade(values.first())} → ${formatGrade(values.last())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Dates.dayAndMonth(history.last().first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Simulateur de moyenne.
 *
 * La question que tout élève se pose devant ses notes, et à laquelle aucune application ne
 * répond : « si je prends 14 au prochain devoir, ça donne quoi ? ». Le résultat est annoncé
 * comme une estimation calculée depuis les notes, jamais comme une moyenne officielle.
 */
@Composable
private fun SimulatorCard(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    subjects: List<String>,
    today: java.time.LocalDate,
) {
    if (subjects.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    var subject by remember(subjects) { mutableStateOf(subjects.first()) }
    var value by remember { mutableStateOf("14") }
    var coefficient by remember { mutableStateOf("1") }

    val hypothesis = Simulator.Hypothesis(
        subject = subject,
        value = value.replace(',', '.').toDoubleOrNull() ?: -1.0,
        coefficient = coefficient.replace(',', '.').toDoubleOrNull() ?: 1.0,
    )
    val outcome = Simulator.simulate(snapshot, hypothesis, today)

    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
        AppCard(onClick = { open = !open }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Simulateur", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Et si je prenais cette note au prochain devoir ?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (open) "Fermer" else "Ouvrir",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = open) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subjects, key = { it }) { entry ->
                            FilterPill(entry, selected = entry == subject) { subject = entry }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text("Note /20") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = coefficient,
                            onValueChange = { coefficient = it },
                            label = { Text("Coefficient") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (outcome == null) {
                        Text(
                            "Saisis une note comprise entre 0 et 20, et un coefficient positif.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        SimulationLine(subject, outcome.subjectBefore, outcome.subjectAfter, settings)
                        SimulationLine("Moyenne générale", outcome.generalBefore, outcome.generalAfter, settings)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Estimation calculée depuis tes notes de la période, pas la moyenne de l'établissement.",
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
private fun SimulationLine(label: String, before: Double?, after: Double?, settings: AppSettings) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectDot(subjectColor(label, settings))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            before?.let { formatGrade(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(" → ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            after?.let { formatGrade(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = when {
                before == null || after == null -> MaterialTheme.colorScheme.onSurface
                after >= before -> AppTheme.palette.success
                else -> MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun SubjectCard(
    subject: String,
    settings: AppSettings,
    average: Double?,
    detail: SubjectAverage?,
    grades: List<Grade>,
) {
    val teachers = detail?.teachers.orEmpty().ifEmpty { listOfNotNull(detail?.teacher?.takeIf { it.isNotBlank() }) }
    val appreciation = detail?.appreciation.orEmpty()
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
                            teachers.joinToString(", ").takeIf { it.isNotBlank() },
                            detail?.classAverage?.let { "classe ${formatGrade(it)}" },
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
                    val range = listOfNotNull(
                        detail?.minimum?.let { "min. ${formatGrade(it)}" },
                        detail?.maximum?.let { "max. ${formatGrade(it)}" },
                        detail?.coefficient?.let { "coef. ${formatGrade(it)}" },
                    )
                    if (range.isNotEmpty()) {
                        Text(
                            range.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
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

/** Du rouge au vert selon le niveau de maîtrise, quand l'établissement le chiffre. */
@Composable
private fun skillColor(value: Int?): androidx.compose.ui.graphics.Color = when (value) {
    1 -> MaterialTheme.colorScheme.error
    2 -> AppTheme.palette.warning
    3 -> MaterialTheme.colorScheme.primary
    4 -> AppTheme.palette.success
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    grade.kind.takeIf { it.isNotBlank() },
                    "coef. ${formatGrade(grade.coefficient)}",
                    grade.classAverage?.let { "classe ${formatGrade(it)}" },
                    // Le meilleur et le pire de la classe : c'est ce qui manque le plus
                    // souvent pour savoir ce que vaut une note.
                    grade.minimum?.let { "min. ${formatGrade(it)}" },
                    grade.maximum?.let { "max. ${formatGrade(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (grade.comment.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    grade.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Le socle commun se note par niveau, pas par point : une note de 12 et
            // « maîtrise fragile » ne disent pas la même chose, et la seconde est souvent
            // la plus utile pour progresser.
            if (grade.skills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                grade.skills.forEach { skill ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(skillColor(skill.value), 7.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull(skill.label, skill.level.takeIf { it.isNotBlank() })
                                .joinToString(" — "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
