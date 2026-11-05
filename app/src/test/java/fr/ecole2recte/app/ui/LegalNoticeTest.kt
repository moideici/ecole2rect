package fr.ecole2recte.app.ui

import fr.ecole2recte.app.sync.SyncIntervals
import fr.ecole2recte.app.ui.login.LegalNotice
import fr.ecole2recte.app.ui.login.needsNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegalNoticeTest {
    private val all = LegalNotice.points.joinToString(" ")

    @Test
    fun `l'avis dit que l'application est non officielle et non affiliee`() {
        assertTrue("non officielle" in all)
        assertTrue("affiliée" in all)
    }

    @Test
    fun `l'avis dit a quoi servent les identifiants et qu'ils restent chiffres sur le telephone`() {
        assertTrue("ne servent qu'à te connecter" in all)
        assertTrue("chiffrés sur ce téléphone" in all)
        assertTrue("nulle part ailleurs" in all)
    }

    @Test
    fun `l'avis previent que l'interface peut changer et que le site fait foi`() {
        assertTrue("n'est pas documentée" in all)
        assertTrue("le site fait foi" in all)
    }

    @Test
    fun `l'avis ne s'affiche que devant la saisie d'identifiants et une seule fois`() {
        assertTrue(needsNotice(accepted = false, loginVisible = true))
        assertFalse(needsNotice(accepted = true, loginVisible = true))
        assertFalse(needsNotice(accepted = false, loginVisible = false))
    }

    private fun readme(): String {
        val file = File("../README.md").takeIf { it.exists() } ?: File("README.md")
        return file.readText()
    }

    @Test
    fun `le README annonce une application non officielle et ses limites connues`() {
        val readme = readme()
        assertTrue("Non officielle" in readme)
        assertTrue("## Limites connues" in readme)
        assertTrue("## Tes données" in readme)
    }
}

class SyncIntervalsTest {
    @Test
    fun `jamais de synchronisation plus souvent que toutes les quatre heures`() {
        assertEquals(4, SyncIntervals.clamp(2))
        assertEquals(4, SyncIntervals.clamp(0))
        assertEquals(4, SyncIntervals.clamp(-3))
        assertEquals(8, SyncIntervals.clamp(8))
        assertEquals(24, SyncIntervals.clamp(100))
        assertTrue(SyncIntervals.choices.all { it >= 4 })
        assertTrue(SyncIntervals.DEFAULT_HOURS in SyncIntervals.choices)
    }
}
