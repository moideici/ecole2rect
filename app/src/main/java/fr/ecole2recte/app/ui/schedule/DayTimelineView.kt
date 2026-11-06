package fr.ecole2recte.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.ScheduleChanges
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.ui.common.subjectIcon
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.subjectColor
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Une journée sur une échelle de temps : les heures à gauche, chaque cours à sa vraie hauteur,
 * les doubles heures d'un seul bloc, les pauses nommées entre les cours, le cours en cours
 * entouré et un trait pour « maintenant ». C'est la même frise dans l'agenda et sur l'accueil ;
 * [compact] la resserre pour l'accueil.
 */
@Composable
fun DayTimelineView(
    entries: List<ScheduleEntry>,
    now: LocalTime?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Échelle verticale choisie par l'utilisateur (voir [ScheduleZoom]) ; l'accueil garde 1,0. */
    zoom: Float = 1f,
    onSelect: (ScheduleEntry) -> Unit = {},
) {
    val timeline = buildDayTimeline(entries)
    if (timeline.lessons.isEmpty()) return
    val perMinute: Dp = ((if (compact) 0.95f else DAY_DP_PER_MINUTE) * ScheduleZoom.clamp(zoom)).dp
    val axisWidth = 44.dp
    val total = perMinute * (timeline.toMin - timeline.fromMin)
    val nowMin = now?.let { it.hour * 60 + it.minute }

    Row(modifier.fillMaxWidth()) {
        // Axe des heures
        Box(Modifier.width(axisWidth).height(total)) {
            for (hour in timeline.fromMin / 60..timeline.toMin / 60) {
                val y = perMinute * (hour * 60 - timeline.fromMin)
                Text(
                    "${hour} h",
                    color = AppTheme.colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.offset(y = (y - 8.dp).coerceAtLeast(0.dp)),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).height(total)) {
            val columnsWidth = maxWidth
            // Graduations
            for (hour in timeline.fromMin / 60..timeline.toMin / 60) {
                Box(
                    Modifier
                        .offset(y = perMinute * (hour * 60 - timeline.fromMin))
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppTheme.colors.muted.copy(alpha = .14f)),
                )
            }
            // Pauses
            timeline.gaps.forEach { gap ->
                val height = perMinute * gap.duration
                Box(
                    Modifier
                        .offset(y = perMinute * (gap.startMin - timeline.fromMin))
                        .fillMaxWidth()
                        .height(height)
                        .padding(vertical = 2.dp)
                        .background(AppTheme.colors.muted.copy(alpha = .07f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (height >= 24.dp) {
                        Text(
                            "${gap.label} · ${formatMinutes(gap.duration)}",
                            color = AppTheme.colors.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            // Cours
            timeline.lessons.forEach { lesson ->
                val entry = lesson.first
                val color = subjectColor(entry.subject.name, entry.subject.colorIndex)
                val height = perMinute * lesson.duration
                val laneWidth = columnsWidth / lesson.lanes
                val live = nowMin != null && !entry.canceled && nowMin >= lesson.startMin && nowMin < lesson.endMin
                val past = nowMin != null && nowMin >= lesson.endMin
                val canceled = lesson.entries.all { it.canceled }
                val selected = entry.copy(start = lesson.first.start, end = lesson.entries.last().end)
                Column(
                    Modifier
                        .offset(x = laneWidth * lesson.lane, y = perMinute * (lesson.startMin - timeline.fromMin))
                        .width(laneWidth)
                        .height(height)
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                        .alpha(if (past) .6f else 1f)
                        .background(color.copy(alpha = if (canceled) .12f else .22f), RoundedCornerShape(12.dp))
                        .then(
                            if (live) Modifier.border(2.dp, AppTheme.colors.accent, RoundedCornerShape(12.dp))
                            else Modifier.border(1.dp, color.copy(alpha = .55f), RoundedCornerShape(12.dp)),
                        )
                        .clickable { onSelect(selected) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            subjectIcon(entry.subject.name),
                            contentDescription = null,
                            tint = if (canceled) AppTheme.colors.muted else color,
                            modifier = Modifier.size(if (compact) 15.dp else 17.dp),
                        )
                        Text(
                            entry.subject.name,
                            color = if (canceled) AppTheme.colors.muted else AppTheme.colors.ink,
                            fontSize = if (compact) 13.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (canceled) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (height >= 46.dp) {
                        Text(
                            listOfNotNull(
                                "${lesson.first.start} – ${lesson.entries.last().end}",
                                entry.subject.room.takeIf { it.isNotBlank() }?.let { "salle $it" },
                            ).joinToString(" · "),
                            color = AppTheme.colors.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (height >= 70.dp && !compact && entry.subject.teacher.isNotBlank()) {
                        Text(entry.subject.teacher, color = AppTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ScheduleChanges.lines(entry).firstOrNull()?.takeIf { height >= 70.dp && !compact && !canceled }?.let { line ->
                        Text(
                            "avant : ${line.old}",
                            color = AppTheme.colors.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                    val tags = listOfNotNull(
                        "Maintenant".takeIf { live },
                        "Permanence".takeIf { entry.kind.equals("PERMANENCE", ignoreCase = true) },
                        lesson.multiLabel.takeIf { it.isNotEmpty() },
                        "Annulé".takeIf { canceled },
                        (ScheduleChanges.tag(entry) ?: "Modifié".takeIf { lesson.entries.any { it.modified } }).takeIf { !canceled },
                        "Évaluation".takeIf { entry.exam },
                    )
                    if (tags.isNotEmpty() && height >= if (compact) 62.dp else 54.dp) {
                        Text(
                            tags.joinToString(" · "),
                            color = if (canceled || lesson.entries.any { it.modified } || entry.before != null) AppTheme.colors.error else AppTheme.colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            // Maintenant
            if (nowMin != null && nowMin in timeline.fromMin..timeline.toMin) {
                Row(
                    Modifier.offset(y = perMinute * (nowMin - timeline.fromMin) - 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(AppTheme.colors.error, CircleShape))
                    Box(Modifier.weight(1f).height(2.dp).background(AppTheme.colors.error))
                }
            }
        }
    }
}

/** L'heure actuelle, rafraîchie chaque minute : sert à repérer le cours en cours. */
@Composable
fun rememberClock(): LocalTime {
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000)
        }
    }
    return time
}
