package fr.ecole2recte.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyModeTest {
    private val entry = GradeEntry(
        id = "1", subject = "Maths", colorIndex = 0, label = "DS 1", value = 16.0, scale = 20.0, coefficient = 2.0,
        date = "2026-11-04", classAverage = 12.5, minimum = 6.0, maximum = 19.0, counted = true, periodCode = "A1",
    )
    private val subject = Subject("Maths", "M. Martin", "B204", 0)
    private val line = SubjectAverage(subject, "15,5", classAverage = "12,5", grades = listOf(entry), appreciation = "Très bon trimestre", minimum = "6", maximum = "19", averageOfficial = true)
    private val overview = GradeOverview(
        average = "14,2", trend = "↗ +0,8", bySubject = listOf(line), recent = listOf(entry), all = listOf(entry),
        evolution = listOf(PeriodAverage("T1", 13.4), PeriodAverage("T2", 14.2)), classAverage = "12,1",
        periodName = "1er trimestre", periodCode = "A1", periods = listOf(GradePeriod("A1", "T1", true, "14,2"), GradePeriod("A2", "T2", false, null)),
        appreciation = "Continue ainsi", averageSource = AverageSource.Official,
    )
    private val on = PrivacyMode(enabled = true)

    @Test
    fun `eteint, rien ne change`() {
        val off = PrivacyMode()
        assertSame(overview, off.mask(overview))
        assertEquals("14,5/20", off.grade("14,5/20"))
        assertFalse(off.hidesGrades)
        assertFalse(off.hidesMessages)
    }

    @Test
    fun `une note est remplacee par trois points`() {
        assertEquals("•••", on.grade("14,5/20"))
        assertEquals("•••", MASKED)
    }

    @Test
    fun `aucune valeur de note ne ressort de l'ensemble des notes`() {
        val masked = on.mask(overview)
        assertEquals(MASKED, masked.average)
        assertEquals("", masked.trend)
        assertNull(masked.classAverage)
        assertTrue(masked.evolution.isEmpty()) // la courbe disparaît
        assertEquals("", masked.appreciation)
        assertEquals(listOf(MASKED, null), masked.periods.map { it.average })
        val subjectLine = masked.bySubject.single()
        assertEquals(MASKED, subjectLine.average)
        assertNull(subjectLine.classAverage)
        assertNull(subjectLine.minimum)
        assertNull(subjectLine.maximum)
        assertEquals("", subjectLine.appreciation)
        listOf(masked.recent.single(), masked.all.single(), subjectLine.grades.single()).forEach {
            assertNull(it.value)
            assertEquals(MASKED, it.rawValue)
            assertNull(it.classAverage)
            assertNull(it.minimum)
            assertNull(it.maximum)
            // Ce qui n'est pas une valeur reste : matière, intitulé, date, coefficient.
            assertEquals("Maths", it.subject)
            assertEquals("DS 1", it.label)
            assertEquals(2.0, it.coefficient, 0.0)
        }
    }

    @Test
    fun `le texte rendu ne contient aucun des chiffres d'origine`() {
        val text = on.mask(overview).toString()
        listOf("14,2", "15,5", "16.0", "12.5", "12,5", "12,1", "13.4", "0,8", "Très bon", "Continue").forEach {
            assertFalse("« $it » ne doit pas ressortir : $text", text.contains(it))
        }
    }

    @Test
    fun `les notes sans valeur numerique restent masquees aussi`() {
        val absent = entry.copy(value = null, rawValue = "Abs")
        assertEquals(MASKED, on.mask(absent).rawValue)
    }

    @Test
    fun `l'accueil masque la moyenne, les notes mises en avant et la moyenne des enfants`() {
        val home = HomeData(
            greeting = "Bonjour", dateLabel = "Jeudi 5 novembre", nextClass = null, today = emptyList(), reminder = null,
            highlights = listOf(
                Highlight(HighlightKind.Grade, "Maths", "DS 1 · 4 nov. · classe 12,5", "16/20", "coef. 2"),
                Highlight(HighlightKind.Message, "1 message non lu", "Mme Dupont · Sortie scolaire"),
                Highlight(HighlightKind.Canceled, "Cours annulé : Maths", "demain à 10:15"),
            ),
            family = listOf(ChildSummary("1", "Léa", "L", "5e", "Maths · 10:15", 1, 2, "14,1", true), ChildSummary("2", "Tom", "T", "3e", null, 0, 0, null, false)),
            grades = overview, allSubjects = listOf(subject),
        )
        val masked = on.mask(home)
        assertEquals(MASKED, masked.grades.average)
        val grade = masked.highlights[0]
        assertEquals("", grade.detail)
        assertEquals(MASKED, grade.value)
        assertNull(grade.valueSub)
        assertEquals("Maths", grade.title) // la matière n'est pas une note
        // Les messages ne sont pas cachés tant que l'option n'est pas cochée.
        assertEquals("Mme Dupont · Sortie scolaire", masked.highlights[1].detail)
        assertEquals("demain à 10:15", masked.highlights[2].detail)
        assertEquals(listOf(MASKED, null), masked.family.map { it.average })
        assertEquals(2, masked.family[0].newGrades) // un nombre de nouveautés n'est pas une note
        assertEquals(MASKED, PrivacyMode(enabled = true, hideMessages = true).mask(home).highlights[1].detail)
    }

    @Test
    fun `les messages ne sont masques qu'avec l'option`() {
        val message = MessagePreview("Mme Dupont", "Sortie scolaire", unread = true, id = "9", dateLabel = "10:15")
        assertEquals(message, on.maskMessages(listOf(message)).single())
        val hidden = PrivacyMode(enabled = true, hideMessages = true)
        assertTrue(hidden.hidesMessages)
        val masked = hidden.maskMessages(listOf(message)).single()
        assertEquals(MASKED, masked.sender)
        assertEquals(MASKED, masked.preview)
        // L'identifiant, l'état et l'heure restent pour que la liste fonctionne.
        assertEquals("9", masked.id)
        assertTrue(masked.unread)
        assertEquals("10:15", masked.dateLabel)
    }

    @Test
    fun `l'option messages sans le mode general ne fait rien`() {
        val onlyOption = PrivacyMode(enabled = false, hideMessages = true)
        assertFalse(onlyOption.hidesMessages)
        assertFalse(onlyOption.hidesGrades)
    }

    @Test
    fun `la recherche masque les notes, et les messages seulement avec l'option`() {
        val grades = SearchGroup(SearchKind.Grade, listOf(SearchHit(SearchKind.Grade, "Maths 16/20", "DS 1")), 1)
        val messages = SearchGroup(SearchKind.Message, listOf(SearchHit(SearchKind.Message, "Sortie", "Mme Dupont")), 1)
        val homework = SearchGroup(SearchKind.Homework, listOf(SearchHit(SearchKind.Homework, "Exercices", "Maths")), 1)
        assertEquals(MASKED, on.mask(grades).hits.single().title)
        assertEquals("", on.mask(grades).hits.single().subtitle)
        assertEquals("Sortie", on.mask(messages).hits.single().title)
        assertEquals(MASKED, PrivacyMode(true, true).mask(messages).hits.single().title)
        assertSame(homework, PrivacyMode(true, true).mask(homework))
    }
}
