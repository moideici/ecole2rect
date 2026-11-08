package fr.ecole2recte.app.ui

import fr.ecole2recte.app.sync.EventKind
import fr.ecole2recte.app.ui.navigation.AppTab
import fr.ecole2recte.app.ui.navigation.SEARCH_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Les onglets demandés par une notification ou un raccourci existent tous : un toucher n'ouvre jamais dans le vide. */
class NavigationTargetsTest {
    private val known = AppTab.entries.map { it.route }.toSet() + setOf("agenda_homework", SEARCH_ROUTE)

    private fun file(path: String) = File(path).takeIf { it.exists() } ?: File("app/$path")

    @Test
    fun `chaque famille de notification ouvre un onglet existant`() {
        EventKind.entries.forEach { kind -> assertTrue("${kind.name} -> ${kind.tab}", kind.tab in known) }
    }

    @Test
    fun `les notes ouvrent les notes, les messages les messages, les devoirs les devoirs`() {
        assertEquals("notes", EventKind.Grade.tab)
        assertEquals("messages", EventKind.Message.tab)
        assertEquals("agenda_homework", EventKind.Homework.tab)
        assertEquals("agenda", EventKind.Canceled.tab)
    }

    @Test
    fun `chaque fonctionnalite App Actions vise un onglet existant`() {
        val xml = file("src/main/res/xml/shortcuts.xml").readText()
        val features = Regex("""android:shortcutId="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(listOf("agenda", "devoirs", "notes", "messages"), features)
        val tabs = features.map { fr.ecole2recte.app.domain.DeepLinks.feature(it)!!.tab }
        tabs.forEach { assertTrue(it, it in known) }
        assertTrue(xml.contains("actions.intent.OPEN_APP_FEATURE"))
    }
}
