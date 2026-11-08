package fr.ecole2recte.app.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetNavigationTest {
    @Test fun `grades ouvre notes et garde le profil et la matiere`() {
        val destination = WidgetDestination.parse("grades", "compte/enfant", "math")!!
        assertEquals("notes", destination.tab)
        assertEquals("compte/enfant", destination.profile)
        assertEquals("math", destination.subject)
    }

    @Test fun `valeurs invalides ignorees et aucun sujet hors notes`() {
        assertNull(WidgetDestination.parse("inconnu", "", ""))
        assertEquals("", WidgetDestination.parse("agenda", "compte/enfant", "math")!!.subject)
        assertEquals("", WidgetDestination.parse("grades", "", "x".repeat(301))!!.subject)
    }
}
