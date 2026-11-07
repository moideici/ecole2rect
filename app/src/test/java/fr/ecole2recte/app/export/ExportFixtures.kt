package fr.ecole2recte.app.export

import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import java.time.LocalDate

/** Deux semaines consécutives d'un emploi du temps qui alterne (semaine 1 = celle du 2 novembre 2026). */
object ExportFixtures {
    val week1Monday: LocalDate = LocalDate.of(2026, 11, 2)

    fun lesson(
        date: String,
        start: String,
        end: String,
        name: String,
        room: String = "",
        teacher: String = "",
        canceled: Boolean = false,
        kind: String = "COURS",
        colorIndex: Int = 0,
    ) = ScheduleEntry(Subject(name, teacher, room, colorIndex), "", canceled, date, start, end, kind = kind)

    /** Lundi : double heure de maths ; mardi : anglais ; mercredi : physique (semaine 1) ou chimie (semaine 2) ; jeudi : SVT. */
    fun week(monday: LocalDate, second: Boolean, thursdayCanceled: Boolean = false): List<ScheduleEntry> {
        fun at(offset: Long) = monday.plusDays(offset).toString()
        return listOf(
            lesson(at(0), "08:00", "08:55", "Maths", "B12", "M. Petit", colorIndex = 1),
            lesson(at(0), "08:55", "09:50", "Maths", "B12", "M. Petit", colorIndex = 1),
            lesson(at(1), "10:00", "11:00", "Anglais", "A1", "Mme Roux", colorIndex = 2),
            if (second) lesson(at(2), "14:00", "15:00", "Chimie", "L2", "Mme Curie", colorIndex = 3)
            else lesson(at(2), "14:00", "15:00", "Physique", "L1", "M. Newton", colorIndex = 4),
            lesson(at(3), "09:00", "10:00", "SVT", "S3", "Mme Darwin", canceled = thursdayCanceled, colorIndex = 5),
        )
    }

    /** La semaine du 2 novembre (semaine 1) et celle du 9 (semaine 2), SVT annulé le jeudi 5. */
    val twoWeeks: List<ScheduleEntry> =
        week(week1Monday, second = false, thursdayCanceled = true) + week(week1Monday.plusWeeks(1), second = true)

    /** Deux semaines identiques : pas d'alternance. */
    val sameTwice: List<ScheduleEntry> = week(week1Monday, false) + week(week1Monday.plusWeeks(1), false)

    val alternation = Alternation(true, week1Monday)
}
