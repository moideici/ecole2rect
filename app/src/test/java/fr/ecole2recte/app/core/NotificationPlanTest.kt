package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.notify.NotificationKind
import fr.ecole2recte.app.core.notify.NotificationPlan
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Les notifications sont la fonctionnalité la plus facile à rendre insupportable : ces tests
 * fixent les deux garde-fous — pas de rafale au premier lancement, jamais deux fois la même.
 */
class NotificationPlanTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val evening = monday.atTime(18, 30)

    private val snapshot = SchoolSnapshot(
        homework = listOf(
            HomeworkItem("901", "Philosophie", monday.plusDays(2), content = "Dissertation"),
            HomeworkItem("902", "Mathématiques", monday.plusDays(1), exam = true),
        ),
        grades = listOf(
            Grade("5001", "Mathématiques", "DS n°1", 17.5, 20.0, 2.0, 13.2, monday, "A001"),
        ),
        messages = listOf(
            MessageItem("3001", "Réunion", "Mme Legrand", monday.atTime(9, 42)),
        ),
    )

    @Test
    fun `le premier passage memorise sans rien afficher`() {
        val plan = NotificationPlan.compute(snapshot, AppSettings(), evening)

        assertTrue("aucune rafale au premier lancement", plan.notifications.isEmpty())
        assertEquals(
            setOf("note:5001", "devoir:901", "devoir:902", "message:3001", "rappel:902:2026-09-08"),
            plan.seen.toSet(),
        )
    }

    @Test
    fun `seul le nouveau est signale`() {
        val known = AppSettings().withNotified(
            listOf("note:5001", "devoir:901", "devoir:902", "message:3001", "rappel:902:2026-09-08"),
        )
        val withNewGrade = snapshot.copy(
            grades = snapshot.grades + Grade("5002", "Anglais", "Oral", 15.0, 20.0, 1.0, null, monday, "A001"),
        )

        val plan = NotificationPlan.compute(withNewGrade, known, evening)

        assertEquals(1, plan.notifications.size)
        val notification = plan.notifications.single()
        assertEquals(NotificationKind.GRADE, notification.kind)
        assertEquals("Nouvelle note en Anglais", notification.title)
        assertTrue(notification.text.startsWith("15/20"))
    }

    @Test
    fun `une evaluation du lendemain declenche un rappel, une seule fois`() {
        val known = AppSettings().withNotified(listOf("note:5001", "devoir:901", "devoir:902", "message:3001"))

        val plan = NotificationPlan.compute(snapshot, known, evening)

        val reminder = plan.notifications.single { it.kind == NotificationKind.EXAM }
        assertEquals("Évaluation demain en Mathématiques", reminder.title)
        assertEquals("rappel:902:2026-09-08", reminder.id)

        val after = NotificationPlan.compute(snapshot, known.withNotified(plan.seen), evening)
        assertTrue("le rappel ne doit pas revenir", after.notifications.isEmpty())
    }

    @Test
    fun `chaque type peut etre coupe separement`() {
        val known = AppSettings(
            notifications = NotificationSettings(grades = false, examReminder = false),
        ).withNotified(listOf("initialise"))

        val plan = NotificationPlan.compute(snapshot, known, evening)

        assertTrue(plan.notifications.none { it.kind == NotificationKind.GRADE })
        assertTrue(plan.notifications.none { it.kind == NotificationKind.EXAM })
        assertTrue(plan.notifications.any { it.kind == NotificationKind.HOMEWORK })
        assertTrue(plan.notifications.any { it.kind == NotificationKind.MESSAGE })
    }

    @Test
    fun `tout couper ne produit plus rien mais continue de memoriser`() {
        val known = AppSettings(notifications = NotificationSettings(enabled = false))
            .withNotified(listOf("initialise"))

        val plan = NotificationPlan.compute(snapshot, known, evening)

        assertTrue(plan.notifications.isEmpty())
        assertTrue("mémoriser évite une rafale à la réactivation", plan.seen.isNotEmpty())
    }

    @Test
    fun `la memoire des notifications reste bornee`() {
        var settings = AppSettings()
        repeat(500) { index -> settings = settings.withNotified(listOf("note:$index")) }

        assertEquals(400, settings.notified.size)
        assertTrue(settings.hasBeenNotified("note:499"))
        assertTrue("les plus anciens finissent par sortir", !settings.hasBeenNotified("note:0"))
    }
}
