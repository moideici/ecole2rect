package fr.ecole2recte.app

import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.runComposition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.ecole2recte.app.sync.WidgetGradeItem
import fr.ecole2recte.app.sync.WidgetMail
import fr.ecole2recte.app.sync.WidgetSlot
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore
import fr.ecole2recte.app.sync.WidgetTask
import fr.ecole2recte.app.widget.DayWidget
import fr.ecole2recte.app.widget.GradesWidget
import fr.ecole2recte.app.widget.HomeworkWidget
import fr.ecole2recte.app.widget.MessagesWidget
import fr.ecole2recte.app.widget.NextClassWidget
import fr.ecole2recte.app.widget.WeekWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Fait dessiner chaque widget à toutes ses tailles, avec et sans données : un widget qui plante au
 * dessin (la cause du « widget qui ne s'affiche pas ») fait échouer ce test. L'instantané réel de
 * l'application est sauvegardé puis remis en place.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceApi::class)
class WidgetRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val sizes = listOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 200.dp), DpSize(260.dp, 320.dp))

    private fun sample(): WidgetSnapshot {
        val today = LocalDate.now()
        fun slot(date: LocalDate, start: String, end: String, subject: String, color: Int, canceled: Boolean = false) =
            WidgetSlot(date.toString(), start, end, subject, "B204", "M. Petit", canceled, color)
        return WidgetSnapshot(
            updatedAt = System.currentTimeMillis(),
            nextClass = null,
            tasks = listOf(
                WidgetTask("Maths", "Exercices 12 à 18", "Demain", 0, today.plusDays(1).toString(), false),
                WidgetTask("Anglais", "Reading", "Vendredi", 1, today.plusDays(3).toString(), true),
            ),
            lessons = listOf(
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

    private fun render(widget: GlanceAppWidget) = runBlocking {
        val views = widget.runComposition(context, sizes = sizes).first()
        views.apply(context, FrameLayout(context))
    }

    @Test
    fun chaqueWidgetSeDessineAvecEtSansDonnees() {
        val store = WidgetSnapshotStore(context)
        val previous = store.read()
        try {
            val widgets = listOf(NextClassWidget(), DayWidget(), WeekWidget(), HomeworkWidget(), GradesWidget(), MessagesWidget())
            store.clear()
            widgets.forEach { render(it) }
            store.write(sample())
            widgets.forEach { render(it) }
        } finally {
            if (previous != null) store.write(previous) else store.clear()
        }
    }
}
