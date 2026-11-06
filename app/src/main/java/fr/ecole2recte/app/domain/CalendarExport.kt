package fr.ecole2recte.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Les champs d'un événement à proposer au calendrier du téléphone. L'application n'écrit jamais dans
 * le calendrier elle-même (aucune permission) : elle ouvre l'écran « nouvel événement » de
 * l'application Calendrier, déjà rempli, et c'est l'utilisateur qui l'enregistre.
 */
data class CalendarEventFields(
    val title: String,
    /** Millisecondes depuis 1970. Pour un événement sur la journée : minuit UTC, comme Android l'attend. */
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String,
    val description: String,
)

/** Fonctions pures : un cours ou un contrôle devient les champs d'un événement. */
object CalendarExport {
    private const val SIGNATURE = "Ajouté depuis ecole2recte"
    private const val MAX_DESCRIPTION = 400

    /**
     * Un cours : titre = matière, lieu = salle, description = professeur (+ note, + mention d'annulation).
     * `null` quand le cours n'a pas d'horaire exploitable.
     */
    fun lesson(entry: ScheduleEntry, zone: ZoneId): CalendarEventFields? {
        val day = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return null
        val start = parseTime(entry.start) ?: return null
        val end = parseTime(entry.end) ?: return null
        val from = LocalDateTime.of(day, start)
        var to = LocalDateTime.of(day, end)
        if (!to.isAfter(from)) to = from.plusMinutes(55)
        val subject = entry.subject
        val description = listOfNotNull(
            subject.teacher.takeIf { it.isNotBlank() }?.let { "Professeur : $it" },
            "Ce cours est annulé dans ÉcoleDirecte.".takeIf { entry.canceled },
            "Une évaluation est prévue.".takeIf { entry.exam },
            entry.note.takeIf { it.isNotBlank() },
            SIGNATURE,
        ).joinToString("\n")
        return CalendarEventFields(
            title = subject.name.ifBlank { "Cours" } + if (entry.canceled) " (annulé)" else "",
            beginMillis = from.atZone(zone).toInstant().toEpochMilli(),
            endMillis = to.atZone(zone).toInstant().toEpochMilli(),
            allDay = false,
            location = subject.room.trim(),
            description = description,
        )
    }

    /**
     * Un contrôle (ou tout devoir) pour une date : un événement sur la journée, intitulé « Contrôle de Maths »
     * ou « Devoir de Maths », avec le début de l'énoncé en description.
     */
    fun homework(task: Homework): CalendarEventFields? {
        val day = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return null
        val begin = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val statement = task.content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        val description = listOf(
            statement.take(MAX_DESCRIPTION).let { if (statement.length > MAX_DESCRIPTION) it.trimEnd() + "…" else it },
            SIGNATURE,
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        val subject = task.subject.ifBlank { "classe" }
        return CalendarEventFields(
            title = (if (task.exam) "Contrôle de " else "Devoir de ") + subject,
            beginMillis = begin,
            endMillis = begin + 24 * 60 * 60 * 1000L,
            allDay = true,
            location = "",
            description = description,
        )
    }

    private fun parseTime(text: String): LocalTime? = runCatching { LocalTime.parse(text.take(5)) }.getOrNull()
}
