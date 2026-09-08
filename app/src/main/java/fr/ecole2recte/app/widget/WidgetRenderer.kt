package fr.ecole2recte.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetKind
import fr.ecole2recte.app.core.widget.WidgetLayout
import fr.ecole2recte.app.core.widget.WidgetOptions
import fr.ecole2recte.app.core.widget.WidgetPayload
import fr.ecole2recte.app.core.widget.WidgetShape
import fr.ecole2recte.app.core.widget.WidgetSize
import fr.ecole2recte.app.core.widget.WidgetSizing

/**
 * Traduction d'un contenu de widget en `RemoteViews`.
 *
 * Quatre gabarits, mais pas quatre tailles : c'est la place réellement donnée par le lanceur
 * qui décide lequel est utilisé et combien de lignes il porte. Ici on ne fait que poser des
 * chaînes, des couleurs et des visibilités — ce qui s'affiche est décidé dans `core`, où
 * c'est testable sans appareil.
 *
 * Une règle traverse tout le fichier : **chaque couleur est posée par le code**, fond
 * compris, jamais laissée au XML seul. Le gabarit est inflaté par le lanceur, avec la
 * configuration du lanceur ; le jour où elle diffère de la nôtre, une couleur XML et une
 * couleur programmée cessent d'être d'accord, et le widget affiche du texte clair sur fond
 * clair. Voir [WidgetPalette].
 */
object WidgetRenderer {

    private val LINE_IDS = listOf(
        Triple(R.id.widget_row_1, R.id.widget_dot_1, R.id.widget_text_1),
        Triple(R.id.widget_row_2, R.id.widget_dot_2, R.id.widget_text_2),
        Triple(R.id.widget_row_3, R.id.widget_dot_3, R.id.widget_text_3),
        Triple(R.id.widget_row_4, R.id.widget_dot_4, R.id.widget_text_4),
        Triple(R.id.widget_row_5, R.id.widget_dot_5, R.id.widget_text_5),
        Triple(R.id.widget_row_6, R.id.widget_dot_6, R.id.widget_text_6),
        Triple(R.id.widget_row_7, R.id.widget_dot_7, R.id.widget_text_7),
        Triple(R.id.widget_row_8, R.id.widget_dot_8, R.id.widget_text_8),
    )
    private val LINE_TIME_IDS = listOf(
        R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3, R.id.widget_time_4,
        R.id.widget_time_5, R.id.widget_time_6, R.id.widget_time_7, R.id.widget_time_8,
    )
    private val LINE_VALUE_IDS = listOf(
        R.id.widget_value_1, R.id.widget_value_2, R.id.widget_value_3, R.id.widget_value_4,
        R.id.widget_value_5, R.id.widget_value_6, R.id.widget_value_7, R.id.widget_value_8,
    )
    private val SLOT_IDS = listOf(
        R.id.widget_slot_name_1 to R.id.widget_slot_value_1,
        R.id.widget_slot_name_2 to R.id.widget_slot_value_2,
        R.id.widget_slot_name_3 to R.id.widget_slot_value_3,
    )
    private val DAY_IDS = listOf(
        R.id.widget_day_name_1 to R.id.widget_day_value_1,
        R.id.widget_day_name_2 to R.id.widget_day_value_2,
        R.id.widget_day_name_3 to R.id.widget_day_value_3,
        R.id.widget_day_name_4 to R.id.widget_day_value_4,
        R.id.widget_day_name_5 to R.id.widget_day_value_5,
        R.id.widget_day_name_6 to R.id.widget_day_value_6,
    )

    fun render(
        context: Context,
        kind: WidgetKind,
        data: WidgetData.Loaded,
        options: WidgetOptions = WidgetOptions(),
        size: WidgetSize = WidgetSize.SMALL,
    ): RemoteViews {
        val layout: WidgetLayout = WidgetSizing.layout(kind, size)
        val payload = WidgetContent.payload(
            kind = kind,
            snapshot = data.snapshot,
            settings = data.settings,
            now = data.now,
            options = options,
            shape = layout.shape,
            maxLines = layout.maxLines,
        )
        val palette = WidgetPalette.of(context, data.settings)
        val views = when (layout.shape) {
            WidgetShape.TILE -> tile(context, payload, palette)
            WidgetShape.ROW -> row(context, payload, palette)
            WidgetShape.HOURS -> hours(context, payload, palette)
            WidgetShape.LIST -> list(context, payload, palette)
            WidgetShape.WEEK -> week(context, payload, palette)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, kind))
        // Une seule description pour toute la carte : sans elle, un lecteur d'écran lit les
        // vues une par une et l'information se perd dans le défilé.
        views.setContentDescription(R.id.widget_root, payload.spoken)
        return views
    }

    /**
     * La vue de secours : le nom de l'application et de quoi l'ouvrir.
     *
     * Elle n'apparaît que si le rendu normal échoue — un cache illisible, une donnée
     * inattendue. Un widget blanc laisse croire que l'application est cassée ; celui-ci dit
     * quoi faire.
     */
    fun fallback(context: Context, kind: WidgetKind, settings: AppSettings = AppSettings()): RemoteViews {
        val palette = WidgetPalette.of(context, settings)
        return RemoteViews(context.packageName, R.layout.widget_tile).apply {
            dress(palette)
            setTextViewText(R.id.widget_label, kind.label.uppercase())
            setTextViewText(R.id.widget_value, "—")
            setTextViewText(R.id.widget_caption, "Ouvre l'application")
            setTextColor(R.id.widget_label, palette.muted)
            setTextColor(R.id.widget_value, palette.ink)
            setTextColor(R.id.widget_caption, palette.muted)
            setViewVisibility(R.id.widget_progress, View.GONE)
            setOnClickPendingIntent(R.id.widget_root, openApp(context, kind))
            setContentDescription(R.id.widget_root, "${kind.label}. Ouvre l'application.")
        }
    }

    private fun tile(context: Context, payload: WidgetPayload, palette: WidgetPalette) =
        RemoteViews(context.packageName, R.layout.widget_tile).apply {
            dress(palette)
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_value, payload.title)
            setTextViewText(R.id.widget_caption, payload.subtitle)
            setTextColor(R.id.widget_label, palette.muted)
            setTextColor(R.id.widget_value, accent(payload) ?: palette.ink)
            setTextColor(R.id.widget_caption, palette.muted)
            progress(payload)
        }

    private fun row(context: Context, payload: WidgetPayload, palette: WidgetPalette) =
        RemoteViews(context.packageName, R.layout.widget_row).apply {
            dress(palette)
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_title, payload.title)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            setTextViewText(R.id.widget_trailing, payload.trailing)
            setTextColor(R.id.widget_label, palette.muted)
            setTextColor(R.id.widget_title, palette.ink)
            setTextColor(R.id.widget_subtitle, palette.muted)
            setTextColor(R.id.widget_trailing, palette.ink)
            // La barre est blanche dans le drawable : c'est le filtre qui lui donne la
            // couleur de la matière, sans un drawable par couleur.
            setInt(R.id.widget_accent, "setColorFilter", accent(payload) ?: palette.accent)
            progress(payload)
        }

    /**
     * Les trois repères de la journée.
     *
     * Le gabarit ne connaît que trois emplacements : les lignes du contenu y sont posées
     * dans l'ordre, et une ligne manquante laisse un tiret plutôt qu'un blanc — un
     * emplacement vide se lit comme une panne.
     */
    private fun hours(context: Context, payload: WidgetPayload, palette: WidgetPalette) =
        RemoteViews(context.packageName, R.layout.widget_hours).apply {
            dress(palette)
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            setTextColor(R.id.widget_label, palette.muted)
            setTextColor(R.id.widget_subtitle, palette.muted)
            SLOT_IDS.forEachIndexed { index, (nameId, valueId) ->
                val line = payload.lines.getOrNull(index)
                setTextViewText(nameId, line?.text.orEmpty())
                setTextViewText(valueId, line?.trailing?.ifBlank { "—" } ?: "—")
                setTextColor(nameId, palette.muted)
                setTextColor(valueId, palette.ink)
            }
        }

    private fun list(context: Context, payload: WidgetPayload, palette: WidgetPalette) =
        RemoteViews(context.packageName, R.layout.widget_list).apply {
            dress(palette)
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_title, payload.title)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            setTextColor(R.id.widget_label, palette.muted)
            setTextColor(R.id.widget_title, palette.ink)
            setTextColor(R.id.widget_subtitle, palette.muted)
            setTextColor(R.id.widget_empty, palette.muted)
            LINE_IDS.forEachIndexed { index, (rowId, dotId, textId) ->
                val line = payload.lines.getOrNull(index)
                if (line == null) {
                    setViewVisibility(rowId, View.GONE)
                    return@forEachIndexed
                }
                setViewVisibility(rowId, View.VISIBLE)
                // La colonne d'heure n'existe que si la ligne en a une : sans cela, les
                // listes de notes ou de devoirs perdraient une colonne de large pour rien.
                val timeId = LINE_TIME_IDS[index]
                if (line.leading.isBlank()) {
                    setViewVisibility(timeId, View.GONE)
                } else {
                    setViewVisibility(timeId, View.VISIBLE)
                    setTextViewText(timeId, line.leading)
                    setTextColor(timeId, palette.muted)
                }
                setTextViewText(textId, line.text)
                setTextViewText(
                    LINE_VALUE_IDS[index],
                    line.trailing.ifBlank { line.secondary },
                )
                setTextColor(textId, palette.ink)
                setTextColor(LINE_VALUE_IDS[index], palette.muted)
                setInt(dotId, "setColorFilter", line.accent?.toInt() ?: palette.accent)
            }
            setViewVisibility(R.id.widget_empty, if (payload.lines.isEmpty()) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_empty, payload.subtitle)
            progress(payload)
        }

    private fun week(context: Context, payload: WidgetPayload, palette: WidgetPalette) =
        RemoteViews(context.packageName, R.layout.widget_week).apply {
            dress(palette)
            setTextViewText(R.id.widget_label, payload.label)
            setTextViewText(R.id.widget_title, payload.title)
            setTextViewText(R.id.widget_subtitle, payload.subtitle)
            setTextColor(R.id.widget_label, palette.muted)
            setTextColor(R.id.widget_title, palette.ink)
            setTextColor(R.id.widget_subtitle, palette.muted)
            setTextColor(R.id.widget_empty, palette.muted)
            DAY_IDS.forEachIndexed { index, (nameId, valueId) ->
                val day = payload.days.getOrNull(index)
                setTextViewText(nameId, day?.name.orEmpty())
                setTextViewText(valueId, day?.value.orEmpty())
                // Le jour courant est la seule information mise en couleur : c'est ce qu'on
                // cherche des yeux en premier.
                val color = when {
                    day == null -> palette.muted
                    day.today -> palette.accent
                    day.empty -> palette.muted
                    else -> palette.ink
                }
                setTextColor(valueId, color)
                setTextColor(nameId, if (day?.today == true) palette.accent else palette.muted)
            }
            setViewVisibility(R.id.widget_empty, if (payload.empty) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_empty, payload.subtitle)
        }

    /** La barre d'avancement n'apparaît que pendant un cours ; sinon elle ne dirait rien. */
    private fun RemoteViews.progress(payload: WidgetPayload) {
        val progress = payload.progress
        if (progress == null) {
            setViewVisibility(R.id.widget_progress, View.GONE)
            return
        }
        setViewVisibility(R.id.widget_progress, View.VISIBLE)
        setProgressBar(R.id.widget_progress, 100, (progress * 100).toInt().coerceIn(0, 100), false)
    }

    /**
     * Pose le fond du widget.
     *
     * Le fond vient d'un drawable choisi par le code, pas d'un `values-night` que le lanceur
     * résoudrait à sa façon : c'est ce qui garantit qu'il s'accorde avec la couleur du texte.
     */
    private fun RemoteViews.dress(palette: WidgetPalette) {
        setInt(R.id.widget_root, "setBackgroundResource", palette.background)
        // La teinte est posée — ou retirée — à chaque rendu : sans le cas `null`, un widget
        // repassé du sur-mesure au thème garderait l'ancienne couleur jusqu'à sa disparition.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tint = palette.backgroundTint
            setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                if (tint == null) null else ColorStateList.valueOf(tint),
            )
        }
    }

    private fun accent(payload: WidgetPayload): Int? = payload.accent?.toInt()

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
