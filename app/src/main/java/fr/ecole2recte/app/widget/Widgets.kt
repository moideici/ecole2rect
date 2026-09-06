package fr.ecole2recte.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import fr.ecole2recte.app.core.widget.WidgetKind

/**
 * Les quinze widgets d'écran d'accueil.
 *
 * Chacun n'est qu'un nom déclaré au système et un [WidgetKind] : tout le contenu vient de
 * `core`, tout le dessin de [WidgetRenderer]. Ajouter un widget, c'est donc ajouter une
 * valeur d'énumération, une classe d'une ligne, un fichier XML et un receveur au manifeste.
 */
abstract class Ecole2recteWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val data = WidgetData.load(context)
        val options = WidgetOptionsStore(context)
        appWidgetIds.forEach { id ->
            runCatching {
                appWidgetManager.updateAppWidget(
                    id,
                    WidgetRenderer.render(context, kind, data, options.read(id)),
                )
            }
        }
    }

    /** Un widget retiré emporte ses réglages : rien ne doit survivre à sa disparition. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetOptionsStore(context).forget(appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }
}

// ── 1×1 ──────────────────────────────────────────────────────────────────────
class AverageWidget : Ecole2recteWidget(WidgetKind.AVERAGE)
class HomeworkCountWidget : Ecole2recteWidget(WidgetKind.HOMEWORK_COUNT)
class UnreadWidget : Ecole2recteWidget(WidgetKind.UNREAD)
class CountdownWidget : Ecole2recteWidget(WidgetKind.COUNTDOWN)
class NextExamWidget : Ecole2recteWidget(WidgetKind.NEXT_EXAM)
class AbsencesWidget : Ecole2recteWidget(WidgetKind.ABSENCES)

// ── 2×1 ──────────────────────────────────────────────────────────────────────
class NextLessonWidget : Ecole2recteWidget(WidgetKind.NEXT_LESSON)
class UrgentHomeworkWidget : Ecole2recteWidget(WidgetKind.URGENT_HOMEWORK)
class LastGradeWidget : Ecole2recteWidget(WidgetKind.LAST_GRADE)
class LastMessageWidget : Ecole2recteWidget(WidgetKind.LAST_MESSAGE)

// ── 2×2 ──────────────────────────────────────────────────────────────────────
class TodayWidget : Ecole2recteWidget(WidgetKind.TODAY)
class HomeworkListWidget : Ecole2recteWidget(WidgetKind.HOMEWORK_LIST)
class GradesListWidget : Ecole2recteWidget(WidgetKind.GRADES_LIST)
class SubjectAveragesWidget : Ecole2recteWidget(WidgetKind.SUBJECT_AVERAGES)

// ── 4×2 ──────────────────────────────────────────────────────────────────────
class WeekWidget : Ecole2recteWidget(WidgetKind.WEEK)

/**
 * Rafraîchissement des widgets posés sur l'écran d'accueil.
 *
 * Le système ne réveille un widget qu'une fois par demi-heure au mieux. L'application, elle,
 * sait exactement quand la donnée a changé : elle pousse donc la mise à jour dès la fin
 * d'une synchronisation ou d'un geste local (un devoir coché, une couleur changée).
 */
object WidgetUpdater {

    private val PROVIDERS: Map<WidgetKind, Class<out AppWidgetProvider>> = mapOf(
        WidgetKind.AVERAGE to AverageWidget::class.java,
        WidgetKind.HOMEWORK_COUNT to HomeworkCountWidget::class.java,
        WidgetKind.UNREAD to UnreadWidget::class.java,
        WidgetKind.COUNTDOWN to CountdownWidget::class.java,
        WidgetKind.NEXT_EXAM to NextExamWidget::class.java,
        WidgetKind.ABSENCES to AbsencesWidget::class.java,
        WidgetKind.NEXT_LESSON to NextLessonWidget::class.java,
        WidgetKind.URGENT_HOMEWORK to UrgentHomeworkWidget::class.java,
        WidgetKind.LAST_GRADE to LastGradeWidget::class.java,
        WidgetKind.LAST_MESSAGE to LastMessageWidget::class.java,
        WidgetKind.TODAY to TodayWidget::class.java,
        WidgetKind.HOMEWORK_LIST to HomeworkListWidget::class.java,
        WidgetKind.GRADES_LIST to GradesListWidget::class.java,
        WidgetKind.SUBJECT_AVERAGES to SubjectAveragesWidget::class.java,
        WidgetKind.WEEK to WeekWidget::class.java,
    )

    fun updateAll(context: Context) {
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
        val data = WidgetData.load(context)
        PROVIDERS.forEach { (kind, provider) ->
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, provider))
            }.getOrNull() ?: IntArray(0)
            if (ids.isEmpty()) return@forEach
            val options = WidgetOptionsStore(context)
            ids.forEach { id ->
                val views = runCatching {
                    WidgetRenderer.render(context, kind, data, options.read(id))
                }.getOrNull()
                if (views != null) runCatching { manager.updateAppWidget(id, views) }
            }
        }
    }
}
