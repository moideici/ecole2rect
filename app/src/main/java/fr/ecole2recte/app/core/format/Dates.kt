package fr.ecole2recte.app.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatage des dates en français, sans dépendre des données de locale de la plateforme.
 *
 * Les noms sont écrits en dur : un même libellé doit sortir identique dans les tests JVM,
 * sur un téléphone configuré en anglais et dans une capture d'écran de bug.
 */
object Dates {

    private val DAY_NAMES = listOf(
        "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche",
    )
    private val MONTH_NAMES = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )

    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun isoDate(date: LocalDate): String = date.format(ISO_DATE)

    fun dayName(date: LocalDate): String = DAY_NAMES[date.dayOfWeek.value - 1]

    fun monthName(date: LocalDate): String = MONTH_NAMES[date.monthValue - 1]

    /** « samedi 5 septembre 2026 » */
    fun fullDate(date: LocalDate): String =
        "${dayName(date)} ${dayLabel(date)} ${monthName(date)} ${date.year}"

    /** « samedi 5 septembre » — l'année n'apporte rien dans une liste de la semaine. */
    fun dayAndMonth(date: LocalDate): String =
        "${dayName(date)} ${dayLabel(date)} ${monthName(date)}"

    /** « sam. 5 » pour les sélecteurs de jour, où la place manque. */
    fun compactDay(date: LocalDate): String =
        "${dayName(date).take(3)}. ${date.dayOfMonth}"

    /** « 1er » le premier du mois, « 5 » les autres jours. */
    fun dayLabel(date: LocalDate): String = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()

    fun time(value: LocalDateTime): String = time(value.toLocalTime())

    fun time(value: LocalTime): String = "%02d:%02d".format(value.hour, value.minute)

    fun range(start: LocalDateTime, end: LocalDateTime): String = "${time(start)} — ${time(end)}"

    /** « 1 h 30 », « 55 min » : durée d'un cours. */
    fun duration(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60}"
    }

    /**
     * Position d'un cours par rapport à maintenant : « dans 24 min », « en cours »,
     * « terminé ». C'est l'information que l'accueil doit donner en un coup d'œil.
     */
    fun relativeToNow(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime): String = when {
        now.isBefore(start) -> {
            val minutes = Duration.between(now, start).toMinutes()
            when {
                minutes <= 0 -> "maintenant"
                minutes < 60 -> "dans $minutes min"
                minutes < 24 * 60 -> "dans ${duration(minutes)}"
                start.toLocalDate() == now.toLocalDate().plusDays(1) -> "demain"
                else -> dayAndMonth(start.toLocalDate())
            }
        }
        now.isBefore(end) -> "en cours"
        else -> "terminé"
    }

    /** Part écoulée d'un cours, entre 0 et 1 — la barre de progression de l'accueil. */
    fun progress(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime): Float {
        val total = Duration.between(start, end).toMinutes().toDouble()
        if (total <= 0) return 0f
        val done = Duration.between(start, now).toMinutes().toDouble()
        return done.div(total).coerceIn(0.0, 1.0).toFloat()
    }

    /** « aujourd'hui », « demain », « hier », sinon « lundi 7 septembre ». */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "aujourd'hui"
        today.plusDays(1) -> "demain"
        today.minusDays(1) -> "hier"
        else -> dayAndMonth(date)
    }

    /** Libellé d'échéance d'un devoir, avec le retard rendu explicite. */
    fun dueLabel(date: LocalDate, today: LocalDate): String = when {
        date.isBefore(today) -> "en retard · ${dayAndMonth(date)}"
        else -> "pour ${relativeDay(date, today)}"
    }

    /**
     * Fraîcheur de la dernière synchronisation. Une donnée affichée sans sa date
     * de récupération est une donnée dont on ne sait pas si elle est vraie.
     */
    fun freshness(syncedAtMillis: Long?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (syncedAtMillis == null || syncedAtMillis <= 0L) return "jamais synchronisé"
        val minutes = (nowMillis - syncedAtMillis) / 60_000L
        return when {
            minutes < 0 -> "à l'instant"
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${duration(minutes)}"
            else -> {
                val moment = LocalDateTime.ofInstant(Instant.ofEpochMilli(syncedAtMillis), zone)
                "le ${dayAndMonth(moment.toLocalDate())} à ${time(moment)}"
            }
        }
    }

    /** Lundi de la semaine contenant [date] — l'emploi du temps se raisonne par semaine. */
    fun startOfWeek(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
