package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetShape
import fr.ecole2recte.app.core.widget.WidgetSize
import fr.ecole2recte.app.core.widget.WidgetSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Le rythme d'une journée : début, pause méridienne, fin.
 *
 * La pause n'est écrite nulle part dans la réponse d'ÉcoleDirecte — c'est un trou parmi
 * d'autres. Ces tests fixent la règle qui la distingue, parce que se tromper de trou ferait
 * annoncer « pause 15:00 » à un élève qui déjeune à midi.
 */
class DayRhythmTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    private fun lesson(id: String, subject: String, from: Pair<Int, Int>, to: Pair<Int, Int>) = Lesson(
        id = id,
        subject = subject,
        room = "C011",
        start = monday.atTime(from.first, from.second),
        end = monday.atTime(to.first, to.second),
    )

    /** Une journée ordinaire : deux cours le matin, déjeuner, deux cours l'après-midi. */
    private val ordinaryDay = listOf(
        lesson("1", "Philosophie", 8 to 0, 9 to 0),
        // Un interclasse de dix minutes, qui n'est pas une pause.
        lesson("2", "Mathématiques", 9 to 10, 11 to 55),
        lesson("3", "Anglais", 13 to 30, 15 to 30),
        lesson("4", "Histoire", 15 to 30, 17 to 0),
    )

    private fun snapshotOf(lessons: List<Lesson>) = SchoolSnapshot(lessons = lessons)

    // ── La pause méridienne ──────────────────────────────────────────────────

    @Test
    fun `reconnait la pause du midi`() {
        val pause = Insights.middayBreak(ordinaryDay, monday)
        assertEquals(monday.atTime(11, 55), pause?.start)
        assertEquals(monday.atTime(13, 30), pause?.end)
        assertEquals(95L, pause?.minutes)
    }

    @Test
    fun `ignore un interclasse de dix minutes`() {
        // Sans seuil, le trou de 9:00 à 9:10 serait candidat — et le plus proche du matin.
        val pause = Insights.middayBreak(ordinaryDay, monday)
        assertTrue((pause?.minutes ?: 0) >= 30)
    }

    @Test
    fun `ignore un trou de fin d'apres-midi`() {
        val day = listOf(
            lesson("1", "Philosophie", 8 to 0, 12 to 0),
            // Deux heures libres, mais à 16 h : ce n'est pas le déjeuner.
            lesson("2", "Mathématiques", 12 to 0, 16 to 0),
            lesson("3", "Anglais", 18 to 0, 19 to 0),
        )
        assertNull(Insights.middayBreak(day, monday))
    }

    @Test
    fun `n'invente pas de pause dans une journee continue`() {
        val day = listOf(
            lesson("1", "Philosophie", 8 to 0, 12 to 0),
            lesson("2", "Mathématiques", 12 to 0, 16 to 0),
        )
        assertNull(Insights.middayBreak(day, monday))
    }

    @Test
    fun `garde le plus long quand deux trous chevauchent le midi`() {
        val day = listOf(
            lesson("1", "Philosophie", 8 to 0, 11 to 0),
            lesson("2", "Mathématiques", 11 to 40, 12 to 0),
            lesson("3", "Anglais", 13 to 30, 15 to 0),
        )
        assertEquals(90L, Insights.middayBreak(day, monday)?.minutes)
    }

    @Test
    fun `ignore un cours annule pour situer la pause`() {
        val day = ordinaryDay.map { if (it.id == "3") it.copy(canceled = true) else it }
        // Anglais annulé : la reprise est celle d'Histoire, donc la pause s'allonge d'autant.
        assertEquals(monday.atTime(15, 30), Insights.middayBreak(day, monday)?.end)
    }

    // ── Le widget « Horaires du jour » ───────────────────────────────────────

    private fun hours(lessons: List<Lesson>, now: LocalDateTime) = WidgetContent.payload(
        kind = WidgetKind.DAY_HOURS,
        snapshot = snapshotOf(lessons),
        settings = AppSettings(),
        now = now,
        shape = WidgetShape.HOURS,
    )

    @Test
    fun `donne les trois reperes de la journee`() {
        val payload = hours(ordinaryDay, monday.atTime(9, 30))
        assertEquals(listOf("début", "pause", "fin"), payload.lines.map { it.text })
        assertEquals(listOf("08:00", "11:55", "17:00"), payload.lines.map { it.trailing })
        assertEquals("AUJOURD'HUI", payload.label)
        assertEquals("08:00 – 17:00", payload.title)
    }

    @Test
    fun `dit un tiret plutot que d'inventer une pause`() {
        val continu = listOf(lesson("1", "Philosophie", 8 to 0, 16 to 0))
        assertEquals("—", hours(continu, monday.atTime(9, 0)).lines[1].trailing)
    }

    @Test
    fun `passe a la prochaine journee une fois les cours finis`() {
        val lessons = ordinaryDay + Lesson(
            id = "9",
            subject = "Sport",
            start = monday.plusDays(1).atTime(10, 0),
            end = monday.plusDays(1).atTime(12, 0),
        )
        // 18 h : « fin 17:00 » n'apprend plus rien, c'est demain qui compte.
        val payload = hours(lessons, monday.atTime(18, 0))
        assertEquals("10:00", payload.lines.first().trailing)
        assertEquals("12:00", payload.lines.last().trailing)
        assertTrue(payload.label != "AUJOURD'HUI")
    }

    @Test
    fun `ne plante pas sans emploi du temps`() {
        val payload = hours(emptyList(), monday.atTime(9, 0))
        assertTrue(payload.empty)
        assertTrue(payload.spoken.isNotBlank())
    }

    // ── La journée lue comme un emploi du temps ──────────────────────────────

    @Test
    fun `la liste du jour porte les plages horaires et la pause`() {
        val payload = WidgetContent.payload(
            kind = WidgetKind.TODAY,
            snapshot = snapshotOf(ordinaryDay),
            settings = AppSettings(),
            now = monday.atTime(7, 0),
            shape = WidgetShape.LIST,
            maxLines = 8,
        )
        assertEquals(
            listOf("Philosophie", "Mathématiques", "Pause méridienne", "Anglais", "Histoire"),
            payload.lines.map { it.text },
        )
        // Une heure de début seule ne dit pas si le cours dure une heure ou trois.
        assertEquals("08:00–09:00", payload.lines.first().trailing)
        assertEquals("11:55", payload.lines[2].trailing)
        assertEquals("1 h 35", payload.lines[2].secondary)
    }

    @Test
    fun `la pause n'apparait qu'une fois`() {
        val payload = WidgetContent.payload(
            kind = WidgetKind.TODAY,
            snapshot = snapshotOf(ordinaryDay),
            settings = AppSettings(),
            now = monday.atTime(7, 0),
            shape = WidgetShape.LIST,
            maxLines = 8,
        )
        assertEquals(1, payload.lines.count { it.text == "Pause méridienne" })
    }

    // ── La forme suit la place ───────────────────────────────────────────────

    @Test
    fun `les horaires occupent un deux-par-un, et rien d'autre`() {
        assertEquals(WidgetShape.HOURS, WidgetSizing.shapeFor(WidgetKind.DAY_HOURS, WidgetSize(150, 60)))
        // Agrandi, il devient la journée détaillée ; réduit, le compte à rebours.
        assertEquals(WidgetShape.LIST, WidgetSizing.shapeFor(WidgetKind.DAY_HOURS, WidgetSize(150, 150)))
        assertEquals(WidgetShape.TILE, WidgetSizing.shapeFor(WidgetKind.DAY_HOURS, WidgetSize(60, 60)))
        // Les autres widgets ne prennent pas cette forme par accident.
        assertEquals(WidgetShape.ROW, WidgetSizing.shapeFor(WidgetKind.NEXT_LESSON, WidgetSize(150, 60)))
    }

    @Test
    fun `chaque forme du widget horaires reste lisible`() {
        WidgetShape.entries.forEach { shape ->
            val payload = WidgetContent.payload(
                kind = WidgetKind.DAY_HOURS,
                snapshot = snapshotOf(ordinaryDay),
                settings = AppSettings(),
                now = monday.atTime(9, 30),
                shape = shape,
            )
            assertTrue("forme $shape sans titre", payload.title.isNotBlank())
            assertTrue("forme $shape muette", payload.spoken.isNotBlank())
        }
    }
}
