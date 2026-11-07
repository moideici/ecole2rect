package fr.ecole2recte.app.domain

import java.time.Instant
import java.time.ZoneId

/**
 * L'âge des données affiché dans le bandeau hors ligne : « Données du 5 nov. à 18 h (il y a 3 h) ».
 * Fonctions pures, horloge et fuseau passés en paramètres pour être testées.
 */
object FreshnessLabel {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** « il y a 12 min », « il y a 3 h », « il y a 2 jours » ; « à l'instant » sous la minute. */
    fun relative(ageMs: Long): String {
        val age = ageMs.coerceAtLeast(0)
        return when {
            age < MINUTE -> "à l'instant"
            age < HOUR -> "il y a ${age / MINUTE} min"
            age < DAY -> "il y a ${age / HOUR} h"
            else -> (age / DAY).let { days -> "il y a $days jour" + if (days > 1) "s" else "" }
        }
    }

    /** « 18 h » ou « 18 h 30 » (les minutes ne sont dites que si elles comptent). */
    fun clock(hour: Int, minute: Int): String =
        if (minute == 0) "$hour h" else "$hour h ${FrenchFormat.twoDigits(minute)}"

    /** « d'aujourd'hui à 18 h », « d'hier à 18 h » ou « du 5 nov. à 18 h » (précédé de « Données »). */
    fun absolute(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = moment.toLocalDate()
        val time = clock(moment.hour, moment.minute)
        return when (day) {
            today -> "d'aujourd'hui à $time"
            today.minusDays(1) -> "d'hier à $time"
            else -> "du ${FrenchFormat.dayMonthShort(day)} à $time"
        }
    }

    /**
     * La phrase complète du bandeau. Exemples : « Données d'aujourd'hui à 15 h (il y a 3 h) »,
     * « Données du 5 nov. à 18 h (il y a 2 jours) », « Données enregistrées (date inconnue) ».
     */
    fun describe(lastSyncMillis: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (lastSyncMillis == null || lastSyncMillis <= 0) return "Données enregistrées (date inconnue)"
        return "Données ${absolute(lastSyncMillis, now, zone)} (${relative(now - lastSyncMillis)})"
    }
}
