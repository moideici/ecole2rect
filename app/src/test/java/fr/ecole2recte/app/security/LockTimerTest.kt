package fr.ecole2recte.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimerTest {
    private var now = 1_000_000L
    private val timer = LockTimer { now }

    private fun away(ms: Long, delay: LockDelay, enabled: Boolean = true): Boolean {
        timer.onBackground()
        now += ms
        return timer.onForeground(enabled, delay)
    }

    @Test
    fun `immediatement verrouille au moindre retour`() {
        assertTrue(away(0, LockDelay.Immediate))
        assertTrue(away(500, LockDelay.Immediate))
    }

    @Test
    fun `une minute laisse passer une absence plus courte`() {
        assertFalse(away(59_999, LockDelay.OneMinute))
        assertTrue(away(60_000, LockDelay.OneMinute))
        assertTrue(away(10 * 60_000, LockDelay.OneMinute))
    }

    @Test
    fun `cinq et quinze minutes`() {
        assertFalse(away(4 * 60_000, LockDelay.FiveMinutes))
        assertTrue(away(5 * 60_000, LockDelay.FiveMinutes))
        assertFalse(away(14 * 60_000, LockDelay.FifteenMinutes))
        assertTrue(away(15 * 60_000, LockDelay.FifteenMinutes))
    }

    @Test
    fun `sans verrou active on ne verrouille jamais`() {
        assertFalse(away(3_600_000, LockDelay.Immediate, enabled = false))
    }

    @Test
    fun `un retour sans depart connu ne verrouille pas`() {
        assertFalse(timer.onForeground(true, LockDelay.Immediate))
    }

    @Test
    fun `une seule decision par absence`() {
        timer.onBackground()
        now += 120_000
        assertTrue(timer.onForeground(true, LockDelay.OneMinute))
        assertFalse(timer.onForeground(true, LockDelay.OneMinute))
    }

    @Test
    fun `l'ecran d'authentification qui passe devant ne reverrouille pas`() {
        timer.beginAuthentication()
        timer.onBackground() // le code de l'appareil prend l'écran
        now += 5_000
        assertFalse(timer.onForeground(true, LockDelay.Immediate))
        timer.endAuthentication()
        // Et l'absence d'avant l'authentification ne compte plus.
        now += 5_000
        assertFalse(timer.onForeground(true, LockDelay.Immediate))
    }

    @Test
    fun `l'horloge qui avance entre deux absences ne s'accumule pas`() {
        assertFalse(away(30_000, LockDelay.OneMinute))
        assertFalse(away(30_000, LockDelay.OneMinute)) // 2 x 30 s, mais deux absences distinctes
    }

    @Test
    fun `le delai par defaut est une minute et les valeurs inconnues y reviennent`() {
        assertEquals(LockDelay.OneMinute, LockDelay.Default)
        assertEquals(LockDelay.OneMinute, LockDelay.parse(null))
        assertEquals(LockDelay.OneMinute, LockDelay.parse("zzz"))
        assertEquals(LockDelay.FifteenMinutes, LockDelay.parse("FifteenMinutes"))
        assertEquals(listOf(0L, 60_000L, 300_000L, 900_000L), LockDelay.entries.map { it.millis })
    }
}
