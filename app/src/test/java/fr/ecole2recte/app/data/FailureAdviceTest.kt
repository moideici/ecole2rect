package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.StaleCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class FailureAdviceTest {
    @Test
    fun `chaque cas a un titre et une action utile`() {
        assertEquals("Mot de passe refusé", FailureAdvice.forKind(FailureKind.InvalidCredentials).title)
        assertEquals(AdviceAction.Reconnect, FailureAdvice.forKind(FailureKind.InvalidCredentials).action)
        assertEquals("Compte verrouillé", FailureAdvice.forKind(FailureKind.Blocked).title)
        assertEquals(AdviceAction.None, FailureAdvice.forKind(FailureKind.Blocked).action)
        assertEquals("ÉcoleDirecte indisponible", FailureAdvice.forKind(FailureKind.Service).title)
        assertEquals("Pas de réseau", FailureAdvice.forKind(FailureKind.Network).title)
        assertEquals("Version d'API refusée (517)", FailureAdvice.forKind(FailureKind.StaleVersion).title)
        assertEquals(AdviceAction.OpenDiagnostic, FailureAdvice.forKind(FailureKind.StaleVersion).action)
        assertEquals("Vérification de sécurité requise", FailureAdvice.forKind(FailureKind.SecurityRefused).title)
        assertEquals(AdviceAction.Reconnect, FailureAdvice.forKind(FailureKind.SecurityRefused).action)
    }

    @Test
    fun `tous les types d'echec donnent des titres differents`() {
        val titles = FailureKind.entries.map { FailureAdvice.forKind(it).title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `l'action de diagnostic se nomme comme dans le menu`() {
        assertEquals("Ouvrir Plus > Diagnostic", AdviceAction.OpenDiagnostic.label)
        assertEquals("Reconnecter", AdviceAction.Reconnect.label)
        assertTrue(FailureAdvice.staleVersion.detail.contains("Plus > Diagnostic"))
    }

    @Test
    fun `une exception est traduite d'apres sa nature`() {
        assertEquals(FailureAdvice.expired, FailureAdvice.forThrowable(SessionExpiredException()))
        assertEquals(FailureAdvice.unusable, FailureAdvice.forThrowable(UnusableResponseException("vide")))
        assertEquals(FailureAdvice.network, FailureAdvice.forThrowable(UnknownHostException("x")))
        assertEquals(FailureAdvice.network, FailureAdvice.forThrowable(IOException()))
        assertEquals(FailureAdvice.generic, FailureAdvice.forThrowable(IllegalStateException("boum")))
        val network = EcoleDirecteException("Connexion réseau impossible.", IOException(), FailureKind.Network)
        assertEquals(FailureAdvice.network, FailureAdvice.forThrowable(network))
        val expired = EcoleDirecteException("La session ÉcoleDirecte a expiré.", SessionExpiredException(), FailureKind.Service)
        assertEquals(FailureAdvice.expired, FailureAdvice.forThrowable(expired))
        assertEquals(FailureAdvice.service, FailureAdvice.forThrowable(EcoleDirecteException("HTTP 503", null, FailureKind.Service)))
    }

    @Test
    fun `un message precis ecrit a l'avance est conserve`() {
        val advice = FailureAdvice.forThrowable(EcoleDirecteException("Les comptes professeur ne sont pas encore pris en charge."))
        assertEquals("Les comptes professeur ne sont pas encore pris en charge.", advice.detail)
        assertEquals(AdviceAction.Retry, advice.action)
    }

    @Test
    fun `chaque cause d'enregistrement a un message`() {
        StaleCause.entries.forEach { cause ->
            assertNotEquals("", FailureAdvice.forStale(cause).title)
            assertNotEquals("", FailureAdvice.forStale(cause).detail)
        }
        assertEquals(FailureAdvice.network, FailureAdvice.forStale(null))
    }

    @Test
    fun `l'ecran de connexion garde le message precis recu`() {
        val refused = FailureAdvice.forLogin(FailureKind.InvalidCredentials, "Identifiant ou mot de passe invalide.")
        assertEquals("Mot de passe refusé", refused.title)
        assertTrue(refused.detail.startsWith("Identifiant ou mot de passe invalide."))
        assertTrue(refused.detail.contains("ne réessaiera pas"))
        val blocked = FailureAdvice.forLogin(FailureKind.Blocked, "Compte bloqué.")
        assertEquals("Compte verrouillé", blocked.title)
        assertTrue(blocked.detail.contains("trop de tentatives"))
        val other = FailureAdvice.forLogin(FailureKind.Other, "Message du serveur")
        assertEquals("Message du serveur", other.detail)
    }
}
