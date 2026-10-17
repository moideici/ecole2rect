package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Change
import fr.ecole2recte.app.core.model.ChangeKind
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
 * Les neuf widgets d'écran d'accueil.
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
        changes = listOf(
            Change(
                id = "annule:2",
                kind = ChangeKind.LESSON_CANCELED,
                text = "Mathématiques 10:15 annulé",
                detail = "aujourd'hui · 10:15",
                subject = "Mathématiques",
                noticedOn = monday,
                atMillis = 1_800_000_000_000L,
            ),
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
    fun `la forme declaree est celle que la taille d installation rend vraiment`() {
        // `natural` est la forme annoncée par chaque widget ; elle sert de valeur par défaut
        // et d'intention documentée. Recompter l'énumération ne prouvait rien : ce qui compte
        // est qu'à la taille où le lanceur pose le widget, le dimensionnement rende cette
        // forme-là. Une intention que le code contredit est une intention fausse.
        WidgetKind.entries.forEach { kind ->
            val size = installSize(kind.natural)
            assertEquals(
                "${kind.name} s'annonce en ${kind.natural} mais s'installe en " +
                    "${WidgetSizing.shapeFor(kind, size)}",
                kind.natural,
                WidgetSizing.shapeFor(kind, size),
            )
        }
    }

    /** La taille que le lanceur annonce pour la forme visée, en dp. */
    private fun installSize(shape: WidgetShape): WidgetSize = when (shape) {
        WidgetShape.TILE -> WidgetSize(40, 40)
        WidgetShape.ROW, WidgetShape.HOURS -> WidgetSize(140, 40)
        WidgetShape.LIST, WidgetShape.GRID -> WidgetSize(140, 140)
        WidgetShape.WEEK -> WidgetSize(280, 140)
    }

    @Test
    fun `chaque widget sait se dire dans les cinq formes`() {
        WidgetKind.entries.forEach { kind ->
            WidgetShape.entries.forEach { shape ->
                val payload = WidgetContent.payload(
                    kind = kind,
                    snapshot = snapshot,
                    // L'objectif n'existe que si on en a fixé un : sans lui, le widget dit
                    // à juste titre qu'il n'a rien à montrer, et le test ne prouverait rien.
                    settings = AppSettings(averageTarget = 16.0),
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
                // Une grille vide serait un widget vide : les widgets horaires doivent
                // toujours avoir une journée à dessiner, fût-ce celle de demain.
                if (shape == WidgetShape.GRID && kind.timetable) {
                    assertTrue("${kind.name} en grille : aucun bloc", payload.blocks.isNotEmpty())
                }
            }
        }
    }


    // ── Adaptation à la taille ───────────────────────────────────────────────

    @Test
    fun `la place disponible choisit le gabarit`() {
        // Les tailles sont celles que le lanceur annonce en dp pour 1×1, 2×1, 2×2 et 4×2.
        assertEquals(WidgetShape.TILE, WidgetSizing.shapeFor(WidgetKind.GRADES, WidgetSize(40, 40)))
        assertEquals(WidgetShape.ROW, WidgetSizing.shapeFor(WidgetKind.GRADES, WidgetSize(140, 40)))
        assertEquals(WidgetShape.LIST, WidgetSizing.shapeFor(WidgetKind.GRADES, WidgetSize(140, 140)))
        // La semaine est le seul contenu qui exige de la largeur ; à l'étroit elle se replie.
        assertEquals(WidgetShape.WEEK, WidgetSizing.shapeFor(WidgetKind.WEEK, WidgetSize(280, 140)))
        // À l'étroit, la semaine redevient une grille de journée ; sans hauteur exacte —
        // avant Android 12 — elle redevient une liste, qui ne ment pas sur les durées.
        assertEquals(WidgetShape.GRID, WidgetSizing.shapeFor(WidgetKind.WEEK, WidgetSize(140, 140)))
        assertEquals(WidgetShape.GRID, WidgetSizing.shapeFor(WidgetKind.DAY, WidgetSize(280, 140)))
        assertEquals(
            WidgetShape.LIST,
            WidgetSizing.shapeFor(WidgetKind.DAY, WidgetSize(280, 140), proportional = false),
        )
        assertEquals(
            WidgetShape.LIST,
            WidgetSizing.shapeFor(WidgetKind.WEEK, WidgetSize(280, 140), proportional = false),
        )
        // Une colonne étroite mais haute reste une tuile : aucune ligne de liste n'y tient.
        assertEquals(WidgetShape.TILE, WidgetSizing.shapeFor(WidgetKind.DAY, WidgetSize(40, 210)))
    }

    @Test
    fun `plus le widget est haut, plus il montre de lignes`() {
        // Les constantes sont mesurées sur le gabarit — en-tête et marges d'un côté, hauteur
        // d'une ligne de l'autre. Les surestimer faisait s'arrêter un widget haut à quatre
        // cours en laissant un tiers de sa surface blanche.
        assertEquals(1, WidgetSizing.maxLines(WidgetSize(140, 70)))
        // Deux lignes en 2×2, et non six : une ligne porte désormais deux niveaux de texte,
        // et promettre six lignes reviendrait à les faire rogner par le bas.
        assertEquals(2, WidgetSizing.maxLines(WidgetSize(140, 140)))
        assertEquals(5, WidgetSizing.maxLines(WidgetSize(140, 220)))
        assertEquals(WidgetSizing.MAX_LINES, WidgetSizing.maxLines(WidgetSize(140, 600)))
    }

    @Test
    fun `agrandir un widget ajoute du contenu, pas du vide`() {
        // Avant le premier cours : la journée entière est encore devant.
        val dawn = monday.atTime(7, 30)
        val small = WidgetContent.payload(
            WidgetKind.DAY, snapshot, AppSettings(), dawn,
            shape = WidgetShape.LIST, maxLines = 1,
        )
        val large = WidgetContent.payload(
            WidgetKind.DAY, snapshot, AppSettings(), dawn,
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
            WidgetKind.DAY, snapshot, AppSettings(), evening,
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
            WidgetKind.NEXT_LESSON, snapshot, AppSettings(), evening,
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
        val payload = WidgetContent.payload(WidgetKind.GRADES, snapshot, AppSettings(), morning)

        assertEquals("15,8", payload.title)
        assertEquals("Trimestre 1", payload.subtitle)
        assertFalse(payload.empty)
    }

    @Test
    fun `le compte a rebours parle en minutes puis en heures`() {
        val payload = WidgetContent.payload(
            WidgetKind.NEXT_LESSON, snapshot, AppSettings(), morning,
            shape = WidgetShape.TILE,
        )

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
        val payload = WidgetContent.payload(
            WidgetKind.DAY, snapshot, AppSettings(), morning,
            shape = WidgetShape.LIST,
        )

        assertEquals(1, payload.lines.size)
        // L'heure à gauche, la matière au milieu, la salle à droite : la lecture d'un
        // emploi du temps, et la matière garde toute la largeur restante.
        assertEquals("10:15", payload.lines.single().leading)
        assertEquals("Mathématiques", payload.lines.single().text)
        assertEquals("C011", payload.lines.single().trailing)
    }

    @Test
    fun `la liste de devoirs suit l etat local`() {
        val settings = AppSettings().withHomeworkDone("901", true)

        val payload = WidgetContent.payload(
            WidgetKind.HOMEWORK, snapshot, settings, morning,
            shape = WidgetShape.LIST,
        )

        assertEquals("1 devoir", payload.title)
        assertEquals("Mathématiques", payload.lines.single().text)
        assertEquals("évaluation", payload.lines.single().secondary)
    }

    @Test
    fun `la semaine dessine chaque jour sur la meme echelle`() {
        val payload = WidgetContent.payload(WidgetKind.WEEK, snapshot, AppSettings(), morning)

        // Le samedi n'a pas cours : sa colonne disparaît plutôt que de voler un sixième de
        // la largeur aux autres.
        assertEquals(5, payload.columns.size)
        assertTrue(payload.columns.first().today)
        assertEquals(listOf("Philosophie", "Mathématiques"), payload.columns.first().blocks
            .filterNot { it.blank || it.free }.map { it.text })

        // Toutes les colonnes couvrent exactement la même durée : c'est ce qui aligne les
        // journées entre elles, et sans quoi la comparaison d'un regard ne veut plus rien dire.
        val durations = payload.columns.map { column -> column.blocks.sumOf { it.minutes } }
        assertEquals(1, durations.distinct().size)
    }

    @Test
    fun `la tuile de vie scolaire montre le compteur le plus urgent`() {
        // Des messages non lus attendent une réponse : ils passent devant tout le reste,
        // et l'en-tête dit lequel des compteurs on regarde.
        val payload = WidgetContent.payload(WidgetKind.SCHOOL_LIFE, snapshot, AppSettings(), morning)
        assertEquals("MESSAGES", payload.label)
        assertEquals("1", payload.title)
        assertEquals("non lus", payload.subtitle)

        // Tout lu : c'est alors l'absence à justifier qui réclame une action.
        val read = AppSettings().withMessageRead("3001")
        val next = WidgetContent.payload(WidgetKind.SCHOOL_LIFE, snapshot, read, morning)
        assertEquals("À JUSTIFIER", next.label)
        assertEquals("1", next.title)
    }

    @Test
    fun `la liste de vie scolaire finit par la prochaine evaluation`() {
        val payload = WidgetContent.payload(
            WidgetKind.SCHOOL_LIFE, snapshot, AppSettings(), morning,
            shape = WidgetShape.LIST, maxLines = 6,
        )

        assertTrue("la liste doit porter quelque chose", payload.lines.isNotEmpty())
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

    // ── Ce que le widget écrit, mot pour mot ─────────────────────────────────

    @Test
    fun `une seule note ne donne pas un pluriel`() {
        val one = snapshot.copy(grades = snapshot.grades.take(1))

        val payload = WidgetContent.payload(
            WidgetKind.GRADES, one, AppSettings(), morning, shape = WidgetShape.LIST,
        )

        // « 1 notes » est la faute qu'on lit sur un écran d'accueil tous les débuts de
        // trimestre ; les trois autres listes du fichier traitaient déjà le cas.
        assertEquals("1 note", payload.title)
        assertEquals(
            "Aucune note",
            WidgetContent.payload(
                WidgetKind.GRADES, SchoolSnapshot(), AppSettings(), morning, shape = WidgetShape.LIST,
            ).title,
        )
    }

    @Test
    fun `le vendredi soir, le widget Demain ne dit pas demain pour un lundi`() {
        // Vendredi 20 h : le prochain jour d'école est le lundi suivant.
        val friday = monday.plusDays(4)
        val evening = friday.atTime(20, 0)
        val week = snapshot.copy(
            lessons = listOf(
                Lesson("l1", "Histoire", room = "D02", start = friday.atTime(8, 0), end = friday.atTime(9, 0)),
                Lesson(
                    "l2", "Physique", room = "E11",
                    start = friday.plusDays(3).atTime(8, 0),
                    end = friday.plusDays(3).atTime(10, 0),
                ),
            ),
        )

        listOf(WidgetShape.ROW, WidgetShape.GRID, WidgetShape.LIST).forEach { shape ->
            val label = WidgetContent.payload(
                WidgetKind.TOMORROW, week, AppSettings(), evening, shape = shape,
            ).label

            assertFalse("$shape : « demain » annoncé pour un lundi → $label", label.contains("DEMAIN"))
            assertTrue("$shape : le jour montré doit être nommé → $label", label.contains("LUNDI"))
        }
    }

    @Test
    fun `le widget Demain dit demain quand c en est un`() {
        val label = WidgetContent.payload(
            WidgetKind.TOMORROW, snapshot, AppSettings(), monday.atTime(20, 0), shape = WidgetShape.ROW,
        ).label

        assertTrue(label, label.startsWith("DEMAIN"))
    }

    @Test
    fun `agrandir le widget des changements ne change pas le compte`() {
        // Deux changements repérés il y a trois jours, un repéré aujourd'hui. Tant que la
        // tuile comptait « depuis hier » et la liste « sept jours », le même widget annonçait
        // 1 en 1×1 et 3 en 2×2 sans que rien n'ait bougé entre les deux rendus.
        val older = (1..2).map { index ->
            Change(
                id = "salle:$index",
                kind = ChangeKind.LESSON_MOVED,
                text = "Salle changée",
                detail = "il y a trois jours",
                noticedOn = monday.minusDays(3),
                atMillis = 1_700_000_000_000L,
            )
        }
        val full = snapshot.copy(changes = snapshot.changes + older)

        val tile = WidgetContent.payload(
            WidgetKind.CHANGES, full, AppSettings(), morning, shape = WidgetShape.TILE,
        )
        val list = WidgetContent.payload(
            WidgetKind.CHANGES, full, AppSettings(), morning, shape = WidgetShape.LIST, maxLines = 6,
        )

        assertEquals("3", tile.title)
        assertEquals("3 changements", list.title)
        assertEquals(3, list.lines.size)
    }
}
