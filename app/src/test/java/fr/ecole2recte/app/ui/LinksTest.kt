package fr.ecole2recte.app.ui

import fr.ecole2recte.app.ui.common.findLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    private fun urls(text: String) = findLinks(text).map { it.url }

    @Test
    fun `une adresse web est reperee sans la ponctuation finale`() {
        assertEquals(listOf("https://exemple.fr/devoir?id=3"), urls("À lire : https://exemple.fr/devoir?id=3."))
        assertEquals(listOf("https://exemple.fr/a"), urls("(voir https://exemple.fr/a)"))
    }

    @Test
    fun `une adresse en www reçoit https`() {
        assertEquals(listOf("https://www.lemonde.fr"), urls("Lire www.lemonde.fr demain"))
    }

    @Test
    fun `un courriel et un numero de telephone sont reperes`() {
        assertEquals(
            listOf("mailto:prof@college.fr", "tel:0612345678"),
            urls("Écrire à prof@college.fr ou appeler le 06 12 34 56 78."),
        )
        assertEquals(listOf("tel:+33612345678"), urls("Appelez +33 6 12 34 56 78"))
    }

    @Test
    fun `l'adresse d'un courriel n'est pas prise pour une adresse web`() {
        assertEquals(listOf("mailto:a@b.fr"), urls("a@b.fr"))
    }

    @Test
    fun `un texte sans lien n'en a pas`() {
        assertEquals(emptyList<String>(), urls("Exercices 12 à 18 page 45, pour le 10/11."))
    }

    @Test
    fun `les positions couvrent exactement le lien`() {
        val text = "Voir https://a.fr/x maintenant"
        val link = findLinks(text).single()
        assertEquals("https://a.fr/x", text.substring(link.start, link.end))
    }
}
