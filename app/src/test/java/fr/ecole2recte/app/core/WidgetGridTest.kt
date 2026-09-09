package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.widget.WidgetBlock
import fr.ecole2recte.app.core.widget.WidgetGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le partage de la hauteur dans une grille.
 *
 * C'est le calcul qui répond au reproche fait aux anciens widgets : « si on donne de l'espace
 * à un widget, c'est pour qu'il l'utilise, pas pour avoir plus de la moitié de vide ». Ces
 * tests vérifient donc deux choses qu'on ne peut pas juger à l'œil sur un écran d'accueil :
 * la place reçue est **entièrement** distribuée, et aucun bloc porteur de texte ne descend
 * sous la hauteur d'une ligne.
 */
class WidgetGridTest {

    private fun lesson(minutes: Long, text: String = "Maths") =
        WidgetBlock(text = text, minutes = minutes)

    private fun gap(minutes: Long) = WidgetBlock(text = "", minutes = minutes, blank = true)

    private fun free(minutes: Long) = WidgetBlock(text = "Pause", minutes = minutes, free = true)

    @Test
    fun `la hauteur suit la duree`() {
        val heights = WidgetGrid.heights(listOf(lesson(60), lesson(120)), availableDp = 180)

        // Deux heures occupent deux fois la place d'une heure : c'est toute l'idée.
        assertEquals(180, heights.sum())
        assertTrue("$heights", heights[1] > heights[0] * 3 / 2)
    }

    @Test
    fun `toute la place recue est distribuee`() {
        val day = listOf(lesson(50), gap(5), lesson(50), gap(20), lesson(105), free(70), lesson(100))

        listOf(80, 102, 190, 320).forEach { available ->
            val heights = WidgetGrid.heights(day, available)
            assertEquals("hauteur $available", available, heights.sum())
        }
    }

    @Test
    fun `un bloc porteur de texte garde la hauteur d une ligne`() {
        // Un intercours de cinq minutes ne doit pas écraser le cours qui le suit.
        val heights = WidgetGrid.heights(listOf(lesson(50), gap(5), lesson(50)), availableDp = 120)

        assertTrue("$heights", heights[0] >= WidgetGrid.MIN_BLOCK_DP)
        assertTrue("$heights", heights[2] >= WidgetGrid.MIN_BLOCK_DP)
    }

    @Test
    fun `une journee trop chargee est coupee, pas ecrasee`() {
        val day = (1..10).map { lesson(60, "Cours $it") }

        val heights = WidgetGrid.heights(day, availableDp = 60)

        // Dix cours dans soixante points ne tiennent pas : quatre entiers valent mieux que
        // dix bandes de six points où plus rien ne se lit.
        assertTrue("$heights", heights.none { it in 1 until WidgetGrid.MIN_BLOCK_DP })
        assertTrue("$heights", heights.sum() <= 60)
        assertTrue("$heights", heights.count { it > 0 } in 1..4)
    }

    @Test
    fun `un espace nu peut disparaitre, un cours jamais silencieusement`() {
        val heights = WidgetGrid.heights(listOf(gap(2), lesson(120)), availableDp = 40)

        assertTrue("$heights", heights[1] >= WidgetGrid.MIN_BLOCK_DP)
        assertEquals(40, heights.sum())
    }

    // ── Ajustement au nombre d'emplacements ──────────────────────────────────

    @Test
    fun `absorber un espace nu ne perd pas ses minutes`() {
        val day = listOf(lesson(50), gap(5), lesson(50), gap(20), lesson(105), free(70), lesson(100))
        val before = day.sumOf { it.minutes }

        val fitted = WidgetGrid.fit(day, slots = 5)

        assertEquals(5, fitted.size)
        // C'est vital pour la semaine : si une colonne perdait des minutes en s'ajustant,
        // elle glisserait par rapport aux autres et l'échelle commune serait fausse.
        assertEquals(before, fitted.sumOf { it.minutes })
        assertEquals(listOf("Maths", "Maths", "Maths", "Pause", "Maths"), fitted.map { it.text })
    }

    @Test
    fun `l ajustement ne touche a rien quand tout tient`() {
        val day = listOf(lesson(50), gap(5), lesson(50))

        assertEquals(day, WidgetGrid.fit(day, slots = 9))
    }

    @Test
    fun `sans espace nu a absorber, l ajustement coupe la fin`() {
        val day = (1..6).map { lesson(60, "Cours $it") }

        val fitted = WidgetGrid.fit(day, slots = 4)

        assertEquals(listOf("Cours 1", "Cours 2", "Cours 3", "Cours 4"), fitted.map { it.text })
    }

    // ── Niveaux de texte ─────────────────────────────────────────────────────

    @Test
    fun `un bloc ne montre que ce qui tient dans sa hauteur`() {
        assertEquals(0, WidgetGrid.levels(8))
        assertEquals(1, WidgetGrid.levels(WidgetGrid.MIN_BLOCK_DP))
        assertEquals(2, WidgetGrid.levels(34))
        assertEquals(3, WidgetGrid.levels(60))
    }
}
