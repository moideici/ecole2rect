package fr.ecole2recte.app.session

import fr.ecole2recte.app.domain.AverageMethod
import fr.ecole2recte.app.domain.GradeCurves
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeMath
import fr.ecole2recte.app.domain.GradeSimulation
import fr.ecole2recte.app.domain.GradeSimulation.Need
import fr.ecole2recte.app.domain.GradeSimulation.Projection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GradeSimulationTest {
    private fun grade(
        value: Double?,
        coef: Double = 1.0,
        scale: Double = 20.0,
        subject: String = "Maths",
        date: String? = "2026-10-01",
        classAverage: Double? = null,
        counted: Boolean = true,
        label: String = "DS",
    ) = GradeEntry("$subject-$date-$value", subject, 0, label, value, scale, coef, date, classAverage, null, null, counted)

    @Test
    fun `la note necessaire est arrondie au dixieme superieur`() {
        // Moyenne 12 (coef 1) ; objectif 13,1 avec une evaluation coef 1 : 13,1 x 2 - 12 = 14,2
        val need = GradeSimulation.need(listOf(grade(12.0)), "13,1", "1") as Need.Needed
        assertEquals(14.2, need.grade, 0.0001)
        // 14,01 reste a 14,1 : jamais promettre trop peu.
        val tight = GradeSimulation.need(listOf(grade(12.0)), "13,005", "1") as Need.Needed
        assertEquals(14.1, tight.grade, 0.0001)
    }

    @Test
    fun `un objectif hors d'atteinte est dit tel quel`() {
        val need = GradeSimulation.need(listOf(grade(5.0)), "18", "1")
        assertTrue(need is Need.Unreachable)
        assertTrue(GradeSimulation.message(need).startsWith("Hors d'atteinte"))
    }

    @Test
    fun `un objectif deja acquis est dit tel quel`() {
        val need = GradeSimulation.need(listOf(grade(18.0, coef = 5.0)), "10", "1")
        assertEquals(Need.Achieved, need)
        assertTrue(GradeSimulation.message(need).contains("déjà acquis"))
    }

    @Test
    fun `un vingt exact reste atteignable malgre l'arrondi`() {
        // Moyenne 10 ; objectif 15 avec coef 1 : il faut 20 pile.
        val need = GradeSimulation.need(listOf(grade(10.0)), "15", "1") as Need.Needed
        assertEquals(20.0, need.grade, 0.0001)
    }

    @Test
    fun `les saisies invalides donnent un message, jamais un plantage`() {
        val grades = listOf(grade(12.0))
        assertTrue(GradeSimulation.need(grades, "", "1") is Need.Invalid)
        assertTrue(GradeSimulation.need(grades, "abc", "1") is Need.Invalid)
        assertTrue(GradeSimulation.need(grades, "25", "1") is Need.Invalid)
        assertTrue(GradeSimulation.need(grades, "-1", "1") is Need.Invalid)
        assertTrue(GradeSimulation.need(grades, "14", "0") is Need.Invalid)
        assertTrue(GradeSimulation.need(grades, "14", "") is Need.Invalid)
        assertTrue(GradeSimulation.projection(grades, "21", "1") is Projection.Invalid)
        assertTrue(GradeSimulation.projection(grades, "12", "-2") is Projection.Invalid)
    }

    @Test
    fun `la virgule et le point sont acceptes`() {
        assertEquals(14.5, GradeSimulation.parse("14,5")!!, 0.0)
        assertEquals(14.5, GradeSimulation.parse(" 14.5 ")!!, 0.0)
        assertNull(GradeSimulation.parse("1,2,3"))
    }

    @Test
    fun `sans note, il faut exactement l'objectif`() {
        val need = GradeSimulation.need(emptyList(), "14", "2") as Need.Needed
        assertEquals(14.0, need.grade, 0.0001)
    }

    @Test
    fun `la moyenne avec une note de plus`() {
        // 12 coef 1 puis 16 coef 1 : 14
        val projection = GradeSimulation.projection(listOf(grade(12.0)), "16", "1") as Projection.Value
        assertEquals(12.0, projection.current!!, 0.0001)
        assertEquals(14.0, projection.projected, 0.0001)
        assertEquals("Ta moyenne passerait de 12,0 à 14,0 / 20.", GradeSimulation.message(projection))
        assertEquals("Ta moyenne serait de 16,0 / 20.", GradeSimulation.message(GradeSimulation.projection(emptyList(), "16", "1")))
    }

    @Test
    fun `l'objectif par defaut est un point au-dessus de la moyenne, au plus 20`() {
        assertEquals("13,0", GradeSimulation.defaultTarget(listOf(grade(12.0))))
        assertEquals("20,0", GradeSimulation.defaultTarget(listOf(grade(19.5))))
        assertEquals("", GradeSimulation.defaultTarget(emptyList()))
    }

    // --- Ecart a la classe ---

    @Test
    fun `l'ecart a la classe se lit avec un signe et une fleche`() {
        assertEquals(1.5, GradeMath.classGap(grade(14.0, classAverage = 12.5))!!, 0.0001)
        assertEquals("▲ +1,5 vs classe", GradeMath.classGapLabel(1.5))
        assertEquals("▼ −2,0 vs classe", GradeMath.classGapLabel(-2.0))
        assertEquals("= classe", GradeMath.classGapLabel(0.04))
    }

    @Test
    fun `sans note ou sans moyenne de classe il n'y a pas d'ecart`() {
        assertNull(GradeMath.classGap(grade(14.0, classAverage = null)))
        assertNull(GradeMath.classGap(grade(null, classAverage = 12.0)))
    }

    // --- Courbes ---

    @Test
    fun `la courbe d'une matiere est dans l'ordre des dates avec la moyenne cumulee`() {
        val all = listOf(
            grade(16.0, date = "2026-10-20"),
            grade(10.0, date = "2026-10-01"),
            grade(20.0, date = "2026-11-02", subject = "Anglais"),
            grade(0.0, date = "2026-10-25", counted = false),
            grade(null, date = "2026-10-26"),
            grade(8.0, scale = 10.0, date = "2026-11-01"),
        )
        val series = GradeCurves.subjectSeries(all, "Maths")
        assertEquals(listOf("2026-10-01", "2026-10-20", "2026-11-01"), series.map { it.date })
        assertEquals(listOf(10.0, 16.0, 16.0), series.map { it.note })
        assertEquals(listOf(10.0, 13.0, 14.0), series.map { it.runningAverage })
    }

    @Test
    fun `les bornes de la courbe restent dans 0 a 20 avec une hauteur minimale`() {
        val (low, high) = GradeCurves.yRange(listOf(19.0, 19.5))
        assertEquals(20.0, high, 0.0)
        assertTrue(high - low >= 2.0)
        val (low2, high2) = GradeCurves.yRange(listOf(0.5))
        assertEquals(0.0, low2, 0.0)
        assertTrue(high2 - low2 >= 2.0)
        assertEquals(0f, GradeCurves.position(2.0, 2.0, 12.0), 0f)
        assertEquals(1f, GradeCurves.position(12.0, 2.0, 12.0), 0f)
        assertEquals(0.5f, GradeCurves.position(7.0, 2.0, 12.0), 0f)
    }

    @Test
    fun `la description de la courbe est lisible par un lecteur d'ecran`() {
        val text = GradeCurves.describe("Moyenne générale par période", listOf("T1", "T2"), listOf(14.2, 15.8))
        assertEquals("Moyenne générale par période : T1 14,2, T2 15,8.", text)
    }

    // --- Le document ---

    @Test
    fun `l'ecran et le document disent la meme chose`() {
        val path = "docs/MOYENNES.md"
        val md = (File(path).takeIf { it.exists() } ?: File("../$path")).readText().replace("\r\n", "\n")
        AverageMethod.sections.forEach { section ->
            assertTrue("titre manquant : ${section.title}", md.contains("## ${section.title}\n"))
            section.paragraphs.forEach { assertTrue("ligne manquante : $it", md.contains(it)) }
        }
    }
}
