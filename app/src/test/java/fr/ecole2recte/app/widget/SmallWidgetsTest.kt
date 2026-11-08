package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.sync.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class SmallWidgetsTest {
    private val now = LocalDateTime.of(2026, 11, 8, 9, 35)
    private fun snapshot() = WidgetSnapshot(1, null, listOf(
        WidgetTask("Maths", "À faire", "", dueDate = "2026-11-09"),
        WidgetTask("Anglais", "Plus tard", "", dueDate = "2026-11-12")),
        lessons = listOf(
            WidgetSlot("2026-11-08", "09:40", "10:00", "Annulé", "", "", true, 0),
            WidgetSlot("2026-11-08", "10:00", "11:00", "Maths", "B12", "", false, 0)),
        grades = listOf(WidgetGradeItem("Maths", "8/10", "1", "DS", 0, "g", "2026-11-07", 1.5, 1000)),
        unread = 4, exams = listOf(WidgetTask("Anglais", "Contrôle", "", dueDate = "2026-11-11", exam = true)))

    @Test fun `prochain cours ignore les annules et calcule le delai a la lecture`() {
        val data = SmallWidgetData.read(SmallWidgetKind.NextClass, snapshot(), now, 1000, PrivacyMode())
        assertEquals("Maths", data.value)
        assertTrue(data.caption.contains("10:00"))
        assertTrue(data.caption.contains("dans 25 min"))
        assertEquals("Salle B12", data.detail)
    }

    @Test fun `devoirs comptes sans limitation et urgence pour demain`() {
        val data = SmallWidgetData.read(SmallWidgetKind.Homework, snapshot(), now, 0, PrivacyMode())
        assertEquals("2", data.value)
        assertEquals(MeanTone.Negative, data.tone)
        assertEquals("Maths", data.detail)
        val tasks = (1..12).map { Homework("Exercice", "Maths", "", 0, dueDate = "2026-11-09", done = it == 1) }
        val stored = WidgetLogic.snapshot(0, now.toLocalDate(), nextClass = null, week = null, homework = tasks, grades = null, inbox = emptyList())
        assertEquals(11, stored.tasks.size)
    }

    @Test fun `derniere note nouveau trois jours et ecart classe au bon bareme`() {
        val data = SmallWidgetData.read(SmallWidgetKind.LastGrade, snapshot(), now, 1001, PrivacyMode())
        assertEquals("8/10", data.value)
        assertEquals("Nouveau", data.badge)
        assertEquals("▲ +1,5 vs classe", data.detail)
        assertEquals("", SmallWidgetData.read(SmallWidgetKind.LastGrade, snapshot(), now, 1000 + fr.ecole2recte.app.data.NEW_GRADE_WINDOW_MS, PrivacyMode()).badge)
    }

    @Test fun `vie privee masque aussi les indicateurs derives et le compteur de messages`() {
        val grade = SmallWidgetData.read(SmallWidgetKind.LastGrade, snapshot(), now, 1001, PrivacyMode(true))
        assertEquals(MASKED, grade.value)
        assertEquals("", grade.detail)
        assertEquals("", grade.badge)
        assertEquals(MASKED, SmallWidgetData.read(SmallWidgetKind.Messages, snapshot(), now, 0, PrivacyMode(true, true)).value)
        assertEquals("4", SmallWidgetData.read(SmallWidgetKind.Messages, snapshot(), now, 0, PrivacyMode(true)).value)
    }

    @Test fun `controle utilise calendrier et ignore passe ou date invalide`() {
        assertEquals("J−3", SmallWidgetData.read(SmallWidgetKind.Exam, snapshot(), now, 0, PrivacyMode()).value)
        val stored = snapshot().copy(exams = listOf(WidgetTask("Erreur", "", "", dueDate = "invalide", exam = true),
            WidgetTask("Passé", "", "", dueDate = "2026-11-07", exam = true)) + snapshot().exams)
        assertEquals("Anglais", SmallWidgetData.read(SmallWidgetKind.Exam, stored, now, 0, PrivacyMode()).caption)
    }

    @Test fun `un controle fait reste un controle a venir`() {
        val stored = WidgetLogic.snapshot(0, LocalDate.of(2026, 11, 8), nextClass = null, week = null,
            homework = listOf(Homework("DS", "Maths", "", 0, dueDate = "2026-11-11", exam = true, done = true)), grades = null, inbox = emptyList())
        assertTrue(stored.tasks.isEmpty())
        assertEquals(1, stored.exams.size)
    }

    @Test fun `sans instantane toutes les valeurs restent absentes`() {
        SmallWidgetKind.entries.forEach { assertEquals("–", SmallWidgetData.read(it, null, now, 0, PrivacyMode()).value) }
    }

    @Test fun `les nouveaux champs passent par le masque commun et la serialisation`() {
        val restored = WidgetSnapshot.fromJson(snapshot().toJson())!!
        assertEquals(snapshot(), restored)
        val masked = WidgetPrivacy.apply(restored, PrivacyMode(true, true))
        assertEquals(0, masked.unread)
        assertNull(masked.grades.single().classGap)
        assertEquals(emptyList<WidgetMeanPeriod>(), masked.meanPeriods)
    }

    @Test fun `une echeance inconnue est comptee sans inventer durgence`() {
        val data = SmallWidgetData.read(SmallWidgetKind.Homework, snapshot().copy(tasks = listOf(WidgetTask("Maths", "DS", ""))), now, 0, PrivacyMode())
        assertEquals("1", data.value)
        assertEquals(MeanTone.Neutral, data.tone)
    }
}
