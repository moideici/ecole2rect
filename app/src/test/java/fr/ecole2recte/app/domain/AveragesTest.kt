package fr.ecole2recte.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles de [Averages], reprises des tests du site (`packages/protocol/test/insights.test.ts`). */
class AveragesTest {
    private fun note(
        id: String,
        value: Double?,
        date: String? = "2026-10-01",
        subject: String = "Mathématiques",
        coefficient: Double = 1.0,
        scale: Double = 20.0,
        counted: Boolean = true,
        period: String = "A001",
    ) = GradeEntry(id, subject, 0, "Devoir $id", value, scale, coefficient, date, null, null, null, counted, period)

    private fun matiere(subject: String, average: Double?, coefficient: Double?, parent: String = "") =
        PublishedAverage("A001", subject, average, coefficient, parent)

    private val avant = note("1", 12.0, "2026-09-01")
    private val derniere = note("2", 16.0, "2026-09-15")

    @Test
    fun `la moyenne est ponderee et ramenee sur 20`() {
        // 16/20 coef 2 et 8/10 coef 1 (soit 16/20) -> 16 ; avec 10/20 coef 1 -> (32 + 16 + 10) / 4
        val average = Averages.weightedAverage(listOf(note("a", 16.0, coefficient = 2.0), note("b", 8.0, scale = 10.0), note("c", 10.0)))
        assertEquals(14.5, average!!, 1e-9)
        assertEquals(20.0, Averages.weightedAverage(listOf(note("x", 5.0, scale = 5.0)))!!, 1e-9)
    }

    @Test
    fun `ni Abs ni note non significative, mais un coefficient nul vaut 1 comme sur le site`() {
        val grades = listOf(note("a", 12.0), note("b", null), note("c", 0.0, counted = false), note("d", 18.0, coefficient = 0.0))
        // (12 + 18) / 2 : Abs n'est pas un zéro, la note hors moyenne ne compte pas, le coefficient 0 vaut 1.
        assertEquals(15.0, Averages.weightedAverage(Averages.gradesFor(grades, "A001"))!!, 1e-9)
        assertNull(Averages.weightedAverage(emptyList()))
        // Un barème nul écarte la note au lieu de la compter telle quelle.
        assertNull(Averages.weightedAverage(listOf(note("z", 12.0, scale = 0.0))))
    }

    @Test
    fun `garde celle de l'etablissement quand elle connait la derniere note`() {
        val verdict = Averages.reconcileAverage(14.0, listOf(avant, derniere))
        assertEquals(AverageOrigin.Official, verdict.origin)
        assertEquals(14.0, verdict.value!!, 0.0)
        assertTrue(verdict.behind.isEmpty())
        // 14,00 publié « 14,04 » : à l'arrondi près, c'est la même moyenne.
        assertEquals(AverageOrigin.Official, Averages.reconcileAverage(14.04, listOf(avant, derniere)).origin)
    }

    @Test
    fun `calcule la sienne quand la publiee est celle d'avant la derniere note`() {
        val verdict = Averages.reconcileAverage(12.0, listOf(avant, derniere))
        assertEquals(AverageOrigin.Recomputed, verdict.origin)
        assertEquals(14.0, verdict.value!!, 1e-9)
        assertEquals(12.0, verdict.official!!, 0.0)
        assertEquals(listOf("2"), verdict.behind.map { it.id })
        val encoreAvant = note("0", 10.0, "2026-08-25")
        assertEquals(listOf("2", "1"), Averages.reconcileAverage(10.0, listOf(encoreAvant, avant, derniere)).behind.map { it.id })
    }

    @Test
    fun `laisse le bulletin avoir raison sur un ecart inexplique ou des notes sans date`() {
        assertEquals(AverageOrigin.Official, Averages.reconcileAverage(5.0, listOf(avant, derniere)).origin)
        assertEquals(AverageOrigin.Official, Averages.reconcileAverage(12.0, listOf(note("1", 12.0, null), note("2", 16.0, null))).origin)
        val computed = Averages.reconcileAverage(null, listOf(avant, derniere))
        assertEquals(AverageOrigin.Computed, computed.origin)
        assertTrue(computed.computed)
    }

    @Test
    fun `recalcule la generale des qu'une matiere est en retard`() {
        val notes = listOf(avant, derniere, note("3", 8.0, "2026-09-10", subject = "Anglais"))
        val matieres = listOf(matiere("Mathématiques", 12.0, 4.0), matiere("Anglais", 8.0, 2.0))
        val verdict = Averages.generalAverage(10.67, notes, "A001", matieres)
        assertEquals(AverageOrigin.Recomputed, verdict.origin)
        // (14 × 4 + 8 × 2) / 6 : une moyenne de moyennes, pondérée par les coefficients.
        assertEquals(12.0, verdict.value!!, 1e-9)
        assertEquals(listOf("2"), verdict.behind.map { it.id })
        val kept = Averages.generalAverage(13.42, listOf(avant, derniere), "A001", listOf(matiere("Mathématiques", 14.0, 4.0)))
        assertEquals(AverageOrigin.Official, kept.origin)
        assertEquals(13.42, kept.value!!, 0.0)
    }

    @Test
    fun `ne compte pas deux fois une specialite rangee sous son tronc commun`() {
        val subjects = Averages.reconcileSubjectAverages(
            listOf(matiere("Mathématiques", 10.0, 2.0), matiere("Spé maths", 18.0, 2.0, parent = "Mathématiques")),
            emptyList(),
            "A001",
        )
        assertEquals(10.0, Averages.averageOfSubjects(subjects)!!, 1e-9)
    }

    @Test
    fun `une matiere seulement notee entre dans la moyenne generale`() {
        val notes = listOf(note("1", 10.0), note("2", 16.0, subject = "Anglais"))
        val subjects = Averages.subjectsOfPeriod(listOf(matiere("Mathématiques", 10.0, 3.0)), notes, "A001")
        assertEquals(listOf("Mathématiques", "Anglais"), subjects.map { it.subject })
        // (10 × 3 + 16 × 1) / 4
        assertEquals(11.5, Averages.generalAverage(null, notes, "A001", subjects).value!!, 1e-9)
    }

    @Test
    fun `simulateur et note a viser`() {
        val grades = listOf(note("a", 15.5, coefficient = 2.0), note("b", null), note("c", 20.0, counted = false))
        val simulation = Averages.simulate(grades, "A001", null, 10.0)
        assertEquals(15.5, simulation.before!!, 1e-9)
        assertEquals(13.6667, simulation.after!!, 1e-3)
        assertTrue(simulation.delta!! < 0)
        val vise = Averages.requiredGrade(grades, "A001", null, 16.0)!!
        // (16 × 3 − 31) / 1 = 17
        assertEquals(17.0, vise.value, 1e-9)
        assertTrue(vise.reachable)
        assertFalse(Averages.requiredGrade(grades, "A001", null, 20.0)!!.reachable)
        assertNull(Averages.requiredGrade(grades, "A001", null, 14.0, coefficient = 0.0))
    }

    @Test
    fun `courbe cumulee par jour, ponderee et sur 20`() {
        val grades = listOf(
            note("1", 10.0, "2026-11-01"),
            note("2", 10.0, "2026-11-02", scale = 10.0, coefficient = 2.0),
            note("3", 0.0, "2026-11-01", counted = false),
            note("4", null, "2026-11-01"),
            note("5", 10.0, null),
            note("6", 10.0, "2026-11-01", period = "T2"),
        )
        val series = Averages.averageSeries(grades, "A001", emptyList(), "Mathématiques")
        assertEquals(listOf("2026-11-01", "2026-11-02"), series.map { it.date })
        assertEquals(10.0, series[0].value, 1e-9)
        assertEquals(50.0 / 3, series[1].value, 1e-9)
        val general = Averages.averageSeries(
            listOf(note("1", 10.0, "2026-11-01"), note("2", 10.0, "2026-11-01"), note("3", 20.0, "2026-11-01", subject = "Français")),
            "A001",
            listOf(PublishedAverage("A001", "Français", coefficient = 3.0)),
        )
        assertEquals(listOf(AveragePoint("2026-11-01", 17.5)), general)
    }

    @Test
    fun `l'affichage arrondit au centieme comme toFixed`() {
        assertEquals("14,13", FrenchFormat.average(14.125))
        // 1,005 vaut 1,00499… en binaire : JavaScript écrit « 1.00 », l'application aussi.
        assertEquals("1,00", FrenchFormat.average(1.005))
        assertEquals("15,80", FrenchFormat.average(15.8))
        assertEquals("0,00", FrenchFormat.average(0.0))
    }
}
