package fr.ecole2recte.app.ui

import fr.ecole2recte.app.domain.StaleCause
import fr.ecole2recte.app.ui.common.staleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleTextTest {
    @Test
    fun `sans cause connue ou faute de reseau on dit hors ligne`() {
        assertEquals("Hors ligne · données enregistrées jeudi 5 novembre à 08:00", staleText("jeudi 5 novembre à 08:00", null))
        assertEquals("Hors ligne · données enregistrées", staleText(null, StaleCause.Network))
    }

    @Test
    fun `les autres causes sont nommees au lieu d'un faux hors ligne`() {
        assertEquals("Compte bloqué par ÉcoleDirecte · données enregistrées lundi", staleText("lundi", StaleCause.Blocked))
        assertTrue(staleText("lundi", StaleCause.StaleVersion).startsWith("Version d'API refusée (517)"))
        assertTrue(staleText(null, StaleCause.Unusable).contains("inexploitable"))
    }
}
