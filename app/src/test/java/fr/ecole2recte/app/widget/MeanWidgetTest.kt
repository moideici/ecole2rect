package fr.ecole2recte.app.widget

import fr.ecole2recte.app.data.TextStorage
import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.sync.WidgetSnapshot
import org.junit.Assert.*
import org.junit.Test

class MeanWidgetTest {
    private val subject = WidgetSubjectMean("math", "Mathématiques", WidgetMean(15.256, AverageSource.Official))
    private val previous = WidgetMeanPeriod("T1", "Premier trimestre", false, WidgetMean(13.0, AverageSource.Official), 12.0, listOf(subject.copy(mean = WidgetMean(14.0, AverageSource.Official))))
    private val current = WidgetMeanPeriod("T2", "Deuxième trimestre", true, WidgetMean(14.26, AverageSource.Recomputed, 13.0), 12.5, listOf(subject))
    private val snapshot = WidgetSnapshot(1L, null, emptyList(), meanPeriods = listOf(previous, current))

    @Test fun `les quatre sources et la periode precedente sont distinctes`() {
        fun read(source: MeanSource, period: MeanPeriod = MeanPeriod.Current) = MeanWidgetData.read(snapshot, MeanWidgetConfig(source = source, period = period, subject = "math", target = 16.0), 2, PrivacyMode())
        assertEquals("14,26", read(MeanSource.General).value)
        assertEquals("15,26", read(MeanSource.Subject).value)
        assertEquals("12,50", read(MeanSource.Class).value)
        assertEquals("−1,74", read(MeanSource.GoalGap).value)
        assertEquals("13,00", read(MeanSource.General, MeanPeriod.Previous).value)
        assertEquals("Recalculée · bulletin 13,00", read(MeanSource.General).origin)
        assertEquals("", read(MeanSource.General).trend) // origines incompatibles
        assertEquals("↗ +1,26", read(MeanSource.Subject).trend)
    }

    @Test fun `les seuils incluent leurs bornes et restent personnalisables`() {
        val config = MeanWidgetConfig()
        assertEquals(MeanTone.Positive, config.tone(14.0))
        assertEquals(MeanTone.Warning, config.tone(10.0))
        assertEquals(MeanTone.Negative, config.tone(9.99))
        assertEquals(MeanTone.Neutral, config.tone(null))
        assertEquals(MeanTone.Positive, config.copy(greenFrom = 5.0, orangeFrom = 2.0).tone(5.0))
    }

    @Test fun `virgule decimales absence et vie privee sans fuite de couleur ou tendance`() {
        assertEquals("15,3", MeanWidgetData.read(snapshot, MeanWidgetConfig(source = MeanSource.Subject, subject = "math"), 1, PrivacyMode()).value)
        assertEquals("–", MeanWidgetData.read(null, MeanWidgetConfig(), 2, PrivacyMode()).value)
        assertEquals("–", MeanWidgetData.read(snapshot, MeanWidgetConfig(source = MeanSource.Subject, subject = "inconnue"), 2, PrivacyMode()).value)
        val hidden = MeanWidgetData.read(snapshot, MeanWidgetConfig(), 2, PrivacyMode(true))
        assertEquals(MASKED, hidden.value)
        assertEquals("", hidden.trend)
        assertEquals("", hidden.origin)
        assertEquals(MeanTone.Neutral, hidden.tone)
    }

    @Test fun `le repli de generale respecte coefficients et sous matieres du moteur`() {
        val period = current.copy(general = WidgetMean(), subjects = listOf(subject,
            subject.copy(id = "zero", mean = WidgetMean(0.0), coefficient = 0.0),
            subject.copy(id = "sub", mean = WidgetMean(0.0), isSubSubject = true)))
        val value = MeanWidgetData.read(snapshot.copy(meanPeriods = listOf(period)), MeanWidgetConfig(), 2, PrivacyMode())
        assertEquals("15,26", value.value)
        assertEquals("calculée par l'app", value.origin)
    }

    @Test fun `configuration par instance serialisee puis supprimee sans toucher aux autres`() {
        val storage = WidgetTestStorage()
        val store = WidgetConfigStore(storage)
        val config = MeanWidgetConfig(profile = "compte/enfant", source = MeanSource.Subject, subject = "math", title = "Mes math", greenFrom = 16.0, orangeFrom = 11.0, showTrend = false)
        store.save(11, config)
        store.save(12, MeanWidgetConfig(title = "Autre"))
        assertEquals(config, WidgetConfigStore(storage).read(11))
        store.remove(11)
        assertEquals(MeanWidgetConfig(), store.read(11))
        assertEquals("Autre", store.read(12).title)
    }

    @Test fun `configuration invalide refusee sans ecraser instance`() {
        val store = WidgetConfigStore(WidgetTestStorage())
        assertFalse(store.save(1, MeanWidgetConfig(title = "Trop long !")))
        assertFalse(store.save(1, MeanWidgetConfig(greenFrom = 9.0, orangeFrom = 10.0)))
        assertFalse(store.save(1, MeanWidgetConfig(target = Double.NaN)))
        assertFalse(store.save(-1, MeanWidgetConfig()))
    }

    @Test fun `instantane enrichi conserve precision et origines`() {
        assertEquals(snapshot, WidgetSnapshot.fromJson(snapshot.toJson()))
        assertEquals(emptyList<WidgetMeanPeriod>(), WidgetSnapshot.fromJson("{\"updatedAt\":1}")!!.meanPeriods)
    }

    @Test fun `petit large et grandes polices choisissent une variante pure`() {
        assertEquals(SmallWidgetVariant.One, SmallWidgetLayout.variant(60f, 60f, false, 1f))
        assertEquals(SmallWidgetVariant.Wide, SmallWidgetLayout.variant(180f, 90f, true, 1f))
        assertEquals(SmallWidgetVariant.One, SmallWidgetLayout.variant(180f, 90f, true, 2f))
        assertEquals(SmallWidgetVariant.Large, SmallWidgetLayout.variant(600f, 840f, false, 1.3f))
        assertEquals(SmallWidgetVariant.Large, SmallWidgetLayout.variant(900f, 600f, true, 1.3f))
        assertTrue(SmallWidgetLayout.content(180f, 110f, true, 1f).detailLines >= 2)
    }

    @Test fun `anciennete en heures sans age negatif`() {
        assertEquals("", MeanWidgetData.age(10_000, 0))
        assertEquals("il y a 2 h", MeanWidgetData.age(1, 7_200_001))
    }

    @Test fun `un tout petit widget reserve la place au chiffre a grande police`() {
        val tiny = SmallWidgetLayout.content(56f, 56f, false, 3f)
        assertFalse(tiny.header)
        assertEquals(0, tiny.captionLines)
        assertEquals(0, tiny.detailLines)
        assertFalse(tiny.badge)
        assertTrue(tiny.valueSize >= 8)
    }

    @Test fun `retrait de compte vide les reglages mais garde une liaison sans repli sur un autre profil`() {
        val store = WidgetConfigStore(WidgetTestStorage())
        store.save(1, MeanWidgetConfig(profile = "compte/enfant", title = "Maths"))
        store.save(2, MeanWidgetConfig(profile = "autre/", title = "Autre"))
        store.clearAccount("compte")
        assertEquals("compte/enfant", store.read(1).profile)
        assertEquals("", store.read(1).title)
        assertEquals("Autre", store.read(2).title)
    }

    @Test fun `le dernier instantane ancien reste lisible avant une nouvelle publication`() {
        val old = WidgetSnapshot(1, null, emptyList(), average = "14,26", averageLabel = "officielle")
        assertEquals("14,26", MeanWidgetData.read(old, MeanWidgetConfig(), 2, PrivacyMode()).value)
        assertEquals("–", MeanWidgetData.read(old, MeanWidgetConfig(period = MeanPeriod.Previous), 2, PrivacyMode()).value)
    }
}

internal class WidgetTestStorage : TextStorage {
    private var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
}
