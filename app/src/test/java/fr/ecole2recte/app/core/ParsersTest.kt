package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.parse.Parsers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParsersTest {

    @Test
    fun `lit les comptes de la reponse de connexion`() {
        val accounts = Parsers.accounts(JSONObject(Payloads.LOGIN_SUCCESS))

        assertEquals(1, accounts.size)
        val account = accounts.single()
        assertEquals("4242", account.id)
        assertEquals("Martin Leroy", account.displayName)
        assertEquals("Terminale A", account.className)
        assertEquals("Lycée Victor Hugo", account.schoolName)
        assertTrue(account.isStudent)
    }

    @Test
    fun `ignore un cours sans horaire plutot que de vider la journee`() {
        val lessons = Parsers.lessons(JSONObject(Payloads.TIMETABLE))

        assertEquals(2, lessons.size)
        val first = lessons.first()
        assertEquals("Philosophie", first.subject)
        assertEquals("B204", first.room)
        assertEquals(10, first.start.hour)
        assertEquals(12, first.end.hour)
        assertFalse(first.canceled)
        assertTrue(lessons[1].canceled)
    }

    @Test
    fun `lit la liste du cahier de textes avec ses dates`() {
        val homework = Parsers.homeworkSummaries(JSONObject(Payloads.HOMEWORK_LIST))

        assertEquals(2, homework.size)
        assertEquals(LocalDate.of(2026, 9, 7), homework[0].dueDate)
        assertEquals("Philosophie", homework[0].subject)
        assertEquals(LocalDate.of(2026, 9, 1), homework[0].givenOn)
        assertFalse(homework[0].done)
        assertTrue(homework[1].done)
        assertTrue(homework[1].exam)
        // La liste courte ne porte pas les contenus : c'est le détail du jour qui les donne.
        assertFalse(homework[0].hasContent)
    }

    @Test
    fun `decode le contenu HTML base64 du detail d un jour`() {
        val homework = Parsers.homeworkForDate(JSONObject(Payloads.HOMEWORK_DAY), LocalDate.of(2026, 9, 7))

        // L'entrée sans « aFaire » est un contenu de séance, pas un devoir.
        assertEquals(1, homework.size)
        val item = homework.single()
        assertEquals("901", item.id)
        assertEquals("Dissertation : la liberté\n• Introduction", item.content)
        assertEquals(1, item.attachments.size)
        assertEquals("sujet.pdf", item.attachments.single().name)
    }

    @Test
    fun `lit les notes, les periodes et les moyennes par matiere`() {
        val payload = Parsers.grades(JSONObject(Payloads.GRADES), today = LocalDate.of(2026, 10, 1))

        assertEquals(1, payload.periods.size)
        val period = payload.periods.single()
        assertEquals("Trimestre 1", period.name)
        assertEquals(15.8, period.average!!, 0.001)
        assertTrue("la période en cours doit être détectée", period.current)

        // Les regroupements de matières ne sont pas des matières.
        assertEquals(1, payload.subjectAverages.size)
        assertEquals("Mathématiques", payload.subjectAverages.single().subject)
        assertEquals("M. Petit", payload.subjectAverages.single().teacher)

        assertEquals(2, payload.grades.size)
        val best = payload.grades.first { it.subject == "Mathématiques" }
        assertEquals(17.5, best.value!!, 0.001)
        assertEquals(20.0, best.scale, 0.001)
        assertEquals(2.0, best.coefficient, 0.001)
        assertTrue(best.counted)

        val philosophy = payload.grades.first { it.subject == "Philosophie" }
        assertEquals(10.0, philosophy.scale, 0.001)
        assertEquals(14.0, philosophy.outOfTwenty!!, 0.001)
        assertFalse("nonSignificatif ne doit pas compter dans les moyennes", philosophy.counted)
    }

    @Test
    fun `lit la liste des messages et le corps d un message`() {
        val messages = Parsers.messages(JSONObject(Payloads.MESSAGES))

        assertEquals(2, messages.size)
        val first = messages.first()
        assertEquals("Réunion parents-professeurs", first.subject)
        assertEquals("Mme Claire Legrand", first.sender)
        assertFalse(first.read)
        assertFalse(first.hasBody)

        val detailed = Parsers.messageBody(JSONObject(Payloads.MESSAGE_BODY))!!
        assertEquals("Bonjour,\nLa réunion aura lieu jeudi.", detailed.body)
        assertEquals(1, detailed.attachments.size)
    }

    @Test
    fun `une reponse vide ne fait pas tomber le parsing`() {
        assertTrue(Parsers.lessons(JSONObject("{}")).isEmpty())
        assertTrue(Parsers.homeworkSummaries(JSONObject("""{"code":200}""")).isEmpty())
        assertTrue(Parsers.messages(JSONObject("""{"data":{}}""")).isEmpty())
        assertNull(Parsers.messageBody(JSONObject("{}")))
        assertTrue(Parsers.grades(JSONObject("{}")).grades.isEmpty())
    }
}
