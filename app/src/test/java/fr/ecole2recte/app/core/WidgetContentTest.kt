package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolLifeEvent
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetSize
import fr.ecole2recte.app.core.widget.WidgetSizing
import fr.ecole2recte.app.core.widget.WidgetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Les quinze widgets d'écran d'accueil.
 *
 * Un widget qui plante n'affiche rien du tout, parfois définitivement jusqu'au redémarrage
 * du lanceur : ces tests vérifient surtout qu'aucun ne dépend d'une donnée présente.
 */
class WidgetContentTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val morning = monday.atTime(9, 51)

    private val snapshot = SchoolSnapshot(
        lessons = listOf(
            Lesson("1", "Philosophie", "Mme Laurent", "B204", monday.atTime(8, 0), monday.atTime(9, 0)),
            Lesson("2", "Mathématiques", "M. Petit", "C011", monday.atTime(10, 15), monday.atTime(12, 0)),
            Lesson("3", "Anglais", room = "A112", start = monday.plusDays(1).atTime(8, 0), end = monday.plusDays(1).atTime(9, 0)),
        ),
        homework = listOf(
            HomeworkItem("901", "Philosophie", monday, content = "Dissertation sur la liberté"),
            HomeworkItem("902", "Mathématiques", monday.plusDays(1), exam = true),
        ),
        grades = listOf(
            Grade("5001", "Mathématiques", "DS n°1", 17.5, 20.0, 2.0, 13.2, monday, "A001"),
            Grade("5002", "Philosophie", "Interro", 12.0, 20.0, 1.0, null, monday.minusDays(3), "A001"),
        ),
        periods = listOf(
            Period("A001", "Trimestre 1", average = 15.8, start = monday.minusDays(7), end = monday.plusDays(60)),
        ),
        messages = listOf(
            MessageItem("3001", "Réunion", "Mme Legrand", monday.atTime(9, 42)),
        ),
        schoolLife = listOf(
            SchoolLifeEvent("v1", SchoolLifeKind.ABSENCE, "Absence", monday.minusDays(3).atTime(8, 0)),
            SchoolLifeEvent(
                "v2",
                SchoolLifeKind.ABSENCE,
                "Absence",
                monday.minusDays(10).atTime(10, 0),
                justified = true,
            ),
            SchoolLifeEvent("v3", SchoolLifeKind.LATE, "Retard", monday.minusDays(1).atTime(8, 5)),
        ),
        syncedAt = mapOf(DataSection.TIMETABLE to morning.minusMinutes(4).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()),
    )

    @Test
    fun `chaque widget produit un contenu, meme sans aucune donnee`() {
        WidgetKind.entries.forEach { kind ->
            val full = WidgetContent.payload(kind, snapshot, AppSettings(), morning)
            val empty = WidgetContent.payload(kind, SchoolSnapshot(), AppSettings(), morning)

            assertTrue("${kind.name} : étiquette vide", full.label.isNotBlank())
            assertTrue("${kind.name} : titre vide", full.title.isNotBlank())
            assertTrue("${kind.name} : titre vide sans données", empty.title.isNotBlank())
            assertTrue("${kind.name} : devrait être signalé vide", empty.empty || empty.lines.isEmpty())
        }
    }

    @Test
    fun `les quinze widgets couvrent les quatre gabarits a l installation`() {
        assertEquals(15, WidgetKind.entries.size)
        assertEquals(6, WidgetKind.entries.count { it.natural == WidgetShape.TILE })
        assertEquals(4, WidgetKind.entries.count { it.natural == WidgetShape.ROW })
        assertEquals(4, WidgetKind.entries.count { it.natural == WidgetShape.LIST })
        assertEquals(1, WidgetKind.entries.count { it.natural == WidgetShape.WEEK })
    }

    @Test
    fun `chaque widget sait se dire dans les quatre formes`() {
        WidgetKind.entries.forEach { kind ->
            WidgetShape.entries.forEach { shape ->
                val payload = WidgetContent.payload(
                    kind = kind,
                    snapshot = snapshot,
                    settings = AppSettings(),
                    now = morning,
                    shape = shape,
                    maxLines = 6,
                )
                assertEquals("${kind.name} en $shape", shape, payload.shape)
                assertTrue("${kind.name} en $shape : titre vide", payload.title.isNotBlank())
                assertTrue("${kind.name} en $shape : libellé vide", payload.label.isNotBlank())
                // Agrandir doit ajouter du contenu, pas du vide : au-delà de la tuile et de
                // la ligne, un widget doit avoir des lignes ou des jours à montrer.
                if (shape == WidgetShape.LIST) {
                    assertTrue("${kind.name} en liste : aucune ligne", payload.lines.isNotEmpty())
                }
            }
        }
    }


    // ── Adaptation à la taille ───────────────────────────────────────────────

    @Test
    fun `la place disponible choisit le gabarit`() {
        // Les tailles sont celles que le lanceur annonce en dp pour 1×1, 2×1, 2×2 et 4×2.
        assertEquals(WidgetShape.TILE, WidgetSizing.shapeFor(WidgetKind.AVERAGE, WidgetSize(40, 40)))
        assertEquals(WidgetShape.ROW, WidgetSizing.shapeFor(WidgetKind.AVERAGE, WidgetSize(140, 40)))
        assertEquals(WidgetShape.LIST, WidgetSizing.shapeFor(WidgetKind.AVERAGE, WidgetSize(140, 140)))
        // La semaine est le seul contenu qui exige de la largeur ; à l'étroit elle se replie.
        assertEquals(WidgetShape.WEEK, WidgetSizing.shapeFor(WidgetKind.WEEK, WidgetSize(280, 140)))
        assertEquals(WidgetShape.LIST, WidgetSizing.shapeFor(WidgetKind.WEEK, WidgetSize(140, 140)))
        assertEquals(WidgetShape.LIST, WidgetSizing.shapeFor(WidgetKind.TODAY, WidgetSize(280, 140)))
        // Une colonne étroite mais haute reste une tuile : aucune ligne de liste n'y tient.
        assertEquals(WidgetShape.TILE, WidgetSizing.shapeFor(WidgetKind.TODAY, WidgetSize(40, 210)))
    }

    @Test
    fun `plus le widget est haut, plus il montre de lignes`() {
        assertEquals(1, WidgetSizing.maxLines(WidgetSize(140, 70)))
        assertEquals(2, WidgetSizing.maxLines(WidgetSize(140, 140)))
        assertEquals(5, WidgetSizing.maxLines(WidgetSize(140, 220)))
        assertEquals(WidgetSizing.MAX_LINES, WidgetSizing.maxLines(WidgetSize(140, 600)))
    }

    @Test
    fun `agrandir un widget ajoute du contenu, pas du vide`() {
        // Avant le premier cours : la journée entière est encore devant.
        val dawn = monday.atTime(7, 30)
        val small = WidgetContent.payload(
            WidgetKind.TODAY, snapshot, AppSettings(), dawn,
            shape = WidgetShape.LIST, maxLines = 1,
        )
        val large = WidgetContent.payload(
            WidgetKind.TODAY, snapshot, AppSettings(), dawn,
            shape = WidgetShape.LIST, maxLines = 6,
        )

        assertEquals(1, small.lines.size)
        assertTrue("la grande version doit montrer davantage", large.lines.size > small.lines.size)
    }

    // ── Jamais « rien » ──────────────────────────────────────────────────────

    @Test
    fun `la journee finie, le widget montre le prochain jour d ecole`() {
        val evening = monday.atTime(19, 30)

        val payload = WidgetContent.payload(
            WidgetKind.TODAY, snapshot, AppSettings(), evening,
            shape = WidgetShape.LIST, maxLines = 4,
        )

        assertEquals("DEMAIN", payload.label)
        assertEquals("Anglais", payload.lines.single().text)
        assertFalse("il y a bien quelque chose à montrer", payload.empty)
    }

    @Test
    fun `le prochain cours annonce le jour quand ce n est pas aujourd hui`() {
        val evening = monday.atTime(19, 30)

        val row = WidgetContent.payload(
            WidgetKind.NEXT_LESSON, snapshot, AppSettings(), evening,
            shape = WidgetShape.ROW,
        )
        val tile = WidgetContent.payload(
            WidgetKind.COUNTDOWN, snapshot, AppSettings(), evening,
            shape = WidgetShape.TILE,
        )

        assertTrue("« dans 12 h » ne dit pas que c'est demain", row.subtitle.contains("demain"))
        assertEquals("DEMAIN", tile.label)
    }

    @Test
    fun `un cours en cours porte son avancement`() {
        val during = monday.atTime(11, 0)

        val payload = WidgetContent.payload(
            WidgetKind.NEXT_LESSON, snapshot, AppSettings(), during,
            shape = WidgetShape.ROW,
        )

        assertEquals("COURS EN COURS", payload.label)
        assertNotNull(payload.progress)
        assertTrue("l'avancement doit rester entre 0 et 1", payload.progress!! in 0f..1f)
    }


    // ── Lecture à voix haute ─────────────────────────────────────────────────

    @Test
    fun `chaque widget se resume en une phrase pour un lecteur d ecran`() {
        WidgetKind.entries.forEach { kind ->
            WidgetShape.entries.forEach { shape ->
                val spoken = WidgetContent.payload(
                    kind = kind,
                    snapshot = snapshot,
                    settings = AppSettings(),
                    now = morning,
                    shape = shape,
                    maxLines = 4,
                ).spoken
                assertTrue("${kind.name} en $shape : rien à annoncer", spoken.length > 3)
                assertFalse("${kind.name} en $shape : ponctuation vide", spoken.contains(". ."))
            }
        }
    }

    @Test
    fun `la phrase annoncee dit le contenu, pas la mise en page`() {
        val spoken = WidgetContent.payload(
            WidgetKind.NEXT_LESSON, snapshot, AppSettings(), morning,
            shape = WidgetShape.ROW,
        ).spoken

        // Une phrase, pas quatre fragments lus l'un après l'autre.
        assertEquals(
            "Prochain cours. Mathématiques. Salle C011 · M. Petit · dans 24 min. 10:15",
            spoken,
        )
    }

    @Test
    fun `la tuile de moyenne affiche celle de l etablissement`() {
        val payload = WidgetContent.payload(WidgetKind.AVERAGE, snapshot, AppSettings(), morning)

        assertEquals("15,8", payload.title)
        assertEquals("Trimestre 1", payload.subtitle)
        assertFalse(payload.empty)
    }

    @Test
    fun `le compte a rebours parle en minutes puis en heures`() {
        val payload = WidgetContent.payload(WidgetKind.COUNTDOWN, snapshot, AppSettings(), morning)

        assertEquals("24 min", payload.title)
        // La tuile doit se suffire à elle-même : sans la salle, « dans 24 min » oblige à
        // ouvrir l'application pour savoir où aller.
        assertEquals("Mathématiques · C011", payload.subtitle)
        assertNotNull("la couleur de la matière doit suivre", payload.accent)
    }

    @Test
    fun `le prochain cours donne salle, professeur et heure`() {
        val payload = WidgetContent.payload(WidgetKind.NEXT_LESSON, snapshot, AppSettings(), morning)

        assertEquals("Mathématiques", payload.title)
        assertEquals("10:15", payload.trailing)
        assertTrue(payload.subtitle.contains("C011"))
        assertTrue(payload.subtitle.contains("M. Petit"))
    }

    @Test
    fun `la journee ne montre que les cours restants`() {
        val payload = WidgetContent.payload(WidgetKind.TODAY, snapshot, AppSettings(), morning)

        assertEquals(1, payload.lines.size)
        assertEquals("Mathématiques", payload.lines.single().text)
        assertEquals("10:15", payload.lines.single().trailing)
    }

    @Test
    fun `la liste de devoirs suit l etat local`() {
        val settings = AppSettings().withHomeworkDone("901", true)

        val payload = WidgetContent.payload(WidgetKind.HOMEWORK_LIST, snapshot, settings, morning)

        assertEquals("1 devoir", payload.title)
        assertEquals("Mathématiques", payload.lines.single().text)
        assertEquals("évaluation", payload.lines.single().secondary)
    }

    @Test
    fun `la semaine compte les cours de chaque jour`() {
        val payload = WidgetContent.payload(WidgetKind.WEEK, snapshot, AppSettings(), morning)

        assertEquals(6, payload.days.size)
        assertEquals("2", payload.days.first().value)
        assertTrue(payload.days.first().today)
        assertEquals("1", payload.days[1].value)
        assertTrue(payload.days[2].empty)
    }

    @Test
    fun `la tuile d evaluation annonce la prochaine, pas toutes`() {
        val payload = WidgetContent.payload(WidgetKind.NEXT_EXAM, snapshot, AppSettings(), morning)

        assertEquals("demain", payload.title)
        assertEquals("Mathématiques", payload.subtitle)
        assertEquals(
            "sans évaluation annoncée, la tuile le dit au lieu de rester vide",
            "rien d'annoncé",
            WidgetContent.payload(WidgetKind.NEXT_EXAM, SchoolSnapshot(), AppSettings(), morning).subtitle,
        )
    }

    @Test
    fun `la tuile d absences ne compte que celles qui attendent un justificatif`() {
        val payload = WidgetContent.payload(WidgetKind.ABSENCES, snapshot, AppSettings(), morning)

        // Une absence justifiée et un retard ne sont pas des absences à justifier.
        assertEquals("1", payload.title)
        assertEquals("absence ce mois", payload.subtitle)
        assertEquals("0", WidgetContent.payload(WidgetKind.ABSENCES, SchoolSnapshot(), AppSettings(), morning).title)
    }

    @Test
    fun `les journees et les croisements devoirs-cours sont justes`() {
        val summary = Insights.daySummary(snapshot.lessons, monday)
        assertEquals(2, summary.lessons)
        assertEquals(165L, summary.minutes)
        assertEquals(monday.atTime(8, 0), summary.start)
        assertEquals(monday.atTime(12, 0), summary.end)

        val gaps = Insights.gaps(Insights.lessonsOn(snapshot.lessons, monday))
        assertEquals(1, gaps.size)
        assertEquals(75L, gaps.single().second)

        val philosophy = snapshot.lessons.first { it.subject == "Philosophie" }
        assertEquals(listOf("901"), Insights.homeworkForLesson(snapshot.homework, philosophy).map { it.id })
        assertEquals(listOf("902"), Insights.upcomingExams(snapshot.homework, AppSettings(), monday).map { it.id })
    }

    @Test
    fun `la moyenne calculee est signalee comme telle`() {
        val withoutOfficial = snapshot.copy(periods = listOf(Period("A001", "Trimestre 1")))

        val official = Insights.generalAverage(snapshot, monday)!!
        val computed = Insights.generalAverage(withoutOfficial, monday)!!

        assertTrue(official.official)
        assertEquals(15.8, official.value, 0.001)
        assertFalse(computed.official)
        // (17,5 × 2 + 12 × 1) / 3
        assertEquals(15.666, computed.value, 0.01)
    }
}
