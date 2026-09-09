package fr.ecole2recte.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetOptions
import fr.ecole2recte.app.core.widget.WidgetSize

/**
 * Les neuf widgets d'écran d'accueil.
 *
 * Chacun n'est qu'un nom déclaré au système et un [WidgetKind] : tout le contenu vient de
 * `core`, tout le dessin de [WidgetRenderer]. Ajouter un widget, c'est donc ajouter une
 * valeur d'énumération, une classe d'une ligne, un fichier XML et un receveur au manifeste.
 *
 * Le seul travail sérieux de cette classe est la **taille**. Un widget Android est
 * redimensionnable : l'utilisateur l'étire, et le lanceur annonce la place accordée dans un
 * `Bundle`. Un widget qui ignore ce bundle affiche la même chose sur une case et sur un quart
 * d'écran — c'est-à-dire du vide. On lit donc la place réelle, et le contenu s'y ajuste.
 */
abstract class Ecole2recteWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = WidgetOptionsStore(context)
        // Deux widgets peuvent suivre deux enfants différents : le cache est donc relu par
        // profil, mais une seule fois pour tous les widgets qui partagent le même.
        val caches = mutableMapOf<String?, WidgetData.Loaded>()
        appWidgetIds.forEach { id ->
            runCatching {
                val options = store.read(id)
                val data = caches.getOrPut(options.accountId) { WidgetData.load(context, options.accountId) }
                appWidgetManager.updateAppWidget(
                    id,
                    views(context, data, options, appWidgetManager.getAppWidgetOptions(id)),
                )
            }
        }
    }

    /**
     * Le lanceur prévient quand le widget est redimensionné : c'est le moment de recalculer,
     * sinon un widget agrandi garderait le contenu de sa petite taille jusqu'au prochain
     * réveil, une demi-heure plus tard.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val stored = WidgetOptionsStore(context).read(appWidgetId)
        val data = WidgetData.load(context, stored.accountId)
        runCatching {
            appWidgetManager.updateAppWidget(appWidgetId, views(context, data, stored, newOptions))
        }
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    /** Un widget retiré emporte ses réglages : rien ne doit survivre à sa disparition. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetOptionsStore(context).forget(appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    private fun views(
        context: Context,
        data: WidgetData.Loaded,
        options: WidgetOptions,
        bundle: Bundle?,
    ): RemoteViews = WidgetSizes.remoteViews(context, kind, data, options, bundle)
}

/**
 * La place accordée à un widget, telle que le système la décrit.
 *
 * Le lanceur annonce la place dans un `Bundle` : à partir d'Android 12 la liste des tailles
 * auxquelles le widget peut apparaître (`OPTION_APPWIDGET_SIZES`), avant cela un minimum en
 * largeur et en hauteur pour chaque orientation.
 *
 * On pourrait fournir un rendu par taille — c'est ce que permet `RemoteViews(Map<SizeF,
 * RemoteViews>)`, et cela évite de réveiller l'application à chaque redimensionnement. Ce
 * n'est pas ce qu'on fait ici, et c'est un choix : un seul rendu, celui de la taille
 * courante, recalculé quand le lanceur signale un changement. C'est la voie « exact
 * layouts » de la documentation, elle se comporte de la même façon sur toutes les versions
 * d'Android, et surtout elle est vérifiable — une carte de rendus hétérogènes ne se
 * diagnostique qu'un appareil à la main.
 */
object WidgetSizes {

    fun remoteViews(
        context: Context,
        kind: WidgetKind,
        data: WidgetData.Loaded,
        options: WidgetOptions,
        bundle: Bundle?,
    ): RemoteViews = runCatching {
        WidgetRenderer.render(context, kind, data, options, size(bundle))
    }.getOrElse {
        // Un widget ne doit jamais rester blanc : si le rendu échoue, on pose une vue
        // minimale qui ouvre l'application plutôt qu'une carte vide et muette.
        WidgetRenderer.fallback(context, kind, data.settings)
    }

    /**
     * La taille à retenir : **la largeur la plus étroite, la hauteur la plus grande**.
     *
     * Le lanceur annonce une taille par orientation. Prendre la plus petite des deux, comme
     * on le faisait, revenait à dessiner pour le paysage — beaucoup plus bas — et laissait
     * la moitié du widget vide en portrait, c'est-à-dire dans l'orientation où il est
     * regardé. Prendre la plus grande partout ferait déborder le texte quand l'écran tourne.
     *
     * La combinaison des deux est celle qui tient dans les deux cas : la largeur du plus
     * étroit garantit qu'aucune ligne n'est coupée, la hauteur du plus haut remplit l'espace
     * réellement disponible en portrait. Une ligne de trop, si l'écran tourne, est simplement
     * rognée par le bord — un moindre mal devant la moitié d'un widget laissée blanche.
     */
    fun size(bundle: Bundle?): WidgetSize {
        val options = bundle ?: return WidgetSize.SMALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val declared = runCatching {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            }.getOrNull().orEmpty()
            val width = declared.minOfOrNull { it.width }
            val height = declared.maxOfOrNull { it.height }
            if (width != null && height != null) return WidgetSize(width.toInt(), height.toInt())
        }
        // Repli d'avant Android 12 : `MIN_WIDTH` est la largeur en portrait, `MAX_HEIGHT`
        // la hauteur en portrait. Ce sont les deux que l'on veut.
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val height = if (maxHeight > 0) maxHeight else minHeight
        if (minWidth <= 0 || height <= 0) return WidgetSize.SMALL
        return WidgetSize(minWidth, height)
    }
}

class NextLessonWidget : Ecole2recteWidget(WidgetKind.NEXT_LESSON)
class DayWidget : Ecole2recteWidget(WidgetKind.DAY)
class WeekWidget : Ecole2recteWidget(WidgetKind.WEEK)
class HomeworkWidget : Ecole2recteWidget(WidgetKind.HOMEWORK)
class GradesWidget : Ecole2recteWidget(WidgetKind.GRADES)
class ObjectiveWidget : Ecole2recteWidget(WidgetKind.OBJECTIVE)
class SchoolLifeWidget : Ecole2recteWidget(WidgetKind.SCHOOL_LIFE)
class ChangesWidget : Ecole2recteWidget(WidgetKind.CHANGES)
class TomorrowWidget : Ecole2recteWidget(WidgetKind.TOMORROW)

/**
 * Rafraîchissement des widgets posés sur l'écran d'accueil.
 *
 * Le système ne réveille un widget qu'une fois par demi-heure au mieux. L'application, elle,
 * sait exactement quand la donnée a changé : elle pousse donc la mise à jour dès la fin
 * d'une synchronisation ou d'un geste local (un devoir coché, une couleur changée).
 */
object WidgetUpdater {

    private val PROVIDERS: Map<WidgetKind, Class<out AppWidgetProvider>> = mapOf(
        WidgetKind.NEXT_LESSON to NextLessonWidget::class.java,
        WidgetKind.DAY to DayWidget::class.java,
        WidgetKind.WEEK to WeekWidget::class.java,
        WidgetKind.HOMEWORK to HomeworkWidget::class.java,
        WidgetKind.GRADES to GradesWidget::class.java,
        WidgetKind.OBJECTIVE to ObjectiveWidget::class.java,
        WidgetKind.SCHOOL_LIFE to SchoolLifeWidget::class.java,
        WidgetKind.CHANGES to ChangesWidget::class.java,
        WidgetKind.TOMORROW to TomorrowWidget::class.java,
    )

    fun updateAll(context: Context) {
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
        val store = WidgetOptionsStore(context)
        val caches = mutableMapOf<String?, WidgetData.Loaded>()
        PROVIDERS.forEach { (kind, provider) ->
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, provider))
            }.getOrNull() ?: IntArray(0)
            if (ids.isEmpty()) return@forEach
            ids.forEach { id ->
                val views = runCatching {
                    val options = store.read(id)
                    WidgetSizes.remoteViews(
                        context = context,
                        kind = kind,
                        data = caches.getOrPut(options.accountId) {
                            WidgetData.load(context, options.accountId)
                        },
                        options = options,
                        bundle = manager.getAppWidgetOptions(id),
                    )
                }.getOrNull()
                if (views != null) runCatching { manager.updateAppWidget(id, views) }
            }
        }
    }

    /** Le widget correspondant à une classe de receveur : l'écran de configuration en a besoin. */
    fun kindOf(className: String?): WidgetKind? =
        PROVIDERS.entries.firstOrNull { it.value.name == className }?.key
}
