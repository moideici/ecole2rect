package fr.ecole2recte.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.runComposition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.ecole2recte.app.settings.PrivacySelection
import fr.ecole2recte.app.settings.ThemeSelection
import fr.ecole2recte.app.sync.WidgetGradeItem
import fr.ecole2recte.app.sync.WidgetMail
import fr.ecole2recte.app.sync.WidgetSlot
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore
import fr.ecole2recte.app.sync.WidgetTask
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.widget.DayWidget
import fr.ecole2recte.app.widget.GradesWidget
import fr.ecole2recte.app.widget.HomeworkWidget
import fr.ecole2recte.app.widget.MessagesWidget
import fr.ecole2recte.app.widget.NextClassWidget
import fr.ecole2recte.app.widget.TimetableSizes
import fr.ecole2recte.app.widget.WeekWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Fait dessiner chaque widget à toutes ses tailles, avec et sans données, dans chaque thème : un widget
 * qui plante au dessin (la cause du « widget qui ne s'affiche pas ») fait échouer ce test. L'instantané
 * réel de l'application et les réglages d'apparence sont sauvegardés puis remis en place.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceApi::class)
class WidgetRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val sizes = listOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 200.dp), DpSize(260.dp, 320.dp))

    /** Les tailles de la semaine et de la journée : une par niveau de détail, petites et grandes. */
    private val timetableSizes = TimetableSizes.toList()

    private fun sample(): WidgetSnapshot {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun slot(date: LocalDate, start: String, end: String, subject: String, color: Int, canceled: Boolean = false) =
            WidgetSlot(date.toString(), start, end, subject, "B204", "M. Petit", canceled, color)
        val week = (0L..4L).flatMap { offset ->
            val day = monday.plusDays(offset)
            listOf(
                slot(day, "08:00", "09:00", "Mathématiques", 0),
                slot(day, "09:00", "10:00", "Mathématiques", 0),
                slot(day, "10:15", "11:15", "Histoire-Géographie", 3),
                slot(day, "13:30", "14:30", "Anglais", 1),
                slot(day, "14:30", "15:30", "Physique-Chimie", 2, canceled = offset == 2L),
            )
        }
        return WidgetSnapshot(
            updatedAt = System.currentTimeMillis(),
            nextClass = null,
            tasks = listOf(
                WidgetTask("Maths", "Exercices 12 à 18", "Demain", 0, today.plusDays(1).toString(), false),
                WidgetTask("Anglais", "Reading", "Vendredi", 1, today.plusDays(3).toString(), true),
            ),
            lessons = week + listOf(
                slot(today, "00:01", "12:00", "Maths", 0),
                slot(today, "12:30", "23:59", "Histoire", 3),
                slot(today, "13:30", "14:30", "Annulé", 4, canceled = true),
                slot(today.plusDays(1), "08:00", "09:00", "Anglais", 1),
            ),
            grades = listOf(WidgetGradeItem("Maths", "16/20", "2", "DS 1", 0), WidgetGradeItem("Anglais", "14,5/20", "1", "Oral", 1)),
            average = "15,8",
            unread = 2,
            mails = listOf(WidgetMail("Vie scolaire", "Réunion"), WidgetMail("M. Petit", "Correction")),
        )
    }

    private fun render(widget: GlanceAppWidget, sizes: List<DpSize> = this.sizes): View = runBlocking {
        val views = widget.runComposition(context, sizes = sizes).first()
        views.apply(context, FrameLayout(context))
    }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** Sauvegarde le snapshot et les réglages d'apparence/vie privée, exécute [block], puis remet tout. */
    private fun withCleanState(block: (WidgetSnapshotStore) -> Unit) {
        val store = WidgetSnapshotStore(context)
        val previous = store.read()
        val prefs = context.getSharedPreferences(ThemeSelection.PREFS, Context.MODE_PRIVATE)
        val saved = prefs.all.filterKeys {
            it in setOf(ThemeSelection.MODE_KEY, ThemeSelection.COLOR_THEME_KEY, PrivacySelection.MODE_KEY, PrivacySelection.MESSAGES_KEY)
        }
        try {
            block(store)
        } finally {
            if (previous != null) store.write(previous) else store.clear()
            val editor = prefs.edit()
                .remove(ThemeSelection.MODE_KEY).remove(ThemeSelection.COLOR_THEME_KEY)
                .remove(PrivacySelection.MODE_KEY).remove(PrivacySelection.MESSAGES_KEY)
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            editor.commit()
        }
    }

    private fun choose(theme: ColorTheme, mode: ThemeMode) {
        context.getSharedPreferences(ThemeSelection.PREFS, Context.MODE_PRIVATE).edit()
            .putString(ThemeSelection.COLOR_THEME_KEY, theme.name).putString(ThemeSelection.MODE_KEY, mode.name).commit()
    }

    @Test
    fun chaqueWidgetSeDessineAvecEtSansDonnees() = withCleanState { store ->
        val widgets = listOf(NextClassWidget(), DayWidget(), WeekWidget(), HomeworkWidget(), GradesWidget(), MessagesWidget())
        store.clear()
        widgets.forEach { render(it) }
        store.write(sample())
        widgets.forEach { render(it) }
    }

    @Test
    fun laSemaineEtLaJourneeSeDessinentAChaqueTailleAvecEtSansDonnees() = withCleanState { store ->
        assertTrue("au moins quatre tailles", timetableSizes.size >= 4)
        store.clear()
        listOf(WeekWidget(), DayWidget()).forEach { widget -> timetableSizes.forEach { render(widget, listOf(it)) } }
        store.write(sample())
        listOf(WeekWidget(), DayWidget()).forEach { widget -> timetableSizes.forEach { render(widget, listOf(it)) } }
        // Une semaine sans le moindre cours : la grille vide ne plante pas non plus.
        store.write(sample().copy(lessons = emptyList()))
        listOf(WeekWidget(), DayWidget()).forEach { widget -> timetableSizes.forEach { render(widget, listOf(it)) } }
    }

    @Test
    fun tousLesWidgetsSuiventChaqueTheme() = withCleanState { store ->
        store.write(sample())
        val widgets = listOf(NextClassWidget(), DayWidget(), WeekWidget(), HomeworkWidget(), GradesWidget(), MessagesWidget())
        val twoSizes = listOf(DpSize(180.dp, 110.dp), DpSize(320.dp, 440.dp))
        ColorTheme.entries.forEach { theme ->
            ThemeMode.entries.forEach { mode ->
                choose(theme, mode)
                widgets.forEach { render(it, twoSizes) }
            }
        }
    }

    @Test
    fun leModeMontrerMasqueLesNotesEtLesMessagesDansLesWidgets() = withCleanState { store ->
        store.write(sample())
        val prefs = context.getSharedPreferences(ThemeSelection.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PrivacySelection.MODE_KEY, true).putBoolean(PrivacySelection.MESSAGES_KEY, true).commit()
        val grades = texts(render(GradesWidget(), listOf(DpSize(260.dp, 320.dp)))).joinToString(" ")
        assertTrue("les notes sont remplacées par •••", grades.contains("•••"))
        assertFalse("la moyenne ne doit plus apparaître", grades.contains("15,8"))
        assertFalse("les notes ne doivent plus apparaître", grades.contains("16/20"))
        val messages = texts(render(MessagesWidget(), listOf(DpSize(260.dp, 320.dp)))).joinToString(" ")
        assertFalse("l'expéditeur est masqué", messages.contains("Vie scolaire"))

        // Mode désactivé : les vraies valeurs reviennent sans nouvelle synchronisation.
        prefs.edit().putBoolean(PrivacySelection.MODE_KEY, false).commit()
        val visible = texts(render(GradesWidget(), listOf(DpSize(260.dp, 320.dp)))).joinToString(" ")
        assertTrue(visible.contains("15,8"))
    }
}
