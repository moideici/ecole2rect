package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.sync.WidgetSnapshot
import org.junit.Assert.*
import org.junit.Test

class GoalsWidgetTest {
    private val subjects = listOf(
        WidgetSubjectMean("math", "Maths", WidgetMean(12.0)),
        WidgetSubjectMean("anglais", "Anglais", WidgetMean(16.0)),
        WidgetSubjectMean("francais", "Français", WidgetMean(10.0)),
        WidgetSubjectMean("absent", "Sans note", WidgetMean()),
    )

    @Test fun `jauge bornee sans objectif et objectif depasse`() {
        assertEquals(80, GoalWidgetData.progress(12.0, 15.0))
        assertEquals(100, GoalWidgetData.progress(16.0, 14.0))
        assertEquals(0, GoalWidgetData.progress(0.0, 15.0))
        assertNull(GoalWidgetData.progress(12.0, null))
        assertNull(GoalWidgetData.progress(null, 15.0))
        assertNull(GoalWidgetData.progress(12.0, 0.0))
        assertNull(GoalWidgetData.progress(Double.NaN, 15.0))
    }

    @Test fun `tri par ecart les plus loin de leur objectif en premier puis absents`() {
        val rows = GoalWidgetData.rows(subjects, mapOf("math" to 15.0, "anglais" to 14.0, "francais" to 16.0), 2, PrivacyMode())
        assertEquals(listOf("francais", "math", "anglais", "absent"), rows.map { it.id })
        assertEquals("10,00 / objectif 16,00", rows.first().text)
        assertEquals("10,00 → 16,00", rows.first().compactText)
        assertEquals("– / objectif à définir", rows.last().text)
    }

    @Test fun `vie privee aucune valeur pourcentage ou ordre de moyenne`() {
        val hidden = GoalWidgetData.rows(subjects, mapOf("math" to 15.0, "francais" to 16.0), 1, PrivacyMode(true))
        assertTrue(hidden.all { it.text == MASKED && it.percent == null && it.gap == null })
        assertEquals(subjects.map { it.id }, hidden.map { it.id })
    }

    @Test fun `stockage par profil et matiere reprise suppression et validation`() {
        val storage = WidgetTestStorage()
        val store = GoalStore(storage)
        assertTrue(store.set("compte/enfant1", "math", 15.5))
        assertTrue(store.set("compte/enfant2", "math", 12.0))
        assertEquals(15.5, GoalStore(storage).values("compte/enfant1")["math"]!!, 0.0)
        assertEquals(12.0, store.values("compte/enfant2")["math"]!!, 0.0)
        assertFalse(store.set("compte/enfant1", "math", 21.0))
        assertFalse(store.set("compte/enfant1", "math", Double.NaN))
        store.set("compte/enfant1", "math", null)
        assertTrue(store.values("compte/enfant1").isEmpty())
        store.clearAccount("compte")
        assertTrue(store.values("compte/enfant2").isEmpty())
    }

    @Test fun `palier deux par deux plafonne a quatre et grandes tailles en montrent plus`() {
        assertEquals(4, GoalWidgetData.capacity(180f, 180f, false, 1f))
        assertEquals(1, GoalWidgetData.capacity(110f, 110f, false, 1f))
        assertTrue(GoalWidgetData.capacity(320f, 440f, false, 1f) > 4)
        assertTrue(GoalWidgetData.capacity(600f, 840f, false, 1.3f) > 4)
        assertTrue(GoalWidgetData.capacity(840f, 600f, true, 1.3f) > 4)
        assertTrue(GoalWidgetData.capacity(180f, 180f, false, 2f) < 4)
    }

    @Test fun `tuile verouillee ou masquee aucune moyenne et decimales globales`() {
        val snapshot = WidgetSnapshot(1, null, emptyList(), meanPeriods = listOf(WidgetMeanPeriod("T1", "Trimestre", true, WidgetMean(15.256, AverageSource.Official))))
        assertEquals("Verrouillé", MeanTileData.label(snapshot, 2, PrivacyMode(), true))
        assertEquals(MASKED, MeanTileData.label(snapshot, 2, PrivacyMode(true), false))
        assertEquals("15,3 /20", MeanTileData.label(snapshot, 1, PrivacyMode(), false))
        assertEquals("–", MeanTileData.label(null, 2, PrivacyMode(), false))
    }
}
