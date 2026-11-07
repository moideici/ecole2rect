package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.MASKED
import fr.ecole2recte.app.domain.PrivacyMode
import fr.ecole2recte.app.sync.WidgetGradeItem
import fr.ecole2recte.app.sync.WidgetMail
import fr.ecole2recte.app.sync.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WidgetPrivacyTest {
    private val snapshot = WidgetSnapshot(
        updatedAt = 1L,
        nextClass = null,
        tasks = emptyList(),
        grades = listOf(WidgetGradeItem("Maths", "16/20", "2", "DS 1", 0)),
        average = "15,8",
        unread = 2,
        mails = listOf(WidgetMail("Vie scolaire", "Réunion")),
    )

    @Test
    fun `sans le mode rien ne change`() {
        assertSame(snapshot, WidgetPrivacy.apply(snapshot, PrivacyMode(enabled = false, hideMessages = true)))
    }

    @Test
    fun `le mode masque les notes et la moyenne mais pas les messages par defaut`() {
        val masked = WidgetPrivacy.apply(snapshot, PrivacyMode(enabled = true))
        assertEquals(MASKED, masked.average)
        assertEquals(MASKED, masked.grades.single().value)
        assertEquals("", masked.grades.single().label)
        assertEquals("Maths", masked.grades.single().subject)
        assertEquals("Vie scolaire", masked.mails.single().sender)
        assertEquals(2, masked.unread)
    }

    @Test
    fun `l'option messages masque l'expediteur et le sujet`() {
        val masked = WidgetPrivacy.apply(snapshot, PrivacyMode(enabled = true, hideMessages = true))
        assertEquals(MASKED, masked.mails.single().sender)
        assertEquals(MASKED, masked.mails.single().subject)
    }

    @Test
    fun `une moyenne absente reste absente`() {
        val masked = WidgetPrivacy.apply(snapshot.copy(average = ""), PrivacyMode(enabled = true))
        assertEquals("", masked.average)
    }

    @Test
    fun `l'instantane d'origine n'est jamais modifie`() {
        WidgetPrivacy.apply(snapshot, PrivacyMode(enabled = true, hideMessages = true))
        assertEquals("15,8", snapshot.average)
        assertEquals("16/20", snapshot.grades.single().value)
    }
}
