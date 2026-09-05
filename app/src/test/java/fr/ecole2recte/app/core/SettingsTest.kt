package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.core.settings.HomeWidgetSetting
import fr.ecole2recte.app.core.settings.SubjectPalette
import fr.ecole2recte.app.core.settings.ThemeChoice
import fr.ecole2recte.app.core.store.SettingsCodec
import fr.ecole2recte.app.core.store.SnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SettingsTest {

    @Test
    fun `l accueil se reorganise et se masque`() {
        val settings = AppSettings()
            .move(HomeWidget.GRADES, -1)
            .toggleWidget(HomeWidget.MESSAGES)

        assertEquals(HomeWidget.GRADES, settings.orderedWidgets[3].widget)
        assertFalse(HomeWidget.MESSAGES in settings.visibleWidgets)
        assertTrue(HomeWidget.NEXT_CLASS in settings.visibleWidgets)
    }

    @Test
    fun `un widget ajoute par une mise a jour apparait quand meme`() {
        val ancien = AppSettings(homeWidgets = listOf(HomeWidgetSetting(HomeWidget.NEXT_CLASS)))

        assertEquals(HomeWidget.DEFAULT_ORDER.size, ancien.orderedWidgets.size)
        assertEquals(HomeWidget.NEXT_CLASS, ancien.orderedWidgets.first().widget)
    }

    @Test
    fun `une matiere garde la meme couleur partout`() {
        val couleur = SubjectPalette.defaultColor("Mathématiques")

        assertEquals(couleur, SubjectPalette.defaultColor("MATHEMATIQUES"))
        assertTrue(couleur in SubjectPalette.COLORS)

        val settings = AppSettings().withSubjectColor("Mathématiques", 0xFF112233)
        assertEquals(0xFF112233, SubjectPalette.colorFor("MATHEMATIQUES", settings.subjectColors))
        // Une matière sans choix explicite garde sa couleur dérivée du nom.
        assertEquals(SubjectPalette.defaultColor("Anglais"), SubjectPalette.colorFor("Anglais", settings.subjectColors))
    }

    @Test
    fun `l etat local d un devoir prime sur celui du serveur`() {
        val settings = AppSettings().withHomeworkDone("901", true)

        assertTrue(settings.isHomeworkDone("901", remoteDone = false))
        assertFalse(settings.withHomeworkDone("901", false).isHomeworkDone("901", remoteDone = true))
        assertTrue(settings.isHomeworkDone("902", remoteDone = true))
    }

    @Test
    fun `les reglages font l aller-retour, et une valeur inconnue ne casse rien`() {
        val settings = AppSettings(
            theme = ThemeChoice.DARK,
            subjectColors = mapOf("maths" to 0xFF112233),
            doneHomework = setOf("901"),
            readMessages = setOf("3001"),
            selectedAccountId = "4242",
            showDiagnostics = true,
        ).move(HomeWidget.MESSAGES, -2)

        val restored = SettingsCodec.decode(SettingsCodec.encode(settings))

        assertEquals(settings.theme, restored.theme)
        assertEquals(settings.subjectColors, restored.subjectColors)
        assertEquals(settings.orderedWidgets, restored.orderedWidgets)
        assertEquals(settings.doneHomework, restored.doneHomework)
        assertEquals("4242", restored.selectedAccountId)
        assertTrue(restored.showDiagnostics)

        // Un fichier corrompu redonne les réglages par défaut, jamais une exception.
        assertEquals(AppSettings(), SettingsCodec.decode("ceci n'est pas du JSON"))
        assertEquals(ThemeChoice.SYSTEM, SettingsCodec.decode("""{"theme":"NEON"}""").theme)
    }

    @Test
    fun `le cache fait l aller-retour et ignore une version inconnue`() {
        val snapshot = SchoolSnapshot(
            homework = listOf(HomeworkItem("901", "Maths", LocalDate.of(2026, 9, 7), content = "Exercices")),
            syncedAt = mapOf(DataSection.HOMEWORK to 1_700_000_000_000L),
        )

        val restored = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))

        assertNotNull(restored)
        assertEquals(snapshot.homework, restored!!.homework)
        assertEquals(snapshot.syncedAt, restored.syncedAt)
        assertNull(SnapshotCodec.decode("""{"version":999}"""))
        assertNull(SnapshotCodec.decode(null))
    }
}
