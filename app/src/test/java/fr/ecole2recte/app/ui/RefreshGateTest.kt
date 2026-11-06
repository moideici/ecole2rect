package fr.ecole2recte.app.ui

import fr.ecole2recte.app.ui.common.MIN_REFRESH_GAP_MS
import fr.ecole2recte.app.ui.common.RefreshGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshGateTest {
    @Test
    fun `un tirage moins de 90 secondes apres la lecture ne relit pas`() {
        assertTrue(RefreshGate.isTooSoon(loadedAt = 1_000_000, now = 1_000_000 + 20_000))
        assertTrue(RefreshGate.isTooSoon(loadedAt = 1_000_000, now = 1_000_000 + MIN_REFRESH_GAP_MS - 1))
    }

    @Test
    fun `apres 90 secondes ou sans lecture on relit`() {
        assertFalse(RefreshGate.isTooSoon(loadedAt = 1_000_000, now = 1_000_000 + MIN_REFRESH_GAP_MS))
        assertFalse(RefreshGate.isTooSoon(loadedAt = 0, now = 5_000))
    }

    @Test
    fun `une horloge qui recule ne bloque pas`() {
        assertFalse(RefreshGate.isTooSoon(loadedAt = 2_000_000, now = 1_000_000))
    }

    @Test
    fun `le message dit l'age et l'attente`() {
        assertEquals(
            "Déjà à jour : lu il y a 20 s. Réessaie dans 70 s, pour ne pas solliciter ÉcoleDirecte pour rien.",
            RefreshGate.tooSoonMessage(loadedAt = 1, now = 1 + 20_000),
        )
        assertTrue(RefreshGate.tooSoonMessage(1, 1 + 89_500).contains("Réessaie dans 1 s"))
        assertEquals(90_000L, MIN_REFRESH_GAP_MS)
    }
}
