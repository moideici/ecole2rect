package fr.ecole2recte.app.export

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Ce que l'utilisateur demande à l'export iCalendar.
 *
 * [from] et [until] sont les bornes (incluses) de la période ; [weeks] les semaines à exporter
 * (1 et/ou 2) quand l'emploi du temps alterne ; [ownerKey] distingue les profils dans les UID.
 */
data class IcsOptions(
    val from: LocalDate,
    val until: LocalDate,
    val ownerKey: String,
    val weeks: Set<Int> = setOf(1, 2),
    val calendarName: String = "Emploi du temps",
    val stamp: Instant = Instant.now(),
    val zone: ZoneId = ZoneId.of("Europe/Paris"),
)

/**
 * Écrit un fichier iCalendar (RFC 5545) : lignes terminées par CRLF et pliées à 75 octets, texte échappé,
 * heures locales avec `TZID=Europe/Paris` (et son VTIMEZONE), un VEVENT récurrent par créneau
 * (`FREQ=WEEKLY`, `INTERVAL=2` pour une semaine sur deux), `EXDATE` pour les occurrences annulées, UID
 * stables : réimporter le fichier met à jour les événements au lieu de les dupliquer.
 */
object IcsWriter {
    private const val CRLF = "\r\n"
    private const val MAX_OCTETS = 75
    private val localStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun write(schedule: TypicalSchedule, options: IcsOptions): String {
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//ecole2recte//Emploi du temps//FR"
        lines += "CALSCALE:GREGORIAN"
        lines += "METHOD:PUBLISH"
        lines += "X-WR-CALNAME:${escape(options.calendarName)}"
        lines += "X-WR-TIMEZONE:${options.zone.id}"
        if (options.zone.id == "Europe/Paris") lines += parisTimezone()
        for (slot in schedule.slots) lines += event(schedule, slot, options)
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "") { fold(it) + CRLF }
    }

    /** Le nombre de VEVENT que [write] produira (pour dire « 24 événements » avant d'exporter). */
    fun eventCount(schedule: TypicalSchedule, options: IcsOptions): Int =
        schedule.slots.count { start(schedule, it, options) != null }

    /** Première date et pas (en semaines) d'un créneau ; `null` s'il n'y a rien à exporter dans la période. */
    internal fun start(schedule: TypicalSchedule, slot: TypicalSlot, options: IcsOptions): Pair<LocalDate, Int>? {
        val selected = if (schedule.alternating) options.weeks.intersect(setOf(1, 2)) else setOf(1)
        if (selected.isEmpty()) return null
        val (anchor, step) = when {
            !schedule.alternating -> (slot.dates[1] ?: return null) to 1
            else -> when (slot.cycle) {
                WeekSelector.First -> (if (1 in selected) slot.dates[1] else null)?.let { it to 2 } ?: return null
                WeekSelector.Second -> (if (2 in selected) slot.dates[2] else null)?.let { it to 2 } ?: return null
                WeekSelector.Every ->
                    if (selected.size == 2) (slot.dates.values.minOrNull() ?: return null) to 1
                    else (slot.dates[selected.single()] ?: return null) to 2
            }
        }
        // Avance par pas de [step] semaines jusqu'à la première occurrence qui n'est pas avant [from].
        var first = anchor
        if (first.isBefore(options.from)) {
            val stepDays = step * 7L
            val missing = java.time.temporal.ChronoUnit.DAYS.between(first, options.from)
            first = first.plusDays(((missing + stepDays - 1) / stepDays) * stepDays)
        }
        return if (first.isAfter(options.until)) null else first to step
    }

    private fun event(schedule: TypicalSchedule, slot: TypicalSlot, options: IcsOptions): List<String> {
        val (first, step) = start(schedule, slot, options) ?: return emptyList()
        val zone = options.zone
        val startTime = LocalTime.parse(slot.start)
        val endTime = LocalTime.parse(slot.end)
        val until = options.until.atTime(23, 59, 59).atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
        val label = when {
            !schedule.alternating -> null
            slot.cycle == WeekSelector.First -> "Semaine 1"
            slot.cycle == WeekSelector.Second -> "Semaine 2"
            else -> "Toutes les semaines"
        }
        val description = listOfNotNull(
            slot.teacher.takeIf { it.isNotBlank() }?.let { "Professeur : $it" },
            label,
            "Permanence".takeIf { slot.permanence },
        ).joinToString("\n")
        val exceptions = slot.canceledOn
            .filter { !it.isBefore(first) && !it.isAfter(options.until) && occursOn(first, step, it) }
            .sorted()
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:${uid(schedule, slot, options)}")
            add("DTSTAMP:${utcStamp.format(options.stamp.atZone(ZoneOffset.UTC))}")
            add("DTSTART;TZID=${zone.id}:${localStamp.format(first.atTime(startTime))}")
            add("DTEND;TZID=${zone.id}:${localStamp.format(first.atTime(endTime))}")
            add("RRULE:FREQ=WEEKLY;INTERVAL=$step;UNTIL=${utcStamp.format(until)}")
            exceptions.forEach { add("EXDATE;TZID=${zone.id}:${localStamp.format(it.atTime(startTime))}") }
            add("SUMMARY:${escape(slot.subject.ifBlank { "Cours" })}")
            if (slot.room.isNotBlank()) add("LOCATION:${escape(slot.room)}")
            if (description.isNotEmpty()) add("DESCRIPTION:${escape(description)}")
            add("TRANSP:OPAQUE")
            add("END:VEVENT")
        }
    }

    /** Vrai si [date] tombe sur une occurrence de la série qui commence à [first] tous les [step] semaines. */
    private fun occursOn(first: LocalDate, step: Int, date: LocalDate): Boolean {
        val days = java.time.temporal.ChronoUnit.DAYS.between(first, date)
        return days >= 0 && days % (step * 7L) == 0L
    }

    /** Identifiant stable : le même créneau du même profil garde le même UID d'un export à l'autre. */
    internal fun uid(schedule: TypicalSchedule, slot: TypicalSlot, options: IcsOptions): String {
        val base = "ecole2recte|${options.ownerKey}|${slot.signature}|${if (schedule.alternating) slot.cycle.name else "-"}"
        val digest = MessageDigest.getInstance("SHA-256").digest(base.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) } + "@ecole2recte"
    }

    /** Échappement d'une valeur TEXT : `\`, `;`, `,` et les sauts de ligne. */
    internal fun escape(text: String): String = buildString {
        for (char in text) {
            when (char) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    /**
     * Plie une ligne à 75 octets UTF-8 : les lignes suivantes commencent par une espace (qui compte dans
     * les 75). Un caractère n'est jamais coupé en deux.
     */
    internal fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return line
        val out = StringBuilder()
        var used = 0
        var limit = MAX_OCTETS
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val chunk = String(Character.toChars(codePoint))
            val size = chunk.toByteArray(Charsets.UTF_8).size
            if (used + size > limit) {
                out.append(CRLF).append(' ')
                used = 1
                limit = MAX_OCTETS
            }
            out.append(chunk)
            used += size
            index += chunk.length
        }
        return out.toString()
    }

    private fun parisTimezone(): List<String> = listOf(
        "BEGIN:VTIMEZONE",
        "TZID:Europe/Paris",
        "BEGIN:DAYLIGHT",
        "TZOFFSETFROM:+0100",
        "TZOFFSETTO:+0200",
        "TZNAME:CEST",
        "DTSTART:19700329T020000",
        "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
        "END:DAYLIGHT",
        "BEGIN:STANDARD",
        "TZOFFSETFROM:+0200",
        "TZOFFSETTO:+0100",
        "TZNAME:CET",
        "DTSTART:19701025T030000",
        "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU",
        "END:STANDARD",
        "END:VTIMEZONE",
    )
}
