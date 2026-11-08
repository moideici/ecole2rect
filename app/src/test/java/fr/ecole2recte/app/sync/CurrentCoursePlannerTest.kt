package fr.ecole2recte.app.sync

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CurrentCoursePlannerTest {
    private fun slot(start: String = "08:00", end: String = "09:00", canceled: Boolean = false) =
        WidgetSlot("2026-11-09", start, end, "Mathématiques", "B12", "", canceled, 0)
    private fun at(time: String) = LocalDateTime.parse("2026-11-09T$time")

    @Test fun disabledByDefault() {
        assertNull(CurrentCoursePlanner.plan(listOf(slot()), at("08:30"), enabled = false).current)
    }
    @Test fun canceledAndMalformedLessonsAreIgnored() {
        val plan = CurrentCoursePlanner.plan(listOf(slot(canceled = true), slot("oops")), at("08:30"))
        assertNull(plan.current)
        assertNull(plan.nextBoundary)
    }
    @Test fun progressAndNextCourse() {
        val plan = CurrentCoursePlanner.plan(listOf(slot(), slot("09:00", "10:00")), at("08:30"))
        assertEquals(50, plan.progress)
        assertEquals("09:00", plan.next?.start)
        assertEquals(at("09:00"), plan.nextBoundary)
        assertEquals(at("08:31"), plan.nextRefresh(at("08:30")))
    }
    @Test fun consecutiveCoursesUseHalfOpenIntervals() {
        val plan = CurrentCoursePlanner.plan(listOf(slot(), slot("09:00", "10:00")), at("09:00"))
        assertEquals("09:00", plan.current?.start)
        assertEquals(0, plan.progress)
    }
    @Test fun pauseHasNoNotificationButSchedulesRestart() {
        val plan = CurrentCoursePlanner.plan(listOf(slot(), slot("10:00", "11:00")), at("09:30"))
        assertNull(plan.current)
        assertEquals(at("10:00"), plan.nextBoundary)
        assertEquals(at("10:00"), plan.nextRefresh(at("09:30")))
    }
    @Test fun endOfDayAndWeekendStop() {
        assertNull(CurrentCoursePlanner.plan(listOf(slot()), at("09:00")).nextBoundary)
        assertNull(CurrentCoursePlanner.plan(listOf(slot()), LocalDateTime.parse("2026-11-15T10:00")).current)
    }
    @Test fun changedScheduleReplacesOldBoundary() {
        assertEquals(at("09:30"), CurrentCoursePlanner.plan(listOf(slot("09:30", "10:30")), at("08:30")).nextBoundary)
    }
    @Test fun privacyRemovesSubjectAndRoom() {
        val plan = CurrentCoursePlanner.plan(listOf(slot()), at("08:30"))
        val text = CurrentCoursePlanner.text(plan, hidden = true)
        assertFalse(text.contains("Mathématiques"))
        assertFalse(text.contains("B12"))
        assertTrue(text.contains("•••"))
    }
}
