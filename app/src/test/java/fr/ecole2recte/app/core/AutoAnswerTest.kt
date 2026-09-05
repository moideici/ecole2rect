package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.auth.AnswerMemory
import fr.ecole2recte.app.core.auth.AutoAnswer
import fr.ecole2recte.app.core.auth.BirthDates
import fr.ecole2recte.app.core.auth.SchoolYear
import fr.ecole2recte.app.core.auth.SecurityAnswer
import fr.ecole2recte.app.core.protocol.TrustedAnswer
import fr.ecole2recte.app.core.store.CredentialsCodec
import fr.ecole2recte.app.core.store.SavedCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Les garde-fous de la réponse automatique.
 *
 * Ces tests disent ce que l'application s'autorise : reconnaître une date saisie, rejouer
 * une réponse validée, et rien d'autre. Ils disent surtout ce qu'elle s'interdit — deviner,
 * approcher, et croire éternelle une réponse qui change à chaque rentrée.
 */
class AutoAnswerTest {

    private val today = LocalDate.of(2026, 9, 7)
    private val choices = listOf("Mars", "Juin", "Septembre")

    @Test
    fun `une proposition correspondant au mois de naissance est retenue`() {
        val decision = AutoAnswer.decide(
            question = "Quel est ton mois de naissance ?",
            choices = choices,
            birthDates = BirthDates.of(SecurityAnswer.BirthDate(month = "6")),
            memory = AnswerMemory(),
            today = today,
        )

        assertEquals("Juin", decision?.answer)
        assertEquals(AutoAnswer.Source.BIRTH_DATE, decision?.source)
    }

    @Test
    fun `sans date saisie, rien n est proposé`() {
        assertNull(
            AutoAnswer.decide("Quel est ton mois de naissance ?", choices, BirthDates(), AnswerMemory(), today),
        )
    }

    @Test
    fun `une date qui ne correspond à aucune proposition ne déclenche rien`() {
        val decision = AutoAnswer.decide(
            question = "Quel est ton mois de naissance ?",
            choices = choices,
            birthDates = BirthDates.of(SecurityAnswer.BirthDate(month = "1")),
            memory = AnswerMemory(),
            today = today,
        )

        assertNull("aucune proposition ne vaut janvier : la question doit être posée", decision)
    }

    @Test
    fun `une question sur un enfant nommé utilise la date de cet enfant`() {
        val dates = BirthDates()
            .with("Martin", SecurityAnswer.BirthDate("4", "3", "2011"))
            .with("Léa", SecurityAnswer.BirthDate("12", "5", "2010"))

        val decision = AutoAnswer.decide(
            question = "Quelle est la date de naissance de votre enfant Léa ?",
            choices = listOf("04/03/2011", "12/05/2010", "30/11/2012"),
            birthDates = dates,
            memory = AnswerMemory(),
            today = today,
        )

        assertEquals("12/05/2010", decision?.answer)
    }

    @Test
    fun `un prénom n est pas reconnu au milieu d un autre`() {
        val dates = BirthDates()
            .with("Anne", SecurityAnswer.BirthDate("4", "3", "2011"))
            .with("Marianne", SecurityAnswer.BirthDate("12", "5", "2010"))

        val decision = AutoAnswer.decide(
            question = "Quelle est la date de naissance de votre enfant Marianne ?",
            choices = listOf("04/03/2011", "12/05/2010"),
            birthDates = dates,
            memory = AnswerMemory(),
            today = today,
        )

        assertEquals("la date d'Anne ne doit pas répondre pour Marianne", "12/05/2010", decision?.answer)
    }

    @Test
    fun `une date complète est reconnue quelle que soit son écriture`() {
        val dates = BirthDates.of(SecurityAnswer.BirthDate("5", "9", "2009"))

        listOf("05/09/2009", "5-9-2009", "2009-09-05", "5 septembre 2009").forEach { written ->
            val decision = AutoAnswer.decide(
                question = "Quelle est ta date de naissance ?",
                choices = listOf("01/01/2000", written),
                birthDates = dates,
                memory = AnswerMemory(),
                today = today,
            )
            assertEquals("« $written » doit être reconnu", written, decision?.answer)
        }
    }

    @Test
    fun `une réponse validée est rejouée telle que le serveur l écrit`() {
        val memory = AnswerMemory().remembering("En quelle classe es-tu ?", "Terminale A", today)

        val decision = AutoAnswer.decide(
            question = "En quelle classe es-tu ?",
            choices = listOf("Première S", "Terminale A", "Seconde 4"),
            birthDates = BirthDates(),
            memory = memory,
            today = today,
        )

        assertEquals("Terminale A", decision?.answer)
        assertEquals(AutoAnswer.Source.REMEMBERED, decision?.source)
    }

    @Test
    fun `une réponse mémorisée absente des propositions n est pas envoyée`() {
        val memory = AnswerMemory().remembering("En quelle classe es-tu ?", "Terminale A", today)

        assertNull(
            "la classe a changé : mieux vaut reposer la question que forcer une réponse fausse",
            AutoAnswer.decide(
                question = "En quelle classe es-tu ?",
                choices = listOf("Première S", "Seconde 4"),
                birthDates = BirthDates(),
                memory = memory,
                today = today,
            ),
        )
    }

    @Test
    fun `une réponse mémorisée expire à la rentrée suivante`() {
        val memory = AnswerMemory().remembering("En quelle classe es-tu ?", "Terminale A", today)
        val entry = memory.answers.single()

        assertEquals(LocalDate.of(2027, 8, 31), entry.validUntil)
        assertTrue(entry.isValid(LocalDate.of(2027, 8, 31)))
        assertNull(
            memory.answerFor("En quelle classe es-tu ?", listOf("Terminale A"), LocalDate.of(2027, 9, 1)),
        )
        assertTrue(memory.purged(LocalDate.of(2027, 9, 1)).answers.isEmpty())
    }

    @Test
    fun `la rentrée de reference est le 1er septembre le plus proche`() {
        assertEquals(LocalDate.of(2026, 9, 1), SchoolYear.nextRollover(LocalDate.of(2026, 6, 30)))
        assertEquals(LocalDate.of(2027, 9, 1), SchoolYear.nextRollover(LocalDate.of(2026, 9, 1)))
        assertEquals(LocalDate.of(2027, 8, 31), SchoolYear.lastDayOfYear(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun `oublier une réponse la retire de la mémoire`() {
        val memory = AnswerMemory()
            .remembering("En quelle classe es-tu ?", "Terminale A", today)
            .forgetting("En quelle classe es-tu ?")

        assertTrue(memory.answers.isEmpty())
    }

    @Test
    fun `mémoriser deux fois la même question ne garde que la dernière réponse`() {
        val memory = AnswerMemory()
            .remembering("En quelle classe es-tu ?", "Première S", today)
            .remembering("En quelle classe es-tu ?", "Terminale A", today)

        assertEquals(1, memory.answers.size)
        assertEquals("Terminale A", memory.answers.single().answer)
    }

    @Test
    fun `le trousseau enregistré se relit entièrement`() {
        val credentials = SavedCredentials(
            identifier = "parent",
            password = "secret",
            trustedAnswer = TrustedAnswer("cn", "cv"),
            birthDates = BirthDates().with("Léa", SecurityAnswer.BirthDate("12", "5", "2010")),
            answers = AnswerMemory().remembering("En quelle classe est votre enfant ?", "3e B", today),
        )

        val decoded = CredentialsCodec.decode(CredentialsCodec.encode(credentials))

        assertEquals(credentials, decoded)
        assertEquals(
            "3e B",
            decoded?.answers?.answerFor("En quelle classe est votre enfant ?", listOf("3e B"), today),
        )
    }

    @Test
    fun `un trousseau écrit par une version plus ancienne reste lisible`() {
        val decoded = CredentialsCodec.decode("""{"identifier":"eleve","password":"secret"}""")

        assertEquals("eleve", decoded?.identifier)
        assertTrue(decoded!!.birthDates.isEmpty)
        assertTrue(decoded.answers.answers.isEmpty())
    }
}
