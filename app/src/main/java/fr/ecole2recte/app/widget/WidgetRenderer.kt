package fr.ecole2recte.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetPayload
import fr.ecole2recte.app.core.widget.WidgetShape

/**
 * Traduction d'un contenu de widget en `RemoteViews`.
 *
 * Quatre gabarits pour quinze widgets : le contenu est décidé dans `core`, ici on ne fait
 * que poser des chaînes, des couleurs et des visibilités. `RemoteViews` ne sait pas répéter
 * une vue, d'où les identifiants numérotés des lignes et des jours.
 */
object WidgetRenderer {

    private val LINE_IDS = listOf(
        Triple(R.id.widget_row_1, R.id.widget_dot_1, R.id.widget_text_1),
        Triple(R.id.widget_row_2, R.id.widget_dot_2, R.id.widget_text_2),
        Triple(R.id.widget_row_3, R.id.widget_dot_3, R.id.widget_text_3),
        Triple(R.id.widget_row_4, R.id.widget_dot_4, R.id.widget_text_4),
    )
    private val LINE_VALUE_IDS = listOf(
        R.id.widget_value_1, R.id.widget_value_2, R.id.widget_value_3, R.id.widget_value_4,
    )
    private val DAY_IDS = listOf(
        R.id.widget_day_name_1 to R.id.widget_day_value_1,
        R.id.widget_day_name_2 to R.id.widget_day_value_2,
        R.id.widget_day_name_3 to R.id.widget_day_value_3,
        R.id.widget_day_name_4 to R.id.widget_day_value_4,
        R.id.widget_day_name_5 to R.id.widget_day_value_5,
        R.id.widget_day_name_6 to R.id.widget_day_value_6,
    )

    fun render(context: Context, kind: WidgetKind, data: WidgetData.Loaded): RemoteViews {
        val payload = WidgetContent.payload(kind, data.snapshot, data.settings, data.now)
        val views = when (kind.shape) {
            WidgetShape.TILE -> tile(context, payload)
            WidgetShape.ROW -> row(context, payload)
            WidgetShape.LIST -> list(context, payload)
            WidgetShape.WEEK -> week(context, payload)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, kind))
        return views
    }

    private fun tile(context: Context, payload: WidgetPayload) =
        RemoteViews(context.packageName, R.layout.widget_tile).apply {
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_value, payload.title)
            setTextViewText(R.id.widget_caption, payload.subtitle)
            setTextColor(R.id.widget_value, accent(payload) ?: ink(context))
        }

    private fun row(context: Context, payload: WidgetPayload) =
        RemoteViews(context.packageName, R.layout.widget_row).apply {
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_title, payload.title)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            setTextViewText(R.id.widget_trailing, payload.trailing)
            // La barre est blanche dans le drawable : c'est le filtre qui lui donne la
            // couleur de la matière, sans un drawable par couleur.
            setInt(R.id.widget_accent, "setColorFilter", accent(payload) ?: defaultAccent(context))
        }

    private fun list(context: Context, payload: WidgetPayload) =
        RemoteViews(context.packageName, R.layout.widget_list).apply {
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_title, payload.title)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            LINE_IDS.forEachIndexed { index, (rowId, dotId, textId) ->
                val line = payload.lines.getOrNull(index)
                if (line == null) {
                    setViewVisibility(rowId, View.GONE)
                    return@forEachIndexed
                }
                setViewVisibility(rowId, View.VISIBLE)
                setTextViewText(textId, line.text)
                setTextViewText(
                    LINE_VALUE_IDS[index],
                    line.trailing.ifBlank { line.secondary },
                )
                setInt(dotId, "setColorFilter", line.accent?.toInt() ?: defaultAccent(context))
            }
            setViewVisibility(R.id.widget_empty, if (payload.lines.isEmpty()) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_empty, payload.subtitle)
        }

    private fun week(context: Context, payload: WidgetPayload) =
        RemoteViews(context.packageName, R.layout.widget_week).apply {
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_title, payload.title)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            DAY_IDS.forEachIndexed { index, (nameId, valueId) ->
                val day = payload.days.getOrNull(index)
                setTextViewText(nameId, day?.name.orEmpty())
                setTextViewText(valueId, day?.value.orEmpty())
                // Le jour courant est la seule information mise en couleur : c'est ce qu'on
                // cherche des yeux en premier.
                val color = when {
                    day == null -> muted(context)
                    day.today -> defaultAccent(context)
                    day.empty -> muted(context)
                    else -> ink(context)
                }
                setTextColor(valueId, color)
                setTextColor(nameId, if (day?.today == true) defaultAccent(context) else muted(context))
            }
            setViewVisibility(R.id.widget_empty, if (payload.empty) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_empty, payload.subtitle)
        }

    private fun accent(payload: WidgetPayload): Int? = payload.accent?.toInt()

    private fun defaultAccent(context: Context): Int = context.getColor(R.color.widget_accent)

    private fun ink(context: Context): Int = context.getColor(R.color.widget_ink)

    private fun muted(context: Context): Int = context.getColor(R.color.widget_muted)

    /** Toucher un widget ouvre l'écran correspondant, pas seulement l'application. */
    private fun openApp(context: Context, kind: WidgetKind): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, kind.target.name)
        }
        return PendingIntent.getActivity(
            context,
            kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
