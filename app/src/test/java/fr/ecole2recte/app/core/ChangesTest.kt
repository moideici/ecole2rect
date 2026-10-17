package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Change
import fr.ecole2recte.app.core.model.ChangeKind
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Changes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Le moteur de « ce qui a changé ».
 *
 * Les cas qui comptent ne sont pas les changements eux-mêmes — comparer deux listes est
 * facile — mais les trois silences : première synchronisation, semaine fraîchement chargée,
 * et passé. Une fonctionnalité qui crie à chaque ouverture apprend à ne plus la lire.
 */
class ChangesTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val tuesday = monday.plusDays(1)
    private val nextMonday = monday.plusWeeks(1)
    private val at = 1_800_000_000_000L
    private val dayMillis = 24L * 60 * 60 * 1000

    private fun lesson(
        id: String,
        day: LocalDate,
        hour: Int,
        subject: String = "Maths",
        room: String = "801",
        canceled: Boolean = false,
    ) = Lesson(
        id = id,
        subject = subject,
        room = room,
        start = day.atTime(hour, 5),
        end = day.atTime(hour + 1, 0),
        canceled = canceled,
    )

    private fun snapshot(
        lessons: List<Lesson> = emptyList(),
        grades: List<Grade> = emptyList(),
        homework: List<HomeworkItem> = emptyList(),
        messages: List<MessageItem> = emptyList(),
    ) = SchoolSnapshot(lessons = lessons, grades = grades, homework = homework, messages = messages)

    private fun between(before: SchoolSnapshot, after: SchoolSnapshot) =
        Changes.between(before, after, monday, at)

    @Test
    fun `une premiere synchronisation ne signale rien`() {
        val after = snapshot(lessons = listOf(lesson("1", monday, 8), lesson("2", tuesday, 10)))

        // Tout est nouveau, donc rien ne l'est : c'est l'arrivée des données, pas un changement.
        assertEquals(emptyList<Change>(), between(SchoolSnapshot(), after))
    }

    @Test
    fun `une annulation est signalee, avec le jour et l heure`() {
        val before = snapshot(lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10)))
        val after = snapshot(
            lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10, canceled = true)),
        )

        val change = between(before, after).single()
        assertEquals(ChangeKind.LESSON_CANCELED, change.kind)
        assertEquals("Maths 10:05 annulé", change.text)
        assertTrue(change.detail, change.detail.startsWith("aujourd'hui"))
    }

    @Test
    fun `un changement de salle est un deplacement, et un second en est un autre`() {
        val before = snapshot(lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10)))
        val moved = snapshot(
            lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10, room = "C12")),
        )
        val movedAgain = snapshot(
            lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10, room = "L3")),
        )

        val first = between(before, moved).single()
        assertEquals(ChangeKind.LESSON_MOVED, first.kind)
        assertEquals("Maths déplacé en C12", first.text)

        // La salle est dans l'identifiant : le second déplacement n'est pas le premier.
        val second = between(moved, movedAgain).single()
        assertTrue(second.id != first.id)
    }

    @Test
    fun `une semaine fraichement chargee n est pas une avalanche d ajouts`() {
        val before = snapshot(lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10)))
        val after = snapshot(
            lessons = before.lessons + listOf(
                lesson("10", nextMonday, 8),
                lesson("11", nextMonday, 10),
                lesson("12", nextMonday, 14),
            ),
        )

        // C'est exactement ce que fait le chargement semaine par semaine : trente cours
        // apparaissent d'un coup sans que rien n'ait bougé à l'emploi du temps.
        assertEquals(emptyList<Change>(), between(before, after))
    }

    @Test
    fun `un cours ajoute a une journee deja connue est signale`() {
        val before = snapshot(lessons = listOf(lesson("1", monday, 8)))
        val after = snapshot(
            lessons = listOf(lesson("1", monday, 8), lesson("2", monday, 10, subject = "Anglais")),
        )

        val change = between(before, after).single()
        assertEquals(ChangeKind.LESSON_ADDED, change.kind)
        assertEquals("Anglais ajouté", change.text)
    }

    @Test
    fun `le passe ne change plus rien`() {
        val yesterday = monday.minusDays(1)
        val before = snapshot(lessons = listOf(lesson("1", yesterday, 8), lesson("2", monday, 8)))
        val after = snapshot(
            lessons = listOf(lesson("1", yesterday, 8, canceled = true), lesson("2", monday, 8)),
        )

        assertEquals(emptyList<Change>(), between(before, after))
    }

    @Test
    fun `une note publiee est signalee, pas celles qu on connaissait`() {
        val old = Grade("1", "Maths", "DS", 14.0, 20.0, 2.0, null, monday, "A001")
        val fresh = Grade("2", "Histoire-géo", "Composition", 16.0, 20.0, 3.0, null, monday, "A001")
        val before = snapshot(grades = listOf(old))
        val after = snapshot(grades = listOf(fresh, old))

        val change = between(before, after).single()
        assertEquals(ChangeKind.GRADE, change.kind)
        assertEquals("Note en Histoire-géo · 16", change.text)
        assertEquals("Composition · coef. 3", change.detail)
    }

    @Test
    fun `un message recu est signale une fois`() {
        val date = LocalDateTime.of(2026, 9, 7, 9, 12)
        val before = snapshot(messages = listOf(MessageItem("1", "Objet", "Vie scolaire", date)))
        val after = snapshot(
            messages = listOf(
                MessageItem("2", "TP noté de jeudi", "M. Gay-Vidal", date),
                MessageItem("1", "Objet", "Vie scolaire", date),
            ),
        )

        val change = between(before, after).single()
        assertEquals(ChangeKind.MESSAGE, change.kind)
        assertEquals("Message de M. Gay-Vidal", change.text)
    }

    // ── Historique ───────────────────────────────────────────────────────────

    private fun change(id: String, atMillis: Long) = Change(
        id = id,
        kind = ChangeKind.GRADE,
        text = id,
        noticedOn = monday,
        atMillis = atMillis,
    )

    @Test
    fun `l historique ne garde pas deux fois le meme changement`() {
        val existing = listOf(change("note:1", at))

        val merged = Changes.merge(existing, listOf(change("note:1", at + 60_000)), at + 60_000)

        assertEquals(1, merged.size)
        // C'est le premier repérage qui fait foi : sinon un changement remonterait en tête
        // à chaque synchronisation, et paraîtrait nouveau chaque fois.
        assertEquals(at, merged.single().atMillis)
    }

    @Test
    fun `l historique oublie ce qui a plus d une semaine`() {
        val existing = listOf(
            change("vieux", at - 8 * dayMillis),
            change("recent", at - 2 * dayMillis),
        )

        val merged = Changes.merge(existing, emptyList(), at)

        assertEquals(listOf("recent"), merged.map { it.id })
    }

    @Test
    fun `l historique est borne et le plus recent vient en tete`() {
        val fresh = (1..Changes.MAX_ENTRIES + 10).map { change("n$it", at - it * 1_000L) }

        val merged = Changes.merge(emptyList(), fresh, at)

        assertEquals(Changes.MAX_ENTRIES, merged.size)
        assertEquals("n1", merged.first().id)
    }
}
