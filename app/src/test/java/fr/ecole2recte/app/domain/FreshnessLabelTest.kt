package fr.ecole2recte.app.domain

import fr.ecole2recte.app.data.AdviceAction
import fr.ecole2recte.app.data.FailureAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FreshnessLabelTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun `l'ancien est dit en relatif`() {
        assertEquals("à l'instant", FreshnessLabel.relative(20_000))
        assertEquals("il y a 12 min", FreshnessLabel.relative(12 * 60_000L))
        assertEquals("il y a 3 h", FreshnessLabel.relative(3 * 3_600_000L + 40 * 60_000L))
        assertEquals("il y a 1 jour", FreshnessLabel.relative(30 * 3_600_000L))
        assertEquals("il y a 3 jours", FreshnessLabel.relative(3 * 24 * 3_600_000L))
        assertEquals("à l'instant", FreshnessLabel.relative(-5))
    }

    @Test
    fun `l'heure est dite sans minutes quand elles sont nulles`() {
        assertEquals("18 h", FreshnessLabel.clock(18, 0))
        assertEquals("18 h 05", FreshnessLabel.clock(18, 5))
    }

    @Test
    fun `des donnees de plusieurs jours donnent la date`() {
        val text = FreshnessLabel.describe(at(5, 18), at(8, 10), paris)
        assertEquals("Données du 5 nov. à 18 h (il y a 2 jours)", text)
    }

    @Test
    fun `des donnees du jour ou de la veille le disent`() {
        assertEquals("Données d'aujourd'hui à 15 h 30 (il y a 3 h)", FreshnessLabel.describe(at(6, 15, 30), at(6, 18, 40), paris))
        assertEquals("Données d'hier à 22 h (il y a 14 h)", FreshnessLabel.describe(at(5, 22), at(6, 12), paris))
    }

    @Test
    fun `sans date on le dit`() {
        assertEquals("Données enregistrées (date inconnue)", FreshnessLabel.describe(null, at(6, 12), paris))
        assertEquals("Données enregistrées (date inconnue)", FreshnessLabel.describe(0, at(6, 12), paris))
    }

    @Test
    fun `le bandeau dit hors ligne seulement pour le reseau`() {
        val now = at(6, 12)
        val offline = FailureAdvice.banner(Freshness(at(5, 18), stale = true, cause = StaleCause.Network), now, paris)
        assertEquals("Hors ligne", offline.title)
        assertEquals("Données d'hier à 18 h (il y a 18 h)", offline.detail)
        assertEquals(AdviceAction.Retry, offline.action)

        val blocked = FailureAdvice.banner(Freshness(at(5, 18), stale = true, cause = StaleCause.Blocked), now, paris)
        assertEquals("Compte verrouillé", blocked.title)
        assertEquals(AdviceAction.None, blocked.action)

        assertEquals("Hors ligne", FailureAdvice.banner(Freshness(null, stale = true), now, paris).title)
        assertTrue(FailureAdvice.banner(Freshness(null, stale = true), now, paris).detail.contains("date inconnue"))
    }
}
