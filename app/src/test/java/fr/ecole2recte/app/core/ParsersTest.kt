package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.SchoolLifeKind
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
    fun `une connexion parent donne un profil par enfant`() {
        val accounts = Parsers.accounts(JSONObject(Payloads.LOGIN_FAMILY))

        assertEquals("les enfants sont les profils, pas la famille", 2, accounts.size)
        val first = accounts.first()
        assertEquals("4242", first.id)
        assertEquals("Martin Leroy", first.displayName)
        assertEquals("Terminale A", first.className)
        assertTrue("un enfant reste un profil élève", first.isStudent)
        assertTrue(first.viewedByFamily)
        assertEquals("777", first.familyId)
        assertEquals("Claire Leroy", first.familyName)
        assertEquals("la messagerie est celle de la famille", "777", first.mailboxId)

        val second = accounts.last()
        assertEquals("Léa Leroy", second.displayName)
        assertEquals("le nom de famille manquant est repris du responsable", "Leroy", second.lastName)
        assertEquals("3e B", second.className)
        assertEquals("Collège Jean Moulin", second.schoolName)
    }

    // ── Modules de l'établissement ───────────────────────────────────────────

    @Test
    fun `lit les actualites avec leur contenu et leurs pieces jointes`() {
        val news = Parsers.news(JSONObject(Payloads.NEWS))

        assertEquals(2, news.size)
        val first = news.first { it.id == "12" }
        assertEquals("Sortie au musée", first.title)
        assertEquals("Départ à 8h.\nPrévoir un pique-nique.", first.content)
        assertEquals(LocalDate.of(2026, 9, 4), first.date)
        assertEquals("Mme Bernard", first.author)
        assertEquals("Sortie", first.category)
        assertEquals("autorisation.pdf", first.attachments.single().name)
        assertTrue("une actualité sans corps reste lisible", news.any { it.content.isBlank() })
    }

    @Test
    fun `lit les manuels numeriques et sait lesquels s ouvrent`() {
        val textbooks = Parsers.textbooks(JSONObject(Payloads.TEXTBOOKS))

        assertEquals(2, textbooks.size)
        val maths = textbooks.first { it.subject == "Mathématiques" }
        assertEquals("Maths 3e", maths.title)
        assertEquals("Hatier", maths.publisher)
        assertTrue(maths.hasLink)
        assertFalse(
            "sans URL, la carte ne doit pas se prétendre cliquable",
            textbooks.first { it.subject == "Histoire-Géographie" }.hasLink,
        )
    }

    @Test
    fun `aplatit l arborescence de l espace de stockage`() {
        val files = Parsers.cloudFiles(JSONObject(Payloads.CLOUD))

        assertEquals("seuls les fichiers comptent, pas les dossiers", 2, files.size)
        val chapter = files.first { it.name == "chapitre1.pdf" }
        assertEquals("Documents partagés / Cours", chapter.folder)
        assertEquals(2_500_000L, chapter.size)
        assertEquals(LocalDate.of(2026, 9, 3), chapter.date)
        assertEquals("CLOUD", chapter.asAttachment().apiDownloadType)
    }

    @Test
    fun `lit les messages envoyes avec leurs destinataires et les classeurs`() {
        val payload = JSONObject(Payloads.MESSAGES_SENT)
        val sent = Parsers.messages(payload, MessageFolder.SENT)

        val message = sent.single()
        assertEquals(MessageFolder.SENT, message.folder)
        assertEquals("Absence de mardi", message.subject)
        assertEquals(listOf("Mme Claire Bernard", "Vie scolaire"), message.recipients)
        assertEquals(
            "dans les envoyés, c'est le destinataire qui compte",
            "Mme Claire Bernard, Vie scolaire",
            message.counterpart,
        )
        assertEquals(listOf("7" to "Professeurs"), Parsers.messageFolders(payload))
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
    fun `lit ce que le bulletin dit autour des chiffres`() {
        val payload = Parsers.grades(JSONObject(Payloads.GRADES), today = LocalDate.of(2026, 10, 1))
        val period = payload.periods.single()

        assertEquals("Trimestre solide, participation à encourager.", period.appreciation)
        assertEquals("Mme Bernard", period.headTeacher)
        assertEquals(LocalDate.of(2026, 12, 18), period.councilDate)
        assertEquals("17:30", period.councilTime)
        assertEquals(6.4, period.minimum!!, 0.001)
        assertEquals(18.9, period.maximum!!, 0.001)

        val maths = payload.subjectAverages.single()
        assertEquals(listOf("M. Petit", "Mme Roy"), maths.teachers)
        assertEquals(
            "deux professeurs, deux appréciations : aucune ne doit disparaître",
            "Excellent trimestre.\n\nOral à travailler.",
            maths.appreciation,
        )
        assertEquals(7.0, maths.minimum!!, 0.001)
        assertEquals(19.0, maths.maximum!!, 0.001)

        val best = payload.grades.first { it.subject == "Mathématiques" }
        assertEquals(4.5, best.minimum!!, 0.001)
        assertEquals(19.5, best.maximum!!, 0.001)
        assertEquals("Devoir Surveille", best.kind)
        val skill = best.skills.single()
        assertEquals("Résoudre un problème", skill.label)
        assertEquals("Maitrise Satisfaisante", skill.level)
        assertEquals(3, skill.value)
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
    fun `lit la vie scolaire, absences retards et encouragements melanges`() {
        val events = Parsers.schoolLife(JSONObject(Payloads.SCHOOL_LIFE))

        assertEquals(3, events.size)
        val absence = events.first { it.id == "7001" }
        assertEquals(SchoolLifeKind.ABSENCE, absence.kind)
        assertEquals("Maladie", absence.reason)
        assertFalse(absence.justified)
        assertEquals(SchoolLifeKind.LATE, events.first { it.id == "7002" }.kind)
        assertTrue(events.first { it.id == "7002" }.justified)
        assertEquals(SchoolLifeKind.ENCOURAGEMENT, events.first { it.id == "7003" }.kind)
        // Le plus récent d'abord : c'est l'ordre dans lequel on lit une vie scolaire.
        assertEquals("7002", events.first().id)
    }

    @Test
    fun `lit le contenu de seance du jour`() {
        val sessions = Parsers.sessionContents(JSONObject(Payloads.HOMEWORK_DAY), LocalDate.of(2026, 9, 7))

        assertEquals(1, sessions.size)
        assertEquals("Histoire-Géo", sessions.single().subject)
        assertEquals("Chapitre 2 : la Révolution", sessions.single().content)
    }

    @Test
    fun `lit les documents, toutes categories confondues`() {
        val documents = Parsers.documents(JSONObject(Payloads.DOCUMENTS))

        assertEquals(2, documents.size)
        assertEquals("Bulletin T1.pdf", documents.first().name)
        assertEquals(LocalDate.of(2026, 12, 20), documents.first().date)
        assertTrue(documents.any { it.category.contains("Administratifs", ignoreCase = true) })
    }

    @Test
    fun `une reponse vide ne fait pas tomber le parsing`() {
        assertTrue(Parsers.lessons(JSONObject("{}")).isEmpty())
        assertTrue(Parsers.homeworkSummaries(JSONObject("""{"code":200}""")).isEmpty())
        assertTrue(Parsers.messages(JSONObject("""{"data":{}}""")).isEmpty())
        assertNull(Parsers.messageBody(JSONObject("{}")))
        assertTrue(Parsers.grades(JSONObject("{}")).grades.isEmpty())
        assertTrue(Parsers.schoolLife(JSONObject("{}")).isEmpty())
        assertTrue(Parsers.documents(JSONObject("""{"data":{}}""")).isEmpty())
        assertTrue(Parsers.sessionContents(JSONObject("{}"), LocalDate.of(2026, 9, 7)).isEmpty())
    }
}
