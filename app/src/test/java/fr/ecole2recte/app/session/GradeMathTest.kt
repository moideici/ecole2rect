package fr.ecole2recte.app.session

import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GradeMathTest {
    private fun grade(value: Double?, scale: Double = 20.0, coef: Double = 1.0, counted: Boolean = true) =
        GradeEntry("x", "Maths", 0, "DS", value, scale, coef, null, null, null, null, counted)

    @Test
    fun `la moyenne est ponderee et ramenee sur 20`() {
        // 16/20 coef 2 et 8/10 coef 1 (soit 16/20) -> 16 ; avec 10/20 coef 1 -> (32 + 16 + 10) / 4
        val average = GradeMath.weightedAverage(listOf(grade(16.0, coef = 2.0), grade(8.0, scale = 10.0), grade(10.0)))
        assertEquals(14.5, average!!, 0.0001)
    }

    @Test
    fun `une absence ou une note qui ne compte pas n'entre pas dans la moyenne`() {
        val average = GradeMath.weightedAverage(listOf(grade(12.0), grade(null), grade(0.0, counted = false)))
        assertEquals(12.0, average!!, 0.0001)
    }

    @Test
    fun `sans note il n'y a pas de moyenne`() {
        assertNull(GradeMath.weightedAverage(emptyList()))
        assertNull(GradeMath.weightedAverage(listOf(grade(null))))
    }

    @Test
    fun `la note necessaire pour atteindre un objectif`() {
        // Moyenne actuelle 12 sur un coef 1 ; pour 14 avec une evaluation coef 1 : (14*2 - 12) / 1 = 16
        assertEquals(16.0, GradeMath.requiredGrade(listOf(grade(12.0)), 14.0, 1.0)!!, 0.0001)
        // Avec un coef 2 : (14*3 - 12) / 2 = 15
        assertEquals(15.0, GradeMath.requiredGrade(listOf(grade(12.0)), 14.0, 2.0)!!, 0.0001)
    }

    @Test
    fun `un objectif impossible depasse 20 et un objectif acquis est negatif`() {
        assertEquals(true, GradeMath.requiredGrade(listOf(grade(5.0)), 18.0, 1.0)!! > 20.0)
        assertEquals(true, GradeMath.requiredGrade(listOf(grade(18.0, coef = 5.0)), 10.0, 1.0)!! < 0.0)
    }

    @Test
    fun `un coefficient nul n'a pas de sens`() {
        assertNull(GradeMath.requiredGrade(listOf(grade(12.0)), 14.0, 0.0))
    }

    @Test
    fun `la moyenne avec une note supplementaire`() {
        assertEquals(14.0, GradeMath.averageWith(listOf(grade(12.0)), 16.0, 1.0)!!, 0.0001)
        assertNotNull(GradeMath.averageWith(emptyList(), 10.0, 1.0))
    }
}
