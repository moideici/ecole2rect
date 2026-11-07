package fr.ecole2recte.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FrenchFormatTest {
    private val previous = Locale.getDefault()

    @Before
    fun phoneInAnotherLanguage() {
        // Un téléphone réglé dans une langue aux chiffres et aux mois différents.
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    }

    @After
    fun restore() = Locale.setDefault(previous)

    private val thursday = LocalDate.of(2026, 11, 5)

    @Test
    fun `les dates connues sont en francais quelle que soit la langue du telephone`() {
        assertEquals("jeudi 5 novembre 2026", FrenchFormat.weekdayDayMonthYear(thursday))
        assertEquals("jeudi 5 novembre", FrenchFormat.weekdayDayMonth(thursday))
        assertEquals("5 novembre", FrenchFormat.dayMonth(thursday))
        assertEquals("5 nov.", FrenchFormat.dayMonthShort(thursday))
        assertEquals("5 nov. 2026", FrenchFormat.dayMonthYearShort(thursday))
        assertEquals("jeudi", FrenchFormat.weekdayFull(thursday))
        assertEquals("Jeu", FrenchFormat.weekdayShort(thursday))
    }

    @Test
    fun `chaque mois et chaque jour ont leur nom francais`() {
        val months = (1..12).map { FrenchFormat.dayMonth(LocalDate.of(2026, it, 1)) }
        assertEquals(
            listOf("1 janvier", "1 février", "1 mars", "1 avril", "1 mai", "1 juin", "1 juillet", "1 août", "1 septembre", "1 octobre", "1 novembre", "1 décembre"),
            months,
        )
        val days = (2..8).map { FrenchFormat.weekdayFull(LocalDate.of(2026, 11, it)) } // lundi 2 novembre 2026
        assertEquals(listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"), days)
    }

    @Test
    fun `l'heure et la date avec l'heure restent en chiffres ASCII`() {
        val moment = LocalDateTime.of(2026, 11, 5, 8, 5)
        assertEquals("08:05", FrenchFormat.time(moment))
        assertEquals("jeudi 5 novembre à 08:05", FrenchFormat.weekdayDayMonthAtTime(moment))
        assertEquals("Jeudi 5 novembre", FrenchFormat.capitalized(FrenchFormat.weekdayDayMonth(thursday)))
    }

    @Test
    fun `les nombres ont la virgule francaise et deux chiffres`() {
        assertEquals("14,5", FrenchFormat.decimal(14.5))
        assertEquals("12,0", FrenchFormat.decimal(12.0))
        assertEquals("0,25", FrenchFormat.decimal(0.25, 2))
        assertEquals("07", FrenchFormat.twoDigits(7))
        assertEquals("2 h 05", FrenchFormat.hoursMinutes(125))
        assertEquals("45 min", FrenchFormat.hoursMinutes(45))
    }

    @Test
    fun `l'horodatage du journal de plantage est stable`() {
        val at = java.time.ZonedDateTime.of(2026, 11, 5, 18, 30, 15, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("2026-11-05 18:30:15", FrenchFormat.isoTime(at, ZoneId.of("UTC")))
    }
}
