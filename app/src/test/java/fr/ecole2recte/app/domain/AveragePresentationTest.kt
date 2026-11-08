package fr.ecole2recte.app.domain

import org.junit.Assert.*
import org.junit.Test

class AveragePresentationTest {
    @Test fun `deux decimales par defaut et une sur demande sans toucher les notes`() {
        assertEquals("14,26", AveragePresentation.number("14,26", 2))
        assertEquals("14,3", AveragePresentation.number("14,26", 1))
        assertEquals(MASKED, AveragePresentation.number(MASKED, 2))
        assertEquals("–", AveragePresentation.number("–", 2))
    }
    @Test fun `tendance seulement entre deux officielles ou deux calculees`() {
        assertEquals("", AveragePresentation.trend(15.0, AverageSource.FromGrades, 14.0, AverageSource.Official, 2))
        assertEquals("↗ +1,00", AveragePresentation.trend(15.0, AverageSource.Official, 14.0, AverageSource.Official, 2))
        assertEquals("≈ ↗ +1,00", AveragePresentation.trend(15.0, AverageSource.Recomputed, 14.0, AverageSource.FromSubjects, 2))
        assertEquals("", AveragePresentation.trend(14.02, AverageSource.Official, 14.0, AverageSource.Official, 2))
    }
    @Test fun `la courbe est limitee a la periode choisie`() {
        fun grade(id: String, value: Double, period: String) = GradeEntry(id, "Maths", 0, "DS", value, 20.0, 1.0, "2026-10-$id", null, null, null, true, period)
        val notes = listOf(grade("01", 2.0, "T1"), grade("02", 12.0, "T2"), grade("03", 16.0, "T2"))
        assertEquals(14.0, GradeCurves.subjectSeries(notes, "Maths", "T2").last().runningAverage, .0001)
        assertEquals(10.0, GradeCurves.subjectSeries(notes, "Maths").last().runningAverage, .0001)
    }
    @Test fun `le mode montrer efface aussi les preuves du recalcul`() {
        val verdict = AverageVerdict(15.0, AverageOrigin.Recomputed, 14.26)
        assertNull(PrivacyMode(true).mask(GradeOverview("15,00", "", emptyList(), verdict = verdict)).verdict)
    }
    @Test fun `une decimale est arrondie depuis le nombre brut sans double arrondi`() {
        val line = SubjectAverage(Subject("Maths", "", "", 0), "14,25", classAverage = "14,25", minimum = "14,25", maximum = "14,25",
            classAverageValue = 14.249, minimumValue = 14.249, maximumValue = 14.249)
        val overview = GradeOverview("14,25", "", listOf(line), verdict = AverageVerdict(14.249, AverageOrigin.Computed, null),
            classAverage = "14,25", classAverageValue = 14.249, periods = listOf(GradePeriod("T1", "T1", true, "14,25", 14.249)))
        val shown = AveragePresentation.format(overview, 1)
        assertEquals("14,2", shown.average)
        assertEquals("14,2", shown.classAverage)
        assertEquals("14,2", shown.bySubject.single().minimum)
        assertEquals("14,2", shown.bySubject.single().maximum)
        assertEquals("14,2", shown.bySubject.single().classAverage)
        assertEquals("14,2", shown.periods.single().average)
        val hidden = PrivacyMode(true).mask(overview)
        assertNull(hidden.classAverageValue)
        assertNull(hidden.bySubject.single().minimumValue)
        assertNull(hidden.periods.single().value)
    }
    @Test fun `la description accessible des courbes respecte la precision`() {
        assertEquals("Moyenne : T1 14,26.", GradeCurves.describe("Moyenne", listOf("T1"), listOf(14.26), 2))
    }
}
