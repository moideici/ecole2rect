package fr.ecole2recte.app.domain

import org.junit.Assert.*
import org.junit.Test

class AverageEngineTest {
    private fun grade(value: Double) = GradeEntry("1", "Maths", 0, "DS", value, 20.0, 2.0, "2026-10-01", null, null, null, true)

    @Test fun `un coefficient nul exclut la matiere mais un inconnu vaut un`() {
        assertEquals(12.0, AverageEngine.weightedSubjects(listOf(
            AverageEngine.SubjectWeight("a", 20.0, 0.0), AverageEngine.SubjectWeight("b", 12.0, null),
        ))!!, 0.0001)
    }

    @Test fun `toutes les matieres exclues donnent le relais aux notes`() {
        val result = AverageEngine.general(null, listOf(AverageEngine.SubjectWeight("a", 20.0, 0.0)), listOf(grade(14.0)))
        assertEquals(14.0, result.value!!, 0.0001)
        assertEquals(AverageSource.FromGrades, result.source)
    }

    @Test fun `la projection part du chiffre affiche sur les baremes dix vingt quarante`() {
        assertEquals("15,0", GradeSimulation.defaultTarget(listOf(grade(8.0)), 14.0))
        for (scale in listOf(10.0, 20.0, 40.0)) {
            val result = GradeSimulation.projection(listOf(grade(8.0)), (scale * .8).toString(), "1", scale.toString(), 14.0) as GradeSimulation.Projection.Value
            assertEquals(14.0, result.current!!, 0.0001)
            assertEquals(44.0 / 3, result.projected, 0.0001)
            val need = GradeSimulation.need(listOf(grade(8.0)), "14", "1", scale.toString(), 14.0) as GradeSimulation.Need.Needed
            assertEquals(scale * .7, need.grade, 0.0001)
        }
        assertTrue(GradeSimulation.projection(emptyList(), "11", "1", "10") is GradeSimulation.Projection.Invalid)
        assertTrue(GradeSimulation.projection(emptyList(), "1", "1", "0") is GradeSimulation.Projection.Invalid)
    }

    @Test fun `effet sur la generale par remplacement ou estimation officielle`() {
        val subjects = listOf(AverageEngine.SubjectWeight("a", 10.0, 3.0), AverageEngine.SubjectWeight("b", 14.0, 1.0))
        val calculated = AverageEngine.projectGeneral(subjects, "a", 12.0, 11.0, false)!!
        assertEquals(12.5, calculated.value, 0.0001)
        assertFalse(calculated.estimated)
        val official = AverageEngine.projectGeneral(subjects, "a", 12.0, 13.0, true)!!
        assertEquals(14.5, official.value, 0.0001)
        assertTrue(official.estimated)
        val fallback = AverageEngine.projectGeneral(subjects, "a", 10.0, 12.0, false)!!
        assertEquals(12.0, fallback.value, .0001)
        assertTrue(fallback.estimated)
    }
}
