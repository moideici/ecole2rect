package fr.ecole2recte.app.widget

import fr.ecole2recte.app.sync.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetLayoutLogicTest {
    private fun slot(
        date: String, start: String, end: String, subject: String = "Maths",
        room: String = "B204", teacher: String = "M. Petit", canceled: Boolean = false, color: Int = 0,
    ) = WidgetSlot(date, start, end, subject, room, teacher, canceled, color)

    private fun hour(h: Int, m: Int = 0) = "%02d:%02d".format(h, m)

    /** Jeudi 5 novembre 2026, 9 h 50. */
    private val now = LocalDateTime.of(2026, 11, 5, 9, 50)

    private val week = listOf(
        slot("2026-11-02", "08:00", "09:00", "Maths"),
        slot("2026-11-03", "08:00", "09:00", "Anglais", color = 1),
        slot("2026-11-04", "09:00", "10:00", "Histoire", color = 3),
        slot("2026-11-05", "08:00", "09:00", "Maths"),
        slot("2026-11-05", "09:30", "10:30", "Histoire", color = 3),
        slot("2026-11-05", "10:30", "11:30", "Anglais", color = 1),
        slot("2026-11-05", "13:30", "14:30", "Physique", color = 2),
        slot("2026-11-06", "08:00", "09:00", "Maths"),
        slot("2026-11-06", "10:00", "11:00", "Annulé", canceled = true, color = 4),
    )

    // ----- niveaux de détail -----------------------------------------------------------------

    @Test
    fun `la semaine monte en detail avec la taille`() {
        fun level(w: Float, h: Float) = WidgetLayoutLogic.weekDetail(w, h)
        assertEquals(WeekDetail.Summary, level(110f, 110f))
        assertEquals(WeekDetail.Summary, level(180f, 110f))
        assertEquals(WeekDetail.Columns, level(250f, 110f))
        assertEquals(WeekDetail.Columns, level(180f, 200f))
        assertEquals(WeekDetail.Grid, level(250f, 200f))
        assertEquals(WeekDetail.Grid, level(250f, 320f))
        assertEquals(WeekDetail.Detailed, level(320f, 320f))
        assertEquals(WeekDetail.Detailed, level(320f, 440f))
    }

    @Test
    fun `la journee monte en detail avec la taille`() {
        fun level(w: Float, h: Float) = WidgetLayoutLogic.dayDetail(w, h)
        assertEquals(DayDetail.Focus, level(110f, 110f))
        assertEquals(DayDetail.Focus, level(250f, 110f))
        assertEquals(DayDetail.List, level(180f, 200f))
        assertEquals(DayDetail.List, level(250f, 200f))
        assertEquals(DayDetail.List, level(180f, 440f))
        assertEquals(DayDetail.Timeline, level(250f, 320f))
        assertEquals(DayDetail.Timeline, level(320f, 440f))
    }

    // ----- textes ------------------------------------------------------------------------------

    @Test
    fun `les initiales de matiere`() {
        assertEquals("Ma", WidgetLayoutLogic.initials("Mathématiques"))
        assertEquals("HG", WidgetLayoutLogic.initials("Histoire-Géographie"))
        assertEquals("ÉP", WidgetLayoutLogic.initials("Éducation physique et sportive"))
        assertEquals("AL", WidgetLayoutLogic.initials("Anglais LV1"))
        assertEquals("Sv", WidgetLayoutLogic.initials("SVT"))
    }

    @Test
    fun `la troncature ajoute des points de suspension et respecte la place`() {
        assertEquals("Maths", WidgetLayoutLogic.truncate("Maths", 8))
        assertEquals("Mathém…", WidgetLayoutLogic.truncate("Mathématiques", 7))
        assertEquals("M", WidgetLayoutLogic.truncate("Maths", 1))
        assertEquals("", WidgetLayoutLogic.truncate("Maths", 0))
        assertTrue(WidgetLayoutLogic.truncate("Éducation physique", 9).length <= 9)
    }

    @Test
    fun `les lignes d'un bloc dependent de sa hauteur et de sa largeur`() {
        val block = WeekBlock(480, 540, "Mathématiques", "Ma", "B204", "M. Petit", 0, canceled = false, live = false)
        val tall = WidgetLayoutLogic.blockLines(block, 70f, 90f, WeekDetail.Detailed)
        assertEquals(4, tall.size)
        assertEquals("Mathématiques", tall[0])
        assertEquals("salle B204", tall[1])
        assertEquals("08:00–09:00", tall[2])
        assertEquals("M. Petit", tall[3])
        assertTrue(WidgetLayoutLogic.blockLines(block, 16f, 48f, WeekDetail.Grid).first().endsWith("…"))
        assertEquals(1, WidgetLayoutLogic.blockLines(block, 16f, 48f, WeekDetail.Grid).size)
        assertTrue(WidgetLayoutLogic.blockLines(block, 6f, 48f, WeekDetail.Grid).isEmpty())
        // Colonne étroite : l'initiale remplace le nom, la salle courte remplace « salle B204 ».
        assertEquals("Ma", WidgetLayoutLogic.blockLines(block, 40f, 22f, WeekDetail.Grid).first())
        assertEquals("B204", WidgetLayoutLogic.blockLines(block, 40f, 40f, WeekDetail.Detailed)[1])
    }

    // ----- semaine -----------------------------------------------------------------------------

    @Test
    fun `la semaine a cinq colonnes avec aujourd'hui et le passe reperes`() {
        val columns = WidgetLayoutLogic.weekColumns(week, now)
        assertEquals(listOf("Lun", "Mar", "Mer", "Jeu", "Ven"), columns.map { it.label })
        assertEquals(listOf(2, 3, 4, 5, 6), columns.map { it.number })
        assertEquals(listOf(true, true, true, false, false), columns.map { it.past })
        assertEquals(listOf(false, false, false, true, false), columns.map { it.today })
        val thursday = columns[3]
        assertEquals(4, thursday.lessons)
        assertEquals("08:00", thursday.first)
        assertEquals("14:30", thursday.last)
        assertEquals(1, columns[4].canceled)
        assertEquals(1, columns[4].lessons)
    }

    @Test
    fun `le samedi s'ajoute seulement s'il y a cours`() {
        val withSaturday = week + slot("2026-11-07", "09:00", "11:00", "Sport")
        assertEquals(6, WidgetLayoutLogic.weekColumns(withSaturday, now).size)
        assertEquals(5, WidgetLayoutLogic.weekColumns(week, now).size)
    }

    @Test
    fun `le week-end la grille passe a la semaine suivante si elle est connue`() {
        val saturday = LocalDateTime.of(2026, 11, 7, 10, 0)
        val next = week + slot("2026-11-09", "08:00", "09:00", "Maths")
        val columns = WidgetLayoutLogic.weekColumns(next, saturday)
        assertEquals(LocalDate.of(2026, 11, 9), columns.first().date)
        assertEquals(1, columns.first().lessons)
        // Sans semaine suivante, on garde la semaine qui vient de finir.
        assertEquals(LocalDate.of(2026, 11, 2), WidgetLayoutLogic.weekColumns(week, saturday).first().date)
    }

    @Test
    fun `une double heure est fondue en un seul bloc`() {
        val blocks = WidgetLayoutLogic.dayBlocks(
            listOf(slot("2026-11-05", "08:00", "09:00"), slot("2026-11-05", "09:00", "10:00"), slot("2026-11-05", "10:15", "11:15", "Anglais")),
            nowMin = null,
        )
        assertEquals(2, blocks.size)
        assertEquals(2, blocks[0].hours)
        assertEquals("08:00–10:00", blocks[0].timeLabel)
    }

    @Test
    fun `des cours simultanes sont regroupes et un cours annule remplace n'apparait pas`() {
        val groups = WidgetLayoutLogic.dayBlocks(
            listOf(
                slot("2026-11-05", "08:00", "09:00", "Allemand", room = "A1"),
                slot("2026-11-05", "08:00", "09:00", "Espagnol", room = "A2"),
            ),
            nowMin = null,
        )
        assertEquals(1, groups.size)
        assertEquals("Allemand / Espagnol", groups.single().title)
        assertEquals("A1/A2", groups.single().room)

        val replaced = WidgetLayoutLogic.dayBlocks(
            listOf(
                slot("2026-11-05", "08:00", "09:00", "Maths", canceled = true),
                slot("2026-11-05", "08:00", "09:00", "Permanence"),
            ),
            nowMin = null,
        )
        assertEquals("Permanence", replaced.single().title)
        assertFalse(replaced.single().canceled)
    }

    @Test
    fun `le cours en cours est repere aujourd'hui seulement`() {
        val columns = WidgetLayoutLogic.weekColumns(week, now)
        val live = columns[3].blocks.filter { it.live }
        assertEquals(listOf("Histoire"), live.map { it.title })
        assertTrue(columns.filterIndexed { i, _ -> i != 3 }.all { column -> column.blocks.none { it.live } })
    }

    @Test
    fun `un cours annule reste dans la colonne mais marque comme annule`() {
        val friday = WidgetLayoutLogic.weekColumns(week, now)[4]
        assertEquals(2, friday.blocks.size)
        assertTrue(friday.blocks.last().canceled)
    }

    @Test
    fun `en liste la semaine garde les jours qui tiennent en commencant par aujourd'hui`() {
        val layout = WidgetLayoutLogic.weekLayout(110f, 110f, week, now)
        assertEquals(WeekDetail.Summary, layout.detail)
        assertEquals(3, layout.columns.size)
        assertEquals(listOf("Mer", "Jeu", "Ven"), layout.columns.map { it.label })
        assertEquals(2, layout.hiddenDays)
        // Plus grand : les cinq jours tiennent.
        assertEquals(5, WidgetLayoutLogic.weekLayout(180f, 200f, week, now).columns.size)
    }

    @Test
    fun `en colonnes le nombre de pastilles suit la hauteur`() {
        val small = WidgetLayoutLogic.weekLayout(250f, 110f, week, now)
        val tall = WidgetLayoutLogic.weekLayout(180f, 300f, week, now)
        assertEquals(WeekDetail.Columns, small.detail)
        assertEquals(WeekDetail.Columns, tall.detail)
        assertTrue(small.chipCapacity in 2..5)
        assertTrue(tall.chipCapacity > small.chipCapacity)
    }

    @Test
    fun `la grille couvre exactement la hauteur disponible`() {
        val layout = WidgetLayoutLogic.weekLayout(320f, 440f, week, now)
        assertEquals(WeekDetail.Detailed, layout.detail)
        assertEquals(480, layout.fromMin)
        assertEquals(15 * 60, layout.toMin)
        assertEquals(layout.gridHeightDp, layout.dpPerMinute * (layout.toMin - layout.fromMin), 0.01f)
        assertEquals((8..14).toList(), layout.hourLabels)
        assertEquals((320f - 20f - WidgetLayoutLogic.RAIL_W) / 5, layout.columnWidthDp, 0.01f)
        assertEquals("Semaine du 2 au 6 nov.", layout.title)
        assertTrue(layout.showTitle)
    }

    @Test
    fun `sans donnees la grille garde des bornes raisonnables`() {
        val layout = WidgetLayoutLogic.weekLayout(250f, 300f, emptyList(), now)
        assertEquals(8 * 60, layout.fromMin)
        assertEquals(17 * 60, layout.toMin)
        assertEquals(5, layout.columns.size)
        assertTrue(layout.columns.all { it.blocks.isEmpty() })
    }

    // ----- journée -----------------------------------------------------------------------------

    private val today = listOf(
        slot("2026-11-05", "08:00", "09:00", "Maths"),
        slot("2026-11-05", "10:00", "11:00", "Anglais", color = 1),
        slot("2026-11-05", "11:00", "12:00", "Anglais", color = 1),
        slot("2026-11-05", "13:30", "14:30", "Histoire", color = 3),
        slot("2026-11-06", "08:00", "09:00", "Demain"),
    )

    @Test
    fun `la journee reste sur aujourd'hui tant qu'il reste un cours`() {
        assertEquals(LocalDate.of(2026, 11, 5), WidgetLayoutLogic.dayTarget(today, now))
        assertEquals(LocalDate.of(2026, 11, 5), WidgetLayoutLogic.dayTarget(today, LocalDateTime.of(2026, 11, 5, 14, 0)))
    }

    @Test
    fun `la journee bascule sur le jour suivant une fois finie`() {
        val evening = LocalDateTime.of(2026, 11, 5, 15, 0)
        assertEquals(LocalDate.of(2026, 11, 6), WidgetLayoutLogic.dayTarget(today, evening))
        val layout = WidgetLayoutLogic.dayLayout(250f, 200f, today, evening)
        assertEquals(DayState.NextDay, layout.state)
        assertEquals("Demain", layout.relative)
        assertEquals("Vendredi 6 novembre", layout.title)
        assertEquals("demain 08:00", layout.nextIn)
        assertEquals(0f, layout.progress, 0f)
    }

    @Test
    fun `sans jour suivant la journee est finie ou vide`() {
        val finished = WidgetLayoutLogic.dayLayout(250f, 200f, today.take(4), LocalDateTime.of(2026, 11, 5, 15, 0))
        assertEquals(DayState.Finished, finished.state)
        assertEquals(1f, finished.progress, 0f)
        assertNull(finished.next)
        assertEquals(DayState.NoLessons, WidgetLayoutLogic.dayLayout(250f, 200f, emptyList(), now).state)
    }

    @Test
    fun `en petit on voit le prochain cours avec dans 25 min`() {
        val at = LocalDateTime.of(2026, 11, 5, 9, 35)
        val layout = WidgetLayoutLogic.dayLayout(110f, 110f, today, at)
        assertEquals(DayDetail.Focus, layout.detail)
        assertNull(layout.current)
        assertEquals("Anglais", layout.next?.title)
        assertEquals("dans 25 min", layout.nextIn)
        assertTrue(layout.rows.isEmpty())
        assertEquals(DayState.Active, layout.state)
    }

    @Test
    fun `pendant un cours il est donne comme en cours avec la progression de la journee`() {
        val at = LocalDateTime.of(2026, 11, 5, 10, 30)
        val layout = WidgetLayoutLogic.dayLayout(110f, 110f, today, at)
        assertEquals("Anglais", layout.current?.title)
        assertEquals(2, layout.current?.hours)
        assertEquals("Histoire", layout.next?.title)
        assertEquals("dans 3 h 00", layout.nextIn)
        // 8 h -> 14 h 30 : 150 minutes écoulées sur 390.
        assertEquals(150f / 390f, layout.progress, 0.001f)
        assertEquals("Fin à 14:30 · reste 4 h 00", layout.remaining)
    }

    @Test
    fun `la liste saute le passe et nomme les pauses`() {
        val layout = WidgetLayoutLogic.dayLayout(250f, 200f, today, now)
        assertEquals(DayDetail.List, layout.detail)
        assertEquals(listOf(RowKind.Lesson, RowKind.Gap, RowKind.Lesson), layout.rows.map { it.kind })
        assertEquals("Anglais", layout.rows[0].title)
        assertEquals("Pause déjeuner · 1 h 30", layout.rows[1].lines.single())
        assertEquals(0, layout.hiddenLessons)
    }

    @Test
    fun `la liste annonce ce qu'elle ne peut pas montrer`() {
        val many = (8..16).map { slot("2026-11-05", hour(it), hour(it, 50), "Cours $it") }
        val layout = WidgetLayoutLogic.dayLayout(180f, 160f, many, LocalDateTime.of(2026, 11, 5, 7, 0))
        assertEquals(DayDetail.List, layout.detail)
        val shown = layout.rows.count { it.kind == RowKind.Lesson }
        assertTrue(shown in 1..4)
        assertEquals(9 - shown, layout.hiddenLessons)
    }

    @Test
    fun `la frise montre toute la journee avec des hauteurs proportionnelles aux durees`() {
        val layout = WidgetLayoutLogic.dayLayout(250f, 320f, today, now)
        assertEquals(DayDetail.Timeline, layout.detail)
        assertEquals(
            listOf(RowKind.Lesson, RowKind.Gap, RowKind.Lesson, RowKind.Gap, RowKind.Lesson),
            layout.rows.map { it.kind },
        )
        val lessons = layout.rows.filter { it.kind == RowKind.Lesson }
        assertTrue(lessons.all { it.heightDp in WidgetLayoutLogic.TIMELINE_MIN..WidgetLayoutLogic.TIMELINE_MAX })
        assertTrue(lessons[1].heightDp >= lessons[0].heightDp)
        assertTrue(lessons[0].past)
        assertEquals("Heure libre · 1 h", layout.rows[1].lines.single())
        val total = layout.rows.sumOf { it.heightDp.toDouble() }
        assertTrue(total <= 320.0 - WidgetLayoutLogic.DAY_HEADER_H + 0.5)
        // Les lignes de texte portent salle et professeur quand la hauteur le permet.
        assertTrue(layout.rows[2].lines.any { it.contains("salle B204") })
    }

    @Test
    fun `la frise abandonne d'abord le passe puis la fin de journee quand la place manque`() {
        val many = (8..17).map { slot("2026-11-05", hour(it), hour(it, 50), "Cours $it") }
        val at = LocalDateTime.of(2026, 11, 5, 12, 10)
        val layout = WidgetLayoutLogic.dayLayout(250f, 272f, many, at)
        assertEquals(DayDetail.Timeline, layout.detail)
        val lessons = layout.rows.filter { it.kind == RowKind.Lesson }
        assertTrue("les cours finis sont retirés les premiers", lessons.first().startMin >= 11 * 60)
        assertTrue(lessons.any { it.live })
        assertTrue(layout.hiddenLessons >= 0)
        assertTrue(layout.rows.sumOf { it.heightDp.toDouble() } <= 272.0 - WidgetLayoutLogic.DAY_HEADER_H + 0.5)
    }

    @Test
    fun `dans combien de temps`() {
        assertEquals("dans 25 min", WidgetLayoutLogic.startsIn(10 * 60, LocalDate.of(2026, 11, 5), LocalDateTime.of(2026, 11, 5, 9, 35)))
        assertEquals("dans 1 h 05", WidgetLayoutLogic.startsIn(10 * 60, LocalDate.of(2026, 11, 5), LocalDateTime.of(2026, 11, 5, 8, 55)))
        assertEquals("demain 08:00", WidgetLayoutLogic.startsIn(8 * 60, LocalDate.of(2026, 11, 6), now))
        assertEquals("lundi 08:00", WidgetLayoutLogic.startsIn(8 * 60, LocalDate.of(2026, 11, 9), now))
    }

    @Test
    fun `des heures malformees ne plantent pas`() {
        val broken = listOf(slot("2026-11-05", "xx:yy", "zz", "Bug"), slot("pas-une-date", "08:00", "09:00"))
        WidgetLayoutLogic.dayLayout(250f, 320f, broken, now)
        WidgetLayoutLogic.weekLayout(320f, 440f, broken, now)
    }
}
