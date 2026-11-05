package fr.ecole2recte.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirthAnswerMatcherTest {
    private val months = listOf("Janvier", "Février", "Mars", "Avril")

    @Test
    fun `retrouve le mois par son numero meme avec des accents`() {
        val answer = findBirthAnswer("Quel est ton mois de naissance ?", months, day = "", month = "2", year = "")
        assertEquals("Février", answer)
    }

    @Test
    fun `retrouve l'annee`() {
        val answer = findBirthAnswer("En quelle année es-tu né ?", listOf("2008", "2009"), day = "", month = "", year = "2009")
        assertEquals("2009", answer)
    }

    @Test
    fun `retrouve le jour avec un zero devant`() {
        val answer = findBirthAnswer("Quel est ton jour de naissance ?", listOf("3", "15"), day = "03", month = "", year = "")
        assertEquals("3", answer)
    }

    @Test
    fun `ne devine pas quand rien ne correspond`() {
        assertNull(findBirthAnswer("Quel est ton mois de naissance ?", months, day = "", month = "11", year = ""))
        assertNull(findBirthAnswer("Quelle est ta couleur préférée ?", months, day = "1", month = "1", year = "2000"))
        assertNull(findBirthAnswer(null, months, day = "1", month = "1", year = "2000"))
    }
}
