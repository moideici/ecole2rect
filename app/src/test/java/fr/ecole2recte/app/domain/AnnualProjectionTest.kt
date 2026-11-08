package fr.ecole2recte.app.domain

import org.junit.Assert.*
import org.junit.Test

class AnnualProjectionTest {
    private fun period(code: String, value: Double?, coefficient: Double? = null) = GradePeriod(code, code, code == "T2", value = value, coefficient = coefficient)

    @Test fun `annuelle ponderee quand tous les coefficients sont connus`() {
        val result = AnnualProjection.annual(listOf(period("T1", 10.0, 1.0), period("T2", 16.0, 2.0)))!!
        assertEquals(14.0, result.value, .00001)
        assertTrue(result.weighted)
    }

    @Test fun `coefficients incomplets donnent une moyenne a plat explicite`() {
        val result = AnnualProjection.annual(listOf(period("T1", 10.0, 3.0), period("T2", 16.0)))!!
        assertEquals(13.0, result.value, .00001)
        assertFalse(result.weighted)
        assertEquals(2, result.periodCount)
    }

    @Test fun `periodes absentes exclues et annee jamais comptee deux fois`() {
        val result = AnnualProjection.annual(listOf(period("T1", 10.0, 0.0), period("T2", 16.0, 2.0), period("T3", null), period("A", 18.0).copy(annual = true)))!!
        assertEquals(16.0, result.value, .00001)
        assertEquals(1, result.periodCount)
        assertNull(AnnualProjection.annual(listOf(period("T1", null))))
    }

    private val grade = GradeEntry("g", "Maths", 0, "DS", 10.0, 20.0, 2.0, null, null, null, null, true, "T2")
    private val overview = GradeOverview("12,00", "", listOf(SubjectAverage(Subject("Maths", "", "", 0), "12,00", grades = listOf(grade), id = "m")),
        periodCode = "T2", periods = listOf(period("T1", 10.0), period("T2", 12.0)), averageSource = AverageSource.Official)

    @Test fun `meilleur pire probable prolongent la moyenne affichee et non les seules notes`() {
        val result = AnnualProjection.project(overview, 2)!!
        assertEquals(16.0, result.best.period!!, .00001)
        assertEquals(6.0, result.worst.period!!, .00001)
        assertEquals(12.0, result.probable.period!!, .00001)
        assertEquals(13.0, result.best.annual!!.value, .00001)
        assertEquals(8.0, result.worst.annual!!.value, .00001)
        assertEquals(11.0, result.probable.annual!!.value, .00001)
    }

    @Test fun `zero evaluation conserve la moyenne et les nombres invalides sont refuses`() {
        assertEquals(12.0, AnnualProjection.project(overview, 0)!!.best.period!!, .00001)
        assertNull(AnnualProjection.project(overview, -1))
        assertNull(AnnualProjection.project(overview, 1001))
        assertNull(AnnualProjection.project(overview.copy(average = "–", bySubject = emptyList()), 3))
    }

    @Test fun `la disposition avec grande police empile les champs`() {
        assertEquals(AnalysisLayout.Stacked, AnalysisLayout.choose(1200, 900, false, 2f))
        assertEquals(AnalysisLayout.Columns, AnalysisLayout.choose(840, 900, true))
    }
}
