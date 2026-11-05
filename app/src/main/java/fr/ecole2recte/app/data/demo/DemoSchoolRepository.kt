package fr.ecole2recte.app.data.demo

import fr.ecole2recte.app.domain.Badges
import fr.ecole2recte.app.domain.Freshness
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.Highlight
import fr.ecole2recte.app.domain.HighlightKind
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.NextClass
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleSlot
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.domain.SubjectAverage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Données de démonstration issues de la maquette. À remplacer au lot 7. */
class DemoSchoolRepository(private val firstName: String? = null) : SchoolRepository {
    override val freshness: StateFlow<Freshness> = MutableStateFlow(Freshness())

    override fun invalidate() = Unit

    private val subjects = listOf(
        Subject("Philosophie", "Mme Laurent", "B204", 2),
        Subject("Mathématiques", "M. Petit", "C011", 0),
        Subject("Anglais", "Mme Robert", "A112", 1),
        Subject("Histoire-Géo", "M. Dubois", "A112", 3),
    )

    private val homework = listOf(
        Homework("Dissertation — La liberté", "Philosophie", "Lundi 7 septembre", 2),
        Homework("Exercices 12 à 18", "Mathématiques", "Mardi 8 septembre", 0),
        Homework("Reading comprehension", "Anglais", "Jeudi 10 septembre", 1),
    )

    private val averages = listOf("14,0", "17,5", "16,0", "15,5")

    private val grades = GradeOverview(
        average = "15,8",
        trend = "↗ +1,2",
        bySubject = subjects.mapIndexed { index, subject -> SubjectAverage(subject, averages[index]) },
    )

    override suspend fun home(): HomeData = HomeData(
        greeting = firstName?.let { "Bonjour $it" } ?: "Bonjour",
        dateLabel = "Samedi 5 septembre 2026",
        nextClass = NextClass(subjects.first(), startLabel = "10:15  ·  dans 24 min", progress = 0.64f),
        today = subjects.take(3).mapIndexed { index, subject ->
            ScheduleSlot(listOf("08:00", "10:15", "13:30")[index], subject)
        },
        reminder = homework.first(),
        highlights = listOf(
            Highlight(HighlightKind.Grade, "Nouvelle note en Mathématiques", "17,5 / 20"),
            Highlight(HighlightKind.Message, "2 messages non lus", "Vie scolaire · Réunion parents-professeurs"),
        ),
        grades = grades,
        allSubjects = subjects,
    )

    override suspend fun schedule(): ScheduleWeek = ScheduleWeek(
        label = "Semaine du 5 septembre",
        entries = subjects.map { ScheduleEntry(it, "Lundi · 10:15 — 12:00") },
    )

    override suspend fun homework(): List<Homework> = homework

    override suspend fun grades(): GradeOverview = grades

    override suspend fun badges(): Badges = Badges(newGrades = 1, urgentHomework = 1, unreadMessages = 2)

    override suspend fun messages(): List<MessagePreview> = listOf(
        MessagePreview("Vie scolaire", "Réunion parents-professeurs..."),
        MessagePreview("M. Petit · Maths", "Correction du devoir maison"),
    )
}
