package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.sync.WidgetSnapshot
import org.junit.Assert.*
import org.junit.Test

class WidgetAverageTest {
    @Test fun `le widget garde le bulletin et explique son retard apres serialisation`() {
        val grade = GradeEntry("1", "Maths", 0, "DS", 16.0, 20.0, 1.0, "2026-11-05", null, null, null, true)
        val snapshot = WidgetLogic.snapshot(updatedAt = 0, today = java.time.LocalDate.of(2026, 11, 5), nextClass = null, week = null, homework = emptyList(),
            grades = GradeOverview("15,00", "", emptyList(), averageSource = AverageSource.Recomputed,
                verdict = AverageVerdict(15.0, AverageOrigin.Recomputed, 14.26, listOf(grade))), inbox = emptyList())
        val restored = WidgetSnapshot.fromJson(snapshot.toJson())!!
        assertEquals("Recalculée", restored.averageLabel)
        assertEquals("14,26", restored.officialAverage)
        assertTrue(restored.averageBehind.contains("05/11"))
        val hidden = WidgetPrivacy.apply(restored, PrivacyMode(true))
        assertEquals("", hidden.officialAverage)
        assertEquals("", hidden.averageBehind)
        assertEquals("", hidden.averageLabel)
    }
}
