package fr.ecole2recte.app.export

import fr.ecole2recte.app.export.ExportFixtures.alternation
import fr.ecole2recte.app.export.ExportFixtures.lesson
import fr.ecole2recte.app.export.ExportFixtures.twoWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableLayoutTest {
    private fun pages(schedule: TypicalSchedule, weeks: List<Int> = listOf(1, 2)) =
        TimetableLayout.pages(schedule, weeks, "Emploi du temps", "Jean Dupont · 2NDE 3", "Généré par ecole2recte")

    private val alternating = TypicalWeeks.build(twoWeeks, alternation)

    @Test
    fun `une page A4 paysage par semaine type`() {
        val result = pages(alternating)
        assertEquals(2, result.size)
        assertEquals(listOf("Emploi du temps — Semaine 1", "Emploi du temps — Semaine 2"), result.map { it.title })
        result.forEach {
            assertEquals(842f, it.width, 0f)
            assertEquals(595f, it.height, 0f)
            assertTrue(it.width > it.height)
        }
        assertEquals(1, pages(alternating, listOf(2)).size)
        assertEquals("Emploi du temps — Semaine 2", pages(alternating, listOf(2)).single().title)
    }

    @Test
    fun `sans alternance une seule page sans numero de semaine`() {
        val single = pages(TypicalWeeks.build(twoWeeks, Alternation()), listOf(1, 2))
        assertEquals(1, single.size)
        assertEquals("Emploi du temps", single.single().title)
    }

    @Test
    fun `les deux pages ont la meme grille et la meme echelle horaire`() {
        val (one, two) = pages(alternating)
        assertEquals(one.hours, two.hours)
        assertEquals(one.headers, two.headers)
        // 8 h à 15 h : huit graduations.
        assertEquals(listOf("8 h", "9 h", "10 h", "11 h", "12 h", "13 h", "14 h", "15 h"), one.hours.map { it.label })
        assertEquals(listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi"), one.headers.map { it.label })
    }

    @Test
    fun `chaque page ne montre que ses cours, les cours communs sur les deux`() {
        val (one, two) = pages(alternating)
        assertEquals(listOf("Maths", "Anglais", "Physique", "SVT"), one.blocks.map { it.subject })
        assertEquals(listOf("Maths", "Anglais", "Chimie", "SVT"), two.blocks.map { it.subject })
        assertEquals(listOf(true, true, false, true), one.blocks.map { it.everyWeek })
    }

    @Test
    fun `les blocs restent dans la grille, sans deborder ni se chevaucher`() {
        for (page in pages(alternating)) {
            page.blocks.forEach {
                assertTrue(it.x >= page.gridLeft && it.x + it.w <= page.gridRight)
                assertTrue(it.y >= page.gridTop && it.y + it.h <= page.gridBottom)
                assertTrue(it.w > 0 && it.h > 0)
            }
            for (a in page.blocks) for (b in page.blocks) {
                if (a === b) continue
                val overlapX = a.x < b.x + b.w && b.x < a.x + a.w
                val overlapY = a.y < b.y + b.h && b.y < a.y + a.h
                assertFalse("${a.subject} chevauche ${b.subject}", overlapX && overlapY)
            }
        }
    }

    @Test
    fun `la hauteur d'un bloc est proportionnelle a la duree`() {
        val page = pages(alternating).first()
        val maths = page.blocks.first { it.subject == "Maths" } // 110 min
        val anglais = page.blocks.first { it.subject == "Anglais" } // 60 min
        assertEquals(110f / 60f, (maths.h + 3f) / (anglais.h + 3f), 0.01f)
        // Le lundi 8 h est tout en haut de la grille.
        assertEquals(page.gridTop + 1.5f, maths.y, 0.01f)
    }

    @Test
    fun `deux cours simultanes se partagent la colonne`() {
        val parallel = listOf(
            lesson("2026-11-02", "10:00", "11:00", "Espagnol", "B1", "Mme Gil"),
            lesson("2026-11-02", "10:00", "11:00", "Allemand", "B2", "M. Rilke"),
        )
        val page = pages(TypicalWeeks.build(parallel, Alternation())).single()
        val (a, b) = page.blocks
        assertEquals(a.w, b.w, 0.01f)
        assertTrue(a.x + a.w <= b.x)
        assertTrue(a.w < page.headers.first().w / 2f)
    }

    @Test
    fun `le texte d'un bloc suit sa hauteur, matiere d'abord, puis salle, horaire, professeur`() {
        val slot = alternating.slots.first { it.subject == "Maths" }
        assertEquals(listOf("Maths"), TimetableLayout.linesFor(slot, 14f).map { it.text })
        assertEquals(listOf("Maths", "Salle B12"), TimetableLayout.linesFor(slot, 30f).map { it.text })
        assertEquals(
            listOf("Maths", "Salle B12", "08:00–09:50", "M. Petit"),
            TimetableLayout.linesFor(slot, 80f).map { it.text },
        )
        assertTrue(TimetableLayout.linesFor(slot, 1f).first().bold)
        assertEquals(1, TimetableLayout.linesFor(slot, 0f).size)
    }

    @Test
    fun `la legende liste chaque matiere une fois, avec son professeur, sous la grille`() {
        val page = pages(alternating).first()
        assertEquals(listOf("Anglais", "Chimie", "Maths", "Physique", "SVT"), page.legend.map { it.subject })
        assertTrue(page.legend.all { it.y > page.gridBottom })
        assertTrue(page.legend.all { it.y + TimetableLayout.LEGEND_ROW <= page.height })
        assertEquals("Mme Roux", page.legend.first().teacher)
    }

    @Test
    fun `une semaine inconnue est signalee sur sa page`() {
        val onlyFirst = TypicalWeeks.build(twoWeeks.filter { it.date < "2026-11-09" }, alternation)
        val (one, two) = pages(onlyFirst)
        assertNull(one.note)
        assertNotNull(two.note)
        assertTrue(two.blocks.isEmpty())
    }

    @Test
    fun `un samedi de cours ajoute une colonne`() {
        val saturday = twoWeeks + lesson("2026-11-07", "09:00", "11:00", "Sport", "Gymnase")
        val page = pages(TypicalWeeks.build(saturday, alternation)).first()
        assertEquals(6, page.headers.size)
        assertEquals("Samedi", page.headers.last().label)
    }

    @Test
    fun `un emploi du temps vide donne quand meme une page lisible`() {
        val page = pages(TypicalWeeks.build(emptyList(), Alternation())).single()
        assertTrue(page.blocks.isEmpty())
        assertTrue(page.hours.size >= 2)
    }

    @Test
    fun `aucune page si aucune semaine n'est demandee en alternance`() {
        assertTrue(pages(alternating, emptyList()).isEmpty())
    }
}
