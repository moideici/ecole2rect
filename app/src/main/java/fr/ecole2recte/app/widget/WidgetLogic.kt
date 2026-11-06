package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.FrenchFormat
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.NextClass
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.sync.WidgetGradeItem
import fr.ecole2recte.app.sync.WidgetLesson
import fr.ecole2recte.app.sync.WidgetMail
import fr.ecole2recte.app.sync.WidgetSlot
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetTask
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Un jour de la semaine résumé pour le widget « Semaine ». */
data class WeekDay(val label: String, val lessons: Int, val canceled: Int, val first: String, val last: String)

/**
 * Ce que les widgets calculent à l'affichage. Fonctions pures : l'heure vient de l'appelant,
 * si bien que « dans 25 min » est juste quand le widget se redessine et non figé à la synchro.
 */
object WidgetLogic {
    private fun start(slot: WidgetSlot) = LocalDateTime.parse("${slot.date}T${slot.start}:00")
    private fun end(slot: WidgetSlot) = LocalDateTime.parse("${slot.date}T${slot.end}:00")

    /** Les cours pas encore finis et pas annulés, dans l'ordre. */
    fun upcoming(slots: List<WidgetSlot>, now: LocalDateTime): List<WidgetSlot> =
        slots.filter { !it.canceled && runCatching { end(it) > now }.getOrDefault(false) }
            .sortedWith(compareBy({ it.date }, { it.start }))

    /** « En cours · jusqu'à 11:00 », « dans 25 min », « dans 1 h 05 », « demain 08:00 ». */
    fun status(slot: WidgetSlot, now: LocalDateTime): String {
        val begins = start(slot)
        if (begins <= now) return "En cours · jusqu'à ${slot.end}"
        val minutes = Duration.between(now, begins).toMinutes()
        return when {
            begins.toLocalDate() == now.toLocalDate() && minutes < 60 -> "dans $minutes min"
            begins.toLocalDate() == now.toLocalDate() -> "dans ${FrenchFormat.hoursMinutes(minutes.toInt())}"
            begins.toLocalDate() == now.toLocalDate().plusDays(1) -> "demain ${slot.start}"
            else -> "${FrenchFormat.weekdayFull(begins.toLocalDate())} ${slot.start}"
        }
    }

    fun today(slots: List<WidgetSlot>, today: LocalDate): List<WidgetSlot> =
        slots.filter { it.date == today.toString() }.sortedBy { it.start }

    /** Combien de lignes de [rowDp] tiennent dans [heightDp] une fois l'en-tête ôté. */
    fun rowsThatFit(heightDp: Float, headerDp: Float, rowDp: Float, max: Int): Int =
        ((heightDp - headerDp) / rowDp).toInt().coerceIn(1, max)

    /** Les [days] prochains jours qui ont cours : nombre de cours, annulés, première et dernière heure. */
    fun week(slots: List<WidgetSlot>, today: LocalDate, days: Int = 5): List<WeekDay> =
        slots.filter { it.date >= today.toString() }
            .groupBy { it.date }
            .toSortedMap()
            .entries
            .take(days)
            .map { (date, list) ->
                val day = LocalDate.parse(date)
                val real = list.filter { !it.canceled }
                WeekDay(
                    label = FrenchFormat.weekdayShort(day) + " " + day.dayOfMonth,
                    lessons = real.size,
                    canceled = list.size - real.size,
                    first = real.minOfOrNull { it.start }.orEmpty(),
                    last = real.maxOfOrNull { it.end }.orEmpty(),
                )
            }

    /** Construit l'instantané des widgets à partir de ce que l'app vient de lire. */
    fun snapshot(
        updatedAt: Long,
        today: LocalDate,
        home: HomeData?,
        week: ScheduleWeek?,
        homework: List<Homework>,
        grades: GradeOverview?,
        inbox: List<MessagePreview>,
    ): WidgetSnapshot = snapshot(updatedAt, today, home?.nextClass, week, homework, grades, inbox)

    fun snapshot(
        updatedAt: Long,
        today: LocalDate,
        nextClass: NextClass?,
        week: ScheduleWeek?,
        homework: List<Homework>,
        grades: GradeOverview?,
        inbox: List<MessagePreview>,
    ): WidgetSnapshot {
        // Du lundi de la semaine en cours jusqu'à six jours après aujourd'hui : le widget « Semaine complète »
        // montre aussi les jours déjà passés de la semaine.
        val from = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()
        val limit = today.plusDays(6).toString()
        return WidgetSnapshot(
            updatedAt = updatedAt,
            nextClass = nextClass?.let {
                WidgetLesson(it.subject.name, it.startLabel, it.subject.room.takeIf { room -> room.isNotBlank() }?.let { room -> "Salle $room" }.orEmpty())
            },
            tasks = homework.filter { !it.done }.take(8).map {
                WidgetTask(it.subject, it.title, it.due, it.colorIndex, it.dueDate, it.exam)
            },
            lessons = week?.entries.orEmpty()
                .filter { it.date >= from && it.date <= limit && it.start.isNotBlank() && it.end.isNotBlank() }
                .take(120)
                .map {
                    WidgetSlot(it.date, it.start, it.end, it.subject.name, it.subject.room, it.subject.teacher, it.canceled, it.subject.colorIndex)
                },
            grades = grades?.recent.orEmpty().filter { it.value != null }.take(6).map {
                val value = it.value!!
                WidgetGradeItem(
                    it.subject,
                    (if (value % 1.0 == 0.0) value.toInt().toString() else FrenchFormat.decimal(value)) + "/" + it.scale.toInt(),
                    if (it.coefficient % 1.0 == 0.0) it.coefficient.toInt().toString() else FrenchFormat.decimal(it.coefficient),
                    it.label,
                    it.colorIndex,
                )
            },
            average = grades?.average.orEmpty().takeIf { it != "–" }.orEmpty(),
            unread = inbox.count { it.unread },
            mails = inbox.filter { it.unread }.take(3).map { WidgetMail(it.sender, it.preview) },
        )
    }
}
