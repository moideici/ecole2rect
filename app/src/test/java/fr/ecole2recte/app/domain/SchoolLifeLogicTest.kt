package fr.ecole2recte.app.domain

import fr.ecole2recte.app.data.api.Parsers
import org.json.JSONObject
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SchoolLifeLogicTest {
    private fun event(id: String, date: String, justified: Boolean = false, minutes: Int? = 90) =
        SchoolLifeEvent(SchoolLifeKind.Absence, "Absence", date, "Transport", justified, id = id, date = date, duration = "1 h 30", countedMinutes = minutes)

    @Test fun fixtureKeepsSchoolDurationInsteadOfComputingClockDifference() {
        val data = JSONObject(javaClass.getResource("/captures/schoollife_duration.json")!!.readText())
        val parsed = Parsers.schoolLife(data)
        assertEquals("1 h 30", parsed.single().duration)
        assertEquals(90, SchoolLifeLogic.countedMinutes(parsed.single().duration))
        assertNull(SchoolLifeLogic.countedMinutes("2 oct. de 8h à 10h"))
    }
    @Test fun countersOnlySumKnownUnjustifiedAbsenceDurations() {
        val events = listOf(event("1", "2026-10-02"), event("2", "2026-10-03", true), event("3", "2026-10-04", minutes = null))
        assertEquals(90, SchoolLifeLogic.unjustifiedMinutes(events))
        assertEquals(1, SchoolLifeLogic.unknownDurations(events))
    }
    @Test fun groupsAndFiltersByMonthAndKind() {
        val events = listOf(event("1", "2026-09-01"), event("2", "2026-10-01"), event("3", ""))
        assertEquals(listOf("2026-10", "2026-09", "Date inconnue"), SchoolLifeLogic.months(events).keys.toList())
        assertEquals(2, SchoolLifeLogic.filter(events, SchoolLifeKind.Absence, "2026-10").single().id.toInt())
        assertTrue(SchoolLifeLogic.filter(events, SchoolLifeKind.Late, null).isEmpty())
    }
    @Test fun reminderThresholdsAndDeduplication() {
        val today = LocalDate.parse("2026-11-09")
        val events = listOf(event("old", "2026-11-04"), event("new", "2026-11-08"), event("ok", "2026-11-01", true), event("future", "2026-12-01"))
        assertTrue(SchoolLifeLogic.reminders(events, today, null, emptySet()).isEmpty())
        assertEquals(listOf("old"), SchoolLifeLogic.reminders(events, today, 5, emptySet()).map { it.id })
        assertTrue(SchoolLifeLogic.reminders(events, today, 5, setOf("old")).isEmpty())
        assertTrue(SchoolLifeLogic.reminders(events, today, 7, emptySet()).isEmpty())
        assertTrue(SchoolLifeLogic.reminders(events, today, 3, emptySet()).isEmpty())
    }
    @Test fun layoutVariantsCoverPhoneTabletLandscapeAndFontScale() {
        assertEquals(1, SchoolLifeLogic.columns(390f, 800f, false, 1f))
        assertEquals(2, SchoolLifeLogic.columns(700f, 900f, false, 1f))
        assertEquals(3, SchoolLifeLogic.columns(1200f, 900f, true, 1f))
        assertEquals(1, SchoolLifeLogic.columns(700f, 350f, true, 2f))
    }
    @Test fun widgetVariantsAndPrivacy() {
        assertFalse(SchoolLifeLogic.widgetExpanded(80f, 80f))
        assertTrue(SchoolLifeLogic.widgetExpanded(180f, 110f))
        assertFalse(SchoolLifeLogic.widgetExpanded(180f, 110f, 2f))
        assertFalse(SchoolLifeLogic.widgetShowsCaption(60f, 60f, 2f))
        assertTrue(SchoolLifeLogic.widgetShowsCaption(180f, 110f, 2f))
        assertEquals(16, SchoolLifeLogic.widgetNumberSize(60f, 60f, 3f, 1))
        assertTrue(SchoolLifeLogic.widgetNumberSize(60f, 60f, 3f, 4) < 16)
        assertEquals("•••", SchoolLifeLogic.widgetValue(2, true))
        assertEquals("–", SchoolLifeLogic.widgetValue(null, false))
    }
}
