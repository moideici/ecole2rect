package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * Les chiffres qu'on met devant un élève.
 *
 * Une moyenne, un écart, un « il manque 0,2 » : ce sont des phrases sur lesquelles il décide
 * quoi réviser. Elles n'ont donc pas le droit d'être approximatives, ni de se contredire
 * d'une carte à l'autre. Ces tests tiennent trois promesses précises : un effet sur la
 * moyenne n'est pas un écart entre deux paquets de notes, l'objectif et son conseil se
 * calculent sur la même base, et un compte de notes porte sur l'ensemble qui sert à la
 * moyenne affichée juste à côté.
 */
class GradeFiguresTest {

    private val term = LocalDate.of(2026, 9, 1)

    private fun grade(
        id: String,
        value: Double?,
        subject: String = "Maths",
        coefficient: Double = 1.0,
        day: Long = 0,
        counted: Boolean = true,
        period: String = "T1",
    ) = Grade(
        id = id,
        subject = subject,
        label = "Devoir $id",
        value = value,
        scale = 20.0,
        coefficient = coefficient,
        classAverage = null,
        date = term.plusDays(day),
        periodCode = period,
        counted = counted,
    )

    private fun snapshot(grades: List<Grade>, periodAverage: Double? = null) = SchoolSnapshot(
        grades = grades,
        periods = listOf(
            Period(
                code = "T1",
                name = "Trimestre 1",
                average = periodAverage,
                start = term,
                end = term.plusMonths(3),
                current = true,
            ),
        ),
    )

    // ── L'effet des dernières notes ──────────────────────────────────────────

    @Test
    fun `l effet des dernieres notes n est pas l ecart entre deux paquets`() {
        // Cinq notes à 16, puis cinq à 10. L'écart entre les deux paquets fait six points ;
        // ce que les cinq dernières ont fait à la moyenne générale en fait trois.
        val grades = (1..5).map { grade("vieille$it", 16.0, day = it.toLong()) } +
            (1..5).map { grade("recente$it", 10.0, day = 10L + it) }

        val trend = Insights.trend(grades)
        val effect = Insights.recentEffect(grades)

        assertNotNull(trend)
        assertNotNull(effect)
        assertEquals(-6.0, trend!!, 0.001)
        assertEquals(-3.0, effect!!, 0.001)
    }

    @Test
    fun `l effet est exactement la moyenne moins celle d avant`() {
        val grades = listOf(
            grade("a", 12.0, day = 1),
            grade("b", 8.0, day = 2, coefficient = 3.0),
            grade("c", 18.0, day = 3),
        )

        val effect = Insights.recentEffect(grades, recentCount = 1)

        // Sans la dernière note, la moyenne est celle des deux premières : la différence
        // est l'effet, au point près, pas une estimation.
        val withAll = Insights.weightedAverage(grades)!!
        val withoutLast = Insights.weightedAverage(grades.dropLast(1))!!
        assertEquals(withAll - withoutLast, effect!!, 0.0001)
    }

    @Test
    fun `sans note anterieure, il n y a pas d effet a annoncer`() {
        val grades = (1..3).map { grade("g$it", 14.0, day = it.toLong()) }

        // Trois notes ne permettent pas de dire ce que « les cinq dernières » ont coûté :
        // se taire vaut mieux que de répondre sur un ensemble vide.
        assertNull(Insights.recentEffect(grades))
    }

    // ── L'objectif et son conseil ────────────────────────────────────────────

    @Test
    fun `l ecart et le levier se calculent sur la meme base`() {
        // Le bulletin annonce 13,0 ; les notes, elles, donnent 15,0. L'objectif vise 14.
        val grades = listOf(
            grade("a", 15.0, coefficient = 1.0),
            grade("b", 15.0, subject = "Anglais", coefficient = 1.0),
        )

        val objective = Insights.objective(snapshot(grades, periodAverage = 13.0), term.plusDays(10), 14.0)!!

        // Sur la base des notes, l'objectif est atteint : le conseil ne peut donc pas
        // coexister avec un « il manque 1 » tiré d'un autre socle.
        assertEquals(15.0, objective.current, 0.001)
        assertTrue(objective.reached)
        assertEquals(13.0, objective.official!!, 0.001)
    }

    @Test
    fun `le levier amene vraiment a l objectif`() {
        val grades = listOf(
            grade("a", 10.0, coefficient = 2.0),
            grade("b", 12.0, subject = "Anglais", coefficient = 1.0),
        )
        val target = 13.0

        val objective = Insights.objective(snapshot(grades), term.plusDays(10), target)!!
        val lever = objective.lever!!

        // On rejoue la note conseillée dans la matière conseillée : la moyenne doit
        // atteindre l'objectif, sans quoi le conseil est faux.
        val simulated = Insights.weightedAverage(
            grades + grade("simulee", lever.mark, subject = lever.subject, coefficient = lever.coefficient),
        )!!
        assertEquals(target, simulated, 0.01)
        assertTrue("un levier ne peut pas exiger plus de 20", lever.mark <= 20.0)
    }

    @Test
    fun `un objectif hors de portee ne propose pas de note impossible`() {
        val grades = (1..10).map { grade("g$it", 4.0, day = it.toLong()) }

        val objective = Insights.objective(snapshot(grades), term.plusDays(20), 18.0)!!

        // Dix notes à 4 ne se rattrapent pas avec une seule : mieux vaut pas de levier
        // qu'un « un 23 sur 20 suffirait ».
        assertNull(objective.lever)
        assertTrue(objective.gap > 0.0)
    }

    // ── L'année en chiffres ─────────────────────────────────────────────────

    @Test
    fun `le compte de notes porte sur l ensemble qui fait la moyenne`() {
        val grades = listOf(
            grade("a", 10.0),
            grade("b", 20.0),
            grade("hors1", 2.0, counted = false),
            grade("hors2", 2.0, counted = false),
            grade("sansvaleur", null),
        )

        val stats = Insights.yearStats(snapshot(grades), AppSettings(), term.plusDays(10))

        // Deux notes comptées, moyenne 15 : annoncer « 4 notes · 15 de moyenne » ferait
        // deux chiffres qui ne parlent pas du même ensemble.
        assertEquals(2, stats.gradeCount)
        assertEquals(2, stats.uncountedGrades)
        assertEquals(15.0, stats.average!!, 0.001)
        assertTrue("une note sans valeur n'est pas une note écartée du calcul", stats.uncountedGrades == 2)
    }

    @Test
    fun `la moyenne annuelle reste ponderee par les coefficients`() {
        val grades = listOf(
            grade("a", 10.0, coefficient = 1.0),
            grade("b", 16.0, coefficient = 3.0),
        )

        val stats = Insights.yearStats(snapshot(grades), AppSettings(), term.plusDays(10))

        assertEquals(14.5, stats.average!!, 0.001)
        assertTrue(abs(stats.average!! - 13.0) > 0.1) // pas une moyenne simple
    }
}
