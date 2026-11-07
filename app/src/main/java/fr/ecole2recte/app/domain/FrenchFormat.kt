package fr.ecole2recte.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Tout le formatage des dates, des heures et des nombres de l'application, en français quelle que
 * soit la langue du téléphone. Aucun autre fichier ne doit s'appuyer sur la langue de l'appareil
 * (`Locale.getDefault()`, `"%02d".format(...)`, `ofPattern` sans langue) : un téléphone en arabe
 * ou en anglais afficherait sinon « Thursday » ou des chiffres arabo-indiens.
 */
object FrenchFormat {
    val locale: Locale = Locale.FRENCH

    fun formatter(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale)

    private val hourMinute = formatter("HH:mm")
    private val weekdayDayMonthYear = formatter("EEEE d MMMM yyyy")
    private val weekdayDayMonth = formatter("EEEE d MMMM")
    private val dayMonth = formatter("d MMMM")
    private val dayMonthShort = formatter("d MMM")
    private val dayMonthYearShort = formatter("d MMM yyyy")
    private val weekdayDayMonthAtTime = formatter("EEEE d MMMM 'à' HH:mm")

    fun capitalized(text: String): String = text.replaceFirstChar { it.titlecase(locale) }

    /** « 18:05 » */
    fun time(moment: java.time.temporal.TemporalAccessor): String = hourMinute.format(moment)

    /** « jeudi 5 novembre 2026 » */
    fun weekdayDayMonthYear(date: LocalDate): String = weekdayDayMonthYear.format(date)

    /** « jeudi 5 novembre » */
    fun weekdayDayMonth(date: LocalDate): String = weekdayDayMonth.format(date)

    /** « 5 novembre » */
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    /** « 5 nov. » */
    fun dayMonthShort(date: LocalDate): String = dayMonthShort.format(date)

    /** « 5 nov. 2026 » */
    fun dayMonthYearShort(date: LocalDate): String = dayMonthYearShort.format(date)

    /** « jeudi 5 novembre à 18:05 » */
    fun weekdayDayMonthAtTime(moment: java.time.temporal.TemporalAccessor): String = weekdayDayMonthAtTime.format(moment)

    /** « Jeu » : jour abrégé, sans point, initiale en majuscule. */
    fun weekdayShort(date: LocalDate): String =
        capitalized(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix("."))

    /** « jeudi » */
    fun weekdayFull(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    /** « 12,5 » : virgule décimale française. [digits] décimales (une par défaut). */
    fun decimal(value: Double, digits: Int = 1): String = String.format(locale, "%.${digits}f", value)

    /** « 07 » : deux chiffres, toujours ASCII. */
    fun twoDigits(value: Int): String = String.format(locale, "%02d", value)

    /** « 2 h 05 » ou « 45 min », pour une durée en minutes. */
    fun hoursMinutes(minutes: Int): String =
        if (minutes < 60) "$minutes min" else "${minutes / 60} h ${twoDigits(minutes % 60)}"

    fun isoTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        formatter("yyyy-MM-dd HH:mm:ss").format(Instant.ofEpochMilli(at).atZone(zone))
}
