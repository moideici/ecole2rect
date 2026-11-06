package fr.ecole2recte.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.ScheduleChanges
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.subjectColor
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Hauteur d'une minute à l'échelle 1,0 : 54 dp par heure en semaine, de quoi voir une journée entière à l'écran. */
internal const val WEEK_DP_PER_MINUTE = 0.9f

/** La vue jour garde sa hauteur d'origine (1,2 dp la minute) à l'échelle 1,0. */
internal const val DAY_DP_PER_MINUTE = 1.2f

/**
 * Pincement à deux doigts pour changer d'échelle. Un seul doigt n'est jamais consommé : le défilement
 * vertical, le balayage d'un jour à l'autre et les appuis sur les cours restent intacts.
 */
fun Modifier.pinchToZoom(current: () -> Float, onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val gesture = event.calculateZoom()
                if (gesture != 1f) {
                    onZoom(ScheduleZoom.pinch(current(), gesture))
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed && !it.changedToUp() })
    }
}

/**
 * La semaine en colonnes (un jour par colonne) sur une frise horaire commune : chaque cours à sa vraie
 * hauteur, coloré par matière, doubles heures fondues, cours annulés barrés et atténués, cours modifiés
 * encadrés. [zoom] multiplie la hauteur ; l'en-tête des jours reste en place pendant le défilement.
 */
@Composable
fun WeekTimelineView(
    days: List<LocalDate>,
    byDate: Map<String, List<ScheduleEntry>>,
    today: LocalDate?,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (ScheduleEntry) -> Unit,
) {
    val timeline = remember(days, byDate) { buildWeekTimeline(days.map { it.toString() }, byDate) }
    val perMinute: Dp = (WEEK_DP_PER_MINUTE * ScheduleZoom.clamp(zoom)).dp
    val total = perMinute * timeline.duration
    val now = if (today != null && today in days) rememberClock() else null
    val currentZoom by rememberUpdatedState(zoom)
    val axisWidth = 34.dp

    Column(modifier.fillMaxSize().pinchToZoom({ currentZoom }, onZoomChange)) {
        Row(Modifier.fillMaxWidth().padding(start = axisWidth + 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
            days.forEach { day ->
                val isToday = day == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() },
                        color = if (isToday) AppTheme.colors.accent else AppTheme.colors.muted,
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        day.dayOfMonth.toString(),
                        color = if (isToday) AppTheme.colors.accent else AppTheme.colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 24.dp),
        ) {
            Box(Modifier.width(axisWidth).height(total)) {
                for (hour in timeline.fromMin / 60..timeline.toMin / 60) {
                    Text(
                        "$hour h",
                        color = AppTheme.colors.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.offset(y = (perMinute * (hour * 60 - timeline.fromMin) - 8.dp).coerceAtLeast(0.dp)),
                    )
                }
            }
            timeline.days.forEach { column ->
                WeekDayColumnView(
                    column = column,
                    timeline = timeline,
                    perMinute = perMinute,
                    total = total,
                    nowMin = now?.takeIf { column.date == today.toString() }?.let { it.hour * 60 + it.minute },
                    modifier = Modifier.weight(1f),
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun WeekDayColumnView(
    column: WeekDayColumn,
    timeline: WeekTimeline,
    perMinute: Dp,
    total: Dp,
    nowMin: Int?,
    modifier: Modifier,
    onSelect: (ScheduleEntry) -> Unit,
) {
    BoxWithConstraints(modifier.height(total).padding(horizontal = 1.dp)) {
        val columnWidth = maxWidth
        for (hour in timeline.fromMin / 60..timeline.toMin / 60) {
            Box(
                Modifier
                    .offset(y = perMinute * (hour * 60 - timeline.fromMin))
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.colors.muted.copy(alpha = .14f)),
            )
        }
        // Les temps libres (déjeuner, heures creuses) en fond très léger, sans texte : la colonne est étroite.
        column.timeline.gaps.forEach { gap ->
            Box(
                Modifier
                    .offset(y = perMinute * (gap.startMin - timeline.fromMin))
                    .fillMaxWidth()
                    .height(perMinute * gap.duration)
                    .background(AppTheme.colors.muted.copy(alpha = .06f)),
            )
        }
        column.timeline.lessons.forEach { lesson ->
            val entry = lesson.first
            val color = subjectColor(entry.subject.name, entry.subject.colorIndex)
            val canceled = lesson.entries.all { it.canceled }
            val modified = !canceled && (lesson.entries.any { it.modified } || entry.before != null)
            val height = perMinute * lesson.duration
            val laneWidth = columnWidth / lesson.lanes
            val content = blockContent(height.value, laneWidth.value)
            val selected = entry.copy(start = lesson.first.start, end = lesson.entries.last().end)
            val past = nowMin != null && nowMin >= lesson.endMin
            val live = nowMin != null && !canceled && nowMin >= lesson.startMin && nowMin < lesson.endMin
            val shape = RoundedCornerShape(6.dp)
            val description = listOfNotNull(
                entry.subject.name,
                "de ${lesson.first.start} à ${lesson.entries.last().end}",
                entry.subject.room.takeIf { it.isNotBlank() }?.let { "salle $it" },
                "annulé".takeIf { canceled },
                "modifié".takeIf { modified },
            ).joinToString(", ")
            Column(
                Modifier
                    .offset(x = laneWidth * lesson.lane, y = perMinute * (lesson.startMin - timeline.fromMin))
                    .width(laneWidth)
                    .height(height)
                    .padding(1.dp)
                    .alpha(if (past) .6f else 1f)
                    .background(color.copy(alpha = if (canceled) .10f else .26f), shape)
                    .border(
                        width = if (live || modified) 2.dp else 1.dp,
                        color = when {
                            live -> AppTheme.colors.accent
                            modified -> AppTheme.colors.error
                            else -> color.copy(alpha = if (canceled) .35f else .7f)
                        },
                        shape = shape,
                    )
                    .semantics { contentDescription = description }
                    .clickable { onSelect(selected) }
                    .padding(horizontal = 3.dp, vertical = 2.dp),
            ) {
                Text(
                    entry.subject.name,
                    color = if (canceled) AppTheme.colors.muted else AppTheme.colors.ink,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = content.titleLines,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (canceled) TextDecoration.LineThrough else null,
                )
                if (content.showRoom && entry.subject.room.isNotBlank()) {
                    Text(entry.subject.room, color = AppTheme.colors.muted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (content.showTime) {
                    Text(
                        "${lesson.first.start}–${lesson.entries.last().end}",
                        color = AppTheme.colors.muted,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                    )
                }
                val tag = when {
                    canceled -> "Annulé"
                    modified -> ScheduleChanges.tag(entry) ?: "Modifié"
                    else -> null
                }
                if (content.showTag && tag != null) {
                    Text(tag, color = AppTheme.colors.error, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        if (nowMin != null && nowMin in timeline.fromMin..timeline.toMin) {
            Row(Modifier.offset(y = perMinute * (nowMin - timeline.fromMin) - 3.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(AppTheme.colors.error, CircleShape))
                Box(Modifier.weight(1f).height(2.dp).background(AppTheme.colors.error))
            }
        }
    }
}
