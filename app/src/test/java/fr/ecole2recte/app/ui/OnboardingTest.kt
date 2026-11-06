package fr.ecole2recte.app.ui

import fr.ecole2recte.app.ui.login.Onboarding
import fr.ecole2recte.app.ui.login.needsOnboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    @Test
    fun `la presentation a trois pages renseignees`() {
        assertEquals(3, Onboarding.pageCount)
        Onboarding.pages.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.lead.isNotBlank())
            assertTrue(it.points.size >= 3)
        }
        assertEquals(Onboarding.pages.map { it.title }.toSet().size, Onboarding.pageCount)
    }

    @Test
    fun `les trois themes demandes sont couverts`() {
        val text = Onboarding.pages.joinToString(" ") { it.title + it.lead + it.points.joinToString(" ") }.lowercase()
        listOf("accueil", "agenda", "notes", "messages").forEach { assertTrue(it, text.contains(it)) }
        listOf("notifications", "widgets", "exports", "réglages").forEach { assertTrue(it, text.contains(it)) }
        listOf("signal", "vie privée", "chiffrés").forEach { assertTrue(it, text.contains(it)) }
    }

    @Test
    fun `elle ne s'affiche qu'une fois, apres l'avis legal, devant la connexion`() {
        assertTrue(needsOnboarding(noticeAccepted = true, seen = false, loginVisible = true))
        assertFalse(needsOnboarding(noticeAccepted = false, seen = false, loginVisible = true)) // l'avis d'abord
        assertFalse(needsOnboarding(noticeAccepted = true, seen = true, loginVisible = true))
        assertFalse(needsOnboarding(noticeAccepted = true, seen = false, loginVisible = false))
    }

    @Test
    fun `suivant avance puis termine`() {
        assertEquals(1, Onboarding.next(0))
        assertEquals(2, Onboarding.next(1))
        assertNull(Onboarding.next(2))
        assertFalse(Onboarding.isLast(1))
        assertTrue(Onboarding.isLast(2))
    }

    @Test
    fun `chaque page se decrit pour un lecteur d'ecran`() {
        assertEquals("Page 1 sur 3 : L'essentiel d'un coup d'œil", Onboarding.description(0))
        assertEquals("Passer", Onboarding.SKIP)
    }
}
