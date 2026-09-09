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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text as Format
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.LocalDate

/**
 * La liste des professeurs.
 *
 * ÉcoleDirecte n'en publie aucune : les noms sont dispersés sur les cours et sur les
 * moyennes, et il faut ouvrir un cours pour savoir qui le fait. Les rassembler coûte peu et
 * répond à une question qu'on se pose souvent — « c'est qui, déjà, en physique ? ».
 */
@Composable
fun TeachersScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onOpenTeacher: (String) -> Unit,
    onClose: () -> Unit,
) {
    val teachers = Insights.teachers(snapshot, today)
    DetailScaffold(
        title = "Professeurs",
        subtitle = if (teachers.isEmpty()) null else "${teachers.size} au total",
        onClose = onClose,
    ) {
        if (teachers.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.Person,
                title = "Aucun professeur connu",
                description = "Les noms apparaissent avec l'emploi du temps et les moyennes.",
            )
            return@DetailScaffold
        }
        teachers.forEach { teacher ->
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard(onClick = { onOpenTeacher(teacher.name) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectBadge(
                            teacher.name,
                            subjectColor(teacher.subjects.firstOrNull().orEmpty(), settings),
                            42.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                teacher.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                teacher.subjects.joinToString(", ").ifBlank { "matière inconnue" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (teacher.headTeacher) Tag("Principal")
                    }
                }
            }
        }
    }
}

/**
 * La fiche d'un professeur.
 *
 * Elle rassemble ce que le service laisse deviner : ses cours de la semaine, la moyenne de
 * ses matières et l'appréciation qu'il a rédigée, puis les devoirs et les notes **de ses
 * matières**.
 *
 * Cette dernière formulation n'est pas de la prudence de façade : ÉcoleDirecte ne dit pas
 * qui a donné un devoir ni qui a corrigé une note. Le lien passe par la matière, et l'écrire
 * « les devoirs qu'il a donnés » serait une affirmation que rien ne soutient — fausse dès
 * qu'une matière compte deux professeurs, ce qui arrive tout le temps en langues.
 */
@Composable
fun TeacherDetailScreen(
    name: String,
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onOpenHomework: (HomeworkItem) -> Unit,
    onClose: () -> Unit,
) {
    val profile = Insights.teacherProfile(snapshot, today, name)
    val weekStart = Dates.startOfWeek(today)
    val lessons = Insights.lessonsOfTeacher(snapshot.lessons, name, weekStart)
    val keys = profile.subjects.map { Format.normalizeKey(it) }.toSet()
    val homework = Insights.pendingHomework(snapshot.homework, settings, today)
        .filter { Format.normalizeKey(it.subject) in keys }
    val grades = snapshot.grades
        .filter { Format.normalizeKey(it.subject) in keys && it.value != null }
        .take(MAX_GRADES)

    DetailScaffold(
        title = name,
        subtitle = profile.subjects.joinToString(", ").takeIf { it.isNotBlank() },
        onClose = onClose,
    ) {
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectBadge(name, subjectColor(profile.subjects.firstOrNull().orEmpty(), settings))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (profile.lessonsThisWeek == 0) {
                                "Aucun cours cette semaine"
                            } else {
                                "${profile.lessonsThisWeek} cours cette semaine · " +
                                    Dates.duration(profile.minutesThisWeek)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (profile.headTeacher) Tag("Professeur principal")
                }
                if (profile.rooms.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DetailLine("Salles", profile.rooms.joinToString(", "))
                }
            }
        }

        // Les moyennes et l'appréciation : c'est la seule chose que le professeur a écrite
        // lui-même, et c'est ce qu'on vient relire.
        val averages = snapshot.subjectAverages
            .filter { Format.normalizeKey(it.subject) in keys }
            .distinctBy { it.subject }
        if (averages.isNotEmpty()) {
            SectionTitle("Ses matières")
            averages.forEach { average ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectDot(subjectColor(average.subject, settings), 11.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                average.subject,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                average.average?.let { Format.number(it) } ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (average.appreciation.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                average.appreciation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (lessons.isNotEmpty()) {
            SectionTitle("Cette semaine")
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard {
                    lessons.forEachIndexed { index, lesson ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(96.dp)) {
                                Text(
                                    Dates.compactDay(lesson.date).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    Dates.time(lesson.start),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    lesson.subject,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    lesson.room.ifBlank { "salle non précisée" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (lesson.canceled) Tag("Annulé", MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (homework.isNotEmpty()) {
            SectionTitle("Devoirs de ses matières")
            homework.take(MAX_HOMEWORK).forEach { item ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard(onClick = { onOpenHomework(item) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectDot(subjectColor(item.subject, settings), 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.subject, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(
                                        Dates.dueLabel(item.dueDate, today),
                                        Format.preview(item.content, 60).takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.exam) Tag("Évaluation", MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (grades.isNotEmpty()) {
            SectionTitle("Notes de ses matières")
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard {
                    grades.forEachIndexed { index, grade ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    grade.label.ifBlank { grade.subject },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        grade.date?.let { Dates.dayAndMonth(it) },
                                        "coef. ${Format.number(grade.coefficient)}",
                                        grade.classAverage?.let { "classe ${Format.number(it)}" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${Format.number(grade.value ?: 0.0)} / ${Format.number(grade.scale)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }

        if (lessons.isEmpty() && homework.isEmpty() && grades.isEmpty() && averages.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.Person,
                title = "Rien à montrer",
                description = "Ce professeur n'apparaît ni dans la semaine chargée ni dans les moyennes.",
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            // Le dire une fois, en petit, plutôt que de laisser croire à un lien que le
            // service ne donne pas.
            "Devoirs et notes sont rattachés par matière : ÉcoleDirecte ne dit pas qui les a donnés.",
            modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Au-delà, la fiche devient un bulletin : ce n'est pas ce qu'on vient y chercher. */
private const val MAX_GRADES = 8
private const val MAX_HOMEWORK = 5
