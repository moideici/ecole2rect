package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * L'emploi du temps dessiné à l'échelle du temps.
 *
 * Une liste de cartes dit *ce qu'il y a* ; une grille dit *comment la journée est faite*. Un
 * cours de trois heures y est trois fois plus haut qu'un cours d'une heure, un trou est un
 * vide qu'on voit sans le lire, et la pause méridienne saute aux yeux sans qu'on ait à la
 * nommer. C'est la lecture des emplois du temps papier, et celle de Pronote.
 *
 * Trois choix méritent d'être dits.
 *
 * **Les bornes viennent des cours, pas d'une convention.** Commencer à 8 h et finir à 18 h
 * laisserait un vide en haut pour qui commence à 10 h, et couperait le bas pour qui finit à
 * 19 h. La grille s'ajuste à la journée réelle.
 *
 * **Les cours qui se superposent sont côte à côte.** Une classe dédoublée donne deux cours à
 * la même heure : empilés, l'un cacherait l'autre et on croirait l'emploi du temps faux.
 *
 * **L'heure courante est une ligne**, comme dans un agenda : elle situe le regard sans
 * qu'aucun texte n'ait à l'expliquer.
 */

/** Hauteur d'une minute. Une heure vaut donc 66 dp — assez pour deux lignes de texte. */
private val MinuteHeight = 1.1.dp

/** La même échelle, resserrée : six colonnes ne peuvent pas être aussi hautes qu'une. */
private val WeekMinuteHeight = 0.62.dp

/** Largeur de la colonne des heures. Assez pour « 12:00 » sans le couper. */
private val GutterWidth = 42.dp

/** En deçà, un bloc ne peut pas porter son texte : on lui laisse ce minimum. */
private val MinimumBlockHeight = 26.dp

/** Hauteur qu'un bloc doit atteindre pour porter une ligne de texte sans la couper. */
private val CompactBlockHeight = 27.dp

/** Ce qu'une grille compacte s'autorise à prendre dans une carte d'accueil. */
private val CompactMaxHeight = 320.dp

/**
 * L'échelle d'une grille compacte, déduite de la journée elle-même.
 *
 * Une échelle fixe ne peut pas convenir : à 0,42 dp la minute, un cours de cinquante minutes
 * fait vingt-et-un points de haut et son nom est coupé en deux — ce qui est pire que de
 * perdre un peu de proportion. On part donc du **cours le plus court**, qui doit rester
 * lisible, et on redescend seulement si la journée entière déborde de la carte.
 */
private fun compactScale(lessons: List<Lesson>): Dp {
    val shortest = lessons.filterNot { it.canceled }
        .minOfOrNull { Duration.between(it.start, it.end).toMinutes() }
        ?.coerceAtLeast(1L)
        ?: 60L
    val readable = CompactBlockHeight / shortest.toFloat()
    val minutes = boundsOf(lessons).hours * 60
    val ceiling = CompactMaxHeight / minutes.toFloat()
    return minOf(readable, ceiling).coerceIn(0.3.dp, 0.9.dp)
}

/** Bornes d'affichage d'une journée, arrondies à l'heure. */
private data class GridBounds(val firstHour: Int, val lastHour: Int) {
    val hours: Int get() = (lastHour - firstHour).coerceAtLeast(1)
    fun heightAt(scale: Dp): Dp = scale * (hours * 60)

    /** Décalage d'un instant depuis le haut de la grille. */
    fun offsetOf(moment: LocalDateTime, scale: Dp): Dp =
        scale * (moment.toLocalTime().toSecondOfDay() / 60f - firstHour * 60)
}

private fun boundsOf(lessons: List<Lesson>): GridBounds {
    val active = lessons.filterNot { it.canceled }.ifEmpty { lessons }
    if (active.isEmpty()) return GridBounds(8, 18)
    val first = active.minOf { it.start.toLocalTime() }.hour
    val last = active.maxOf { it.end.toLocalTime() }
    // L'heure de fin est arrondie vers le haut : un cours qui finit à 17:05 ne doit pas
    // dépasser du cadre.
    val lastHour = if (last.minute > 0) last.hour + 1 else last.hour
    return GridBounds(first, lastHour.coerceAtLeast(first + 1))
}

/** Un cours et la colonne qui lui revient quand plusieurs se chevauchent. */
private data class Placed(val lesson: Lesson, val column: Int, val columns: Int)

/**
 * Range les cours en colonnes.
 *
 * Les cours qui se chevauchent forment un groupe, et chaque groupe se partage la largeur.
 * Un cours seul occupe donc toute la largeur, deux cours simultanés la moitié chacun — sans
 * qu'une classe dédoublée ne fasse rétrécir toute la journée.
 */
private fun place(lessons: List<Lesson>): List<Placed> {
    val sorted = lessons.sortedWith(compareBy({ it.start }, { it.end }))
    val placed = mutableListOf<Placed>()
    var group = mutableListOf<Lesson>()
    var groupEnd: LocalDateTime? = null

    fun flush() {
        if (group.isEmpty()) return
        val columnEnds = mutableListOf<LocalDateTime>()
        val assignments = group.map { lesson ->
            val free = columnEnds.indexOfFirst { !it.isAfter(lesson.start) }
            if (free >= 0) {
                columnEnds[free] = lesson.end
                lesson to free
            } else {
                columnEnds += lesson.end
                lesson to columnEnds.lastIndex
            }
        }
        assignments.forEach { (lesson, column) -> placed += Placed(lesson, column, columnEnds.size) }
        group = mutableListOf()
        groupEnd = null
    }

    sorted.forEach { lesson ->
        val end = groupEnd
        if (end != null && !lesson.start.isBefore(end)) flush()
        group += lesson
        groupEnd = maxOf(groupEnd ?: lesson.end, lesson.end)
    }
    flush()
    return placed
}

/**
 * La journée, à l'échelle.
 *
 * [now] n'est utilisé que si la date affichée est aujourd'hui : la ligne du temps présent
 * n'a aucun sens sur la journée de mardi prochain.
 */
@Composable
fun DayGrid(
    lessons: List<Lesson>,
    settings: AppSettings,
    date: LocalDate,
    now: LocalDateTime,
    onOpenLesson: (Lesson) -> Unit,
    modifier: Modifier = Modifier,
    /** Combien de devoirs sont à rendre pour ce cours : le bloc le signale d'un point. */
    homeworkFor: (Lesson) -> Int = { 0 },
    /**
     * Vue de plus loin : la journée entière tient dans une carte d'accueil, avec ses
     * proportions, et seule la matière reste écrite. L'échelle est déduite de la journée
     * pour qu'aucun nom ne se retrouve coupé.
     */
    compact: Boolean = false,
) {
    val showDetails = !compact
    val scale = if (compact) compactScale(lessons) else MinuteHeight
    val bounds = boundsOf(lessons)
    val placed = place(lessons)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Serrées, les lignes d'heures deviennent une trame illisible : on les espace.
    val hourStep = if (scale < 0.7.dp) 2 else 1
    // Le plancher compact vaut la hauteur d'une ligne : en dessous, le nom serait rogné,
    // et un bloc légèrement trop haut ment moins qu'un mot coupé en deux.
    val minimumBlock = if (showDetails) MinimumBlockHeight else CompactBlockHeight

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val lane = (maxWidth - GutterWidth - 4.dp).coerceAtLeast(1.dp)

        Box(Modifier.fillMaxWidth().height(bounds.heightAt(scale))) {
            // Les heures d'abord : elles sont le fond sur lequel les cours se posent.
            for (hour in bounds.firstHour..bounds.lastHour step hourStep) {
                val y = scale * ((hour - bounds.firstHour) * 60)
                Row(
                    Modifier.fillMaxWidth().offset(y = y),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d:00".format(hour),
                        modifier = Modifier.width(GutterWidth),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Box(Modifier.weight(1f).height(1.dp).background(outline))
                }
            }

            placed.forEach { entry ->
                val lesson = entry.lesson
                val minutes = Duration.between(lesson.start, lesson.end).toMinutes().coerceAtLeast(1)
                // Jamais négatif : Compose refuse une largeur négative, et un widget très
                // étroit ne doit pas faire tomber tout l'écran.
                val gouttiere = if (entry.columns > 1) 3.dp else 0.dp
                val width = ((lane / entry.columns) - gouttiere).coerceAtLeast(1.dp)
                LessonBlock(
                    lesson = lesson,
                    settings = settings,
                    now = now,
                    today = date == now.toLocalDate(),
                    homework = homeworkFor(lesson),
                    showDetails = showDetails,
                    modifier = Modifier
                        .offset(
                            x = GutterWidth + 4.dp + (lane / entry.columns) * entry.column,
                            y = bounds.offsetOf(lesson.start, scale),
                        )
                        .width(width)
                        .height((scale * minutes.toInt()).coerceAtLeast(minimumBlock)),
                    onClick = { onOpenLesson(lesson) },
                )
            }

            // La ligne du présent, uniquement aujourd'hui et à l'intérieur de la journée.
            if (date == now.toLocalDate()) {
                val minuteOfDay = now.toLocalTime().toSecondOfDay() / 60
                if (minuteOfDay in (bounds.firstHour * 60)..(bounds.lastHour * 60)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = bounds.offsetOf(now, scale))
                            .padding(start = GutterWidth)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonBlock(
    lesson: Lesson,
    settings: AppSettings,
    now: LocalDateTime,
    today: Boolean,
    homework: Int,
    showDetails: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = subjectColor(lesson.subject, settings)
    val current = today && !lesson.start.isAfter(now) && lesson.end.isAfter(now) && !lesson.canceled
    val description = buildList {
        add(lesson.subject.ifBlank { "Cours" })
        add("de ${Dates.time(lesson.start)} à ${Dates.time(lesson.end)}")
        if (lesson.room.isNotBlank()) add("salle ${lesson.room}")
        if (lesson.teacher.isNotBlank()) add(lesson.teacher)
        if (lesson.canceled) add("annulé")
        if (homework > 0) add(if (homework == 1) "1 devoir à rendre" else "$homework devoirs à rendre")
    }.joinToString(", ")

    Box(
        modifier
            // Rogné d'abord : sans cela, la barre de couleur déborde des coins arrondis.
            .clip(RoundedCornerShape(8.dp))
            .background(
                // Un fond très pâle et une barre franche : la couleur situe la matière sans
                // rendre le texte illisible, en clair comme en sombre.
                color.copy(alpha = if (lesson.canceled) 0.05f else 0.14f),
                RoundedCornerShape(8.dp),
            )
            .then(
                if (current) {
                    Modifier.border(1.5.dp, color, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (lesson.canceled) color.copy(alpha = 0.4f) else color),
        )
        Column(
            Modifier
                .align(if (showDetails) Alignment.TopStart else Alignment.CenterStart)
                .padding(start = 9.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Text(
                lesson.subject.ifBlank { "Cours" },
                style = if (showDetails) {
                    MaterialTheme.typography.labelLarge
                } else {
                    // Une ligne plus courte tient dans un bloc plus petit : c'est ce qui
                    // permet à un cours de cinquante minutes de garder son nom entier.
                    MaterialTheme.typography.labelSmall
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (lesson.canceled) TextDecoration.LineThrough else null,
            )
            if (showDetails) {
                Text(
                    listOfNotNull(
                        lesson.room.takeIf { it.isNotBlank() },
                        lesson.group.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { Dates.time(lesson.start) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDetails && lesson.canceled) {
                Text(
                    "Annulé",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
            }
        }
        // Un devoir à rendre pour ce cours se signale dans sa case, là où on le cherche.
        // Le point est décoratif : la phrase du bloc le dit déjà au lecteur d'écran.
        if (homework > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .width(6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

/**
 * La semaine, à la même échelle.
 *
 * Six colonnes qui partagent une seule règle d'heures : c'est ce qui permet de comparer deux
 * journées d'un regard — voir que le mardi finit plus tôt, que le mercredi est vide. Une
 * liste de six résumés ne le montre pas.
 */
@Composable
fun WeekGrid(
    days: List<LocalDate>,
    lessonsOf: (LocalDate) -> List<Lesson>,
    settings: AppSettings,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = days.flatMap(lessonsOf)
    val bounds = boundsOf(all)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val height = bounds.heightAt(WeekMinuteHeight)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidth = ((maxWidth - GutterWidth) / days.size.coerceAtLeast(1)).coerceAtLeast(1.dp)

        Column {
            // L'en-tête des jours, aligné sur les colonnes qu'il nomme.
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Spacer(Modifier.width(GutterWidth))
                days.forEach { day ->
                    Column(
                        Modifier
                            .width(columnWidth)
                            .clickable { onSelectDay(day) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            Dates.dayName(day).take(3).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day == today) MaterialTheme.colorScheme.primary else muted,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day == today) MaterialTheme.colorScheme.primary else muted,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(height)) {
                for (hour in bounds.firstHour..bounds.lastHour) {
                    val y = WeekMinuteHeight * ((hour - bounds.firstHour) * 60)
                    Row(
                        Modifier.fillMaxWidth().offset(y = y),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${hour}h",
                            modifier = Modifier.width(GutterWidth),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            fontSize = 10.sp,
                        )
                        Box(Modifier.weight(1f).height(1.dp).background(outline))
                    }
                }

                days.forEachIndexed { index, day ->
                    place(lessonsOf(day)).forEach { entry ->
                        val lesson = entry.lesson
                        val minutes = Duration.between(lesson.start, lesson.end).toMinutes().coerceAtLeast(1)
                        val slot = columnWidth / entry.columns
                        WeekBlock(
                            lesson = lesson,
                            settings = settings,
                            modifier = Modifier
                                .offset(
                                    x = GutterWidth + columnWidth * index + slot * entry.column,
                                    y = bounds.offsetOf(lesson.start, WeekMinuteHeight),
                                )
                                .width((slot - 2.dp).coerceAtLeast(1.dp))
                                .height((WeekMinuteHeight * minutes.toInt()).coerceAtLeast(14.dp)),
                            onClick = { onSelectDay(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBlock(
    lesson: Lesson,
    settings: AppSettings,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = subjectColor(lesson.subject, settings)
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = if (lesson.canceled) 0.06f else 0.2f))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    "${lesson.subject}, ${Dates.time(lesson.start)} à ${Dates.time(lesson.end)}" +
                        if (lesson.canceled) ", annulé" else ""
            }
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            lesson.subject,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (lesson.canceled) TextDecoration.LineThrough else null,
        )
    }
}
