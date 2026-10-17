package fr.ecole2recte.app.core.export

import fr.ecole2recte.app.core.model.Lesson
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Export iCalendar de l'emploi du temps.
 *
 * Le format est vieux, verbeux et pointilleux — mais c'est celui que comprennent tous les
 * agendas, ce qui permet de sortir son emploi du temps de l'application sans dépendre d'elle.
 * Les cours annulés sont exportés comme tels plutôt qu'omis : un trou inexpliqué dans un
 * agenda est pire qu'un événement barré.
 */
object Ics {

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun export(
        lessons: List<Lesson>,
        calendarName: String = "ecole2recte",
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val builder = StringBuilder()
        builder.line("BEGIN:VCALENDAR")
        builder.line("VERSION:2.0")
        builder.line("PRODID:-//ecole2recte//emploi du temps//FR")
        builder.line("CALSCALE:GREGORIAN")
        builder.line("METHOD:PUBLISH")
        builder.line("X-WR-CALNAME:" + escape(calendarName))
        lessons.sortedBy { it.start }.forEach { lesson ->
            builder.line("BEGIN:VEVENT")
            builder.line("UID:" + lesson.id + "@ecole2recte")
            builder.line("DTSTAMP:" + utc(lesson.start.atZone(zone)))
            builder.line("DTSTART:" + lesson.start.format(STAMP))
            builder.line("DTEND:" + lesson.end.format(STAMP))
            builder.line("SUMMARY:" + escape(summary(lesson)))
            if (lesson.room.isNotBlank()) builder.line("LOCATION:" + escape(lesson.room))
            val description = listOfNotNull(
                lesson.teacher.takeIf { it.isNotBlank() },
                lesson.note.takeIf { it.isNotBlank() },
            ).joinToString(" — ")
            if (description.isNotBlank()) builder.line("DESCRIPTION:" + escape(description))
            if (lesson.canceled) builder.line("STATUS:CANCELLED")
            builder.line("END:VEVENT")
        }
        builder.line("END:VCALENDAR")
        return builder.toString()
    }

    private fun summary(lesson: Lesson): String = when {
        lesson.canceled -> lesson.subject + " (annulé)"
        lesson.exam -> lesson.subject + " (devoir)"
        else -> lesson.subject
    }

    private fun utc(moment: ZonedDateTime): String =
        moment.withZoneSameInstant(ZoneId.of("UTC")).format(STAMP) + "Z"

    /** Les virgules, points-virgules et retours à la ligne ont un sens dans le format. */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    // Le format exige CRLF : certains agendas refusent tout net un fichier en LF.
    private fun StringBuilder.line(value: String) {
        append(value)
        append("\r\n")
    }
}
