package fr.ecole2recte.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.widget.WidgetBlock
import fr.ecole2recte.app.core.widget.WidgetContent
import fr.ecole2recte.app.core.widget.WidgetGrid
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
    private val LINE_SECONDARY_IDS = listOf(
        R.id.widget_secondary_1, R.id.widget_secondary_2, R.id.widget_secondary_3, R.id.widget_secondary_4,
        R.id.widget_secondary_5, R.id.widget_secondary_6, R.id.widget_secondary_7, R.id.widget_secondary_8,
    )
    private val SLOT_IDS = listOf(
        R.id.widget_slot_name_1 to R.id.widget_slot_value_1,
        R.id.widget_slot_name_2 to R.id.widget_slot_value_2,
        R.id.widget_slot_name_3 to R.id.widget_slot_value_3,
    )

    /** Les douze emplacements de la grille : conteneur, barre de matière, trois niveaux. */
    private val GRID_IDS = listOf(
        GridSlot(R.id.grid_slot_1, R.id.grid_bar_1, R.id.grid_text_1, R.id.grid_meta_1, R.id.grid_detail_1),
        GridSlot(R.id.grid_slot_2, R.id.grid_bar_2, R.id.grid_text_2, R.id.grid_meta_2, R.id.grid_detail_2),
        GridSlot(R.id.grid_slot_3, R.id.grid_bar_3, R.id.grid_text_3, R.id.grid_meta_3, R.id.grid_detail_3),
        GridSlot(R.id.grid_slot_4, R.id.grid_bar_4, R.id.grid_text_4, R.id.grid_meta_4, R.id.grid_detail_4),
        GridSlot(R.id.grid_slot_5, R.id.grid_bar_5, R.id.grid_text_5, R.id.grid_meta_5, R.id.grid_detail_5),
        GridSlot(R.id.grid_slot_6, R.id.grid_bar_6, R.id.grid_text_6, R.id.grid_meta_6, R.id.grid_detail_6),
        GridSlot(R.id.grid_slot_7, R.id.grid_bar_7, R.id.grid_text_7, R.id.grid_meta_7, R.id.grid_detail_7),
        GridSlot(R.id.grid_slot_8, R.id.grid_bar_8, R.id.grid_text_8, R.id.grid_meta_8, R.id.grid_detail_8),
        GridSlot(R.id.grid_slot_9, R.id.grid_bar_9, R.id.grid_text_9, R.id.grid_meta_9, R.id.grid_detail_9),
        GridSlot(R.id.grid_slot_10, R.id.grid_bar_10, R.id.grid_text_10, R.id.grid_meta_10, R.id.grid_detail_10),
        GridSlot(R.id.grid_slot_11, R.id.grid_bar_11, R.id.grid_text_11, R.id.grid_meta_11, R.id.grid_detail_11),
        GridSlot(R.id.grid_slot_12, R.id.grid_bar_12, R.id.grid_text_12, R.id.grid_meta_12, R.id.grid_detail_12),
    )

    private data class GridSlot(val root: Int, val bar: Int, val text: Int, val meta: Int, val detail: Int)

    /** Les six colonnes de la semaine : conteneur, nom du jour, neuf blocs. */
    private val WEEK_COLUMN_IDS = listOf(
        R.id.week_column_1, R.id.week_column_2, R.id.week_column_3,
        R.id.week_column_4, R.id.week_column_5, R.id.week_column_6,
    )
    private val WEEK_DAY_IDS = listOf(
        R.id.week_day_1, R.id.week_day_2, R.id.week_day_3,
        R.id.week_day_4, R.id.week_day_5, R.id.week_day_6,
    )
    private val WEEK_BLOCK_IDS = listOf(
        listOf(
            R.id.week_1_1, R.id.week_1_2, R.id.week_1_3,
            R.id.week_1_4, R.id.week_1_5, R.id.week_1_6,
            R.id.week_1_7, R.id.week_1_8, R.id.week_1_9,
        ),
        listOf(
            R.id.week_2_1, R.id.week_2_2, R.id.week_2_3,
            R.id.week_2_4, R.id.week_2_5, R.id.week_2_6,
            R.id.week_2_7, R.id.week_2_8, R.id.week_2_9,
        ),
        listOf(
            R.id.week_3_1, R.id.week_3_2, R.id.week_3_3,
            R.id.week_3_4, R.id.week_3_5, R.id.week_3_6,
            R.id.week_3_7, R.id.week_3_8, R.id.week_3_9,
        ),
        listOf(
            R.id.week_4_1, R.id.week_4_2, R.id.week_4_3,
            R.id.week_4_4, R.id.week_4_5, R.id.week_4_6,
            R.id.week_4_7, R.id.week_4_8, R.id.week_4_9,
        ),
        listOf(
            R.id.week_5_1, R.id.week_5_2, R.id.week_5_3,
            R.id.week_5_4, R.id.week_5_5, R.id.week_5_6,
            R.id.week_5_7, R.id.week_5_8, R.id.week_5_9,
        ),
        listOf(
            R.id.week_6_1, R.id.week_6_2, R.id.week_6_3,
            R.id.week_6_4, R.id.week_6_5, R.id.week_6_6,
            R.id.week_6_7, R.id.week_6_8, R.id.week_6_9,
        ),
    )

    fun render(
        context: Context,
        kind: WidgetKind,
        data: WidgetData.Loaded,
        options: WidgetOptions = WidgetOptions(),
        size: WidgetSize = WidgetSize.SMALL,
    ): RemoteViews {
        // Une hauteur exacte sur une vue distante n'existe qu'à partir d'Android 12 : en
        // deçà, les grilles redeviennent des listes plutôt que des blocs tous égaux, qui
        // mentiraient sur la durée des cours.
        val proportional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val layout: WidgetLayout = WidgetSizing.layout(kind, size, proportional)
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
            // `shapeFor` ne rend ces deux formes qu'avec `proportional`, donc jamais avant
            // Android 12 ; le compilateur ne le sait pas, et l'analyse de version a besoin
            // du test écrit ici pour autoriser `setViewLayoutHeight`. Le repli n'est donc
            // pas atteignable, mais il est juste.
            WidgetShape.GRID -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                grid(context, payload, palette, size)
            } else {
                list(context, payload, palette)
            }

            WidgetShape.LIST -> list(context, payload, palette)
            WidgetShape.WEEK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                week(context, payload, palette, size)
            } else {
                list(context, payload, palette)
            }
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
                setTextColor(textId, palette.ink)
                // Le second niveau est ce que les listes ne montraient pas : l'échéance, le
                // coefficient, l'expéditeur. Il disparaît quand la ligne n'a rien à y mettre,
                // plutôt que de laisser une ligne vide sous chaque titre.
                val secondaryId = LINE_SECONDARY_IDS[index]
                if (line.secondary.isBlank()) {
                    setViewVisibility(secondaryId, View.GONE)
                } else {
                    setViewVisibility(secondaryId, View.VISIBLE)
                    setTextViewText(secondaryId, line.secondary)
                    setTextColor(secondaryId, palette.muted)
                }
                setTextViewText(LINE_VALUE_IDS[index], line.trailing)
                setTextColor(LINE_VALUE_IDS[index], palette.muted)
                setInt(dotId, "setColorFilter", line.accent?.toInt() ?: palette.accent)
            }
            setViewVisibility(R.id.widget_empty, if (payload.lines.isEmpty()) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_empty, payload.subtitle)
            progress(payload)
        }

    /**
     * La journée dessinée à l'échelle du temps.
     *
     * Chaque bloc reçoit une hauteur exacte, calculée dans `core` à partir de sa durée. Un
     * cours de deux heures fait deux fois la hauteur d'un cours d'une heure, la pause occupe
     * la place qu'elle prend vraiment, et la grille descend jusqu'au bas de la carte — c'est
     * la réponse au reproche fait à l'ancien 2×2, qui empilait quatre lignes et laissait la
     * moitié du widget en blanc.
     *
     * `setViewLayoutHeight` n'existe qu'à partir d'Android 12 ; en deçà, cette forme n'est
     * jamais choisie (voir [WidgetSizing.shapeFor]), et l'appel est donc toujours légal ici.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun grid(
        context: Context,
        payload: WidgetPayload,
        palette: WidgetPalette,
        size: WidgetSize,
    ) = RemoteViews(context.packageName, R.layout.widget_grid).apply {
        dress(palette)
        setTextViewText(R.id.widget_label, payload.label)
        setTextViewText(R.id.widget_title, payload.title)
        setTextViewText(R.id.widget_subtitle, payload.trailing.ifBlank { payload.subtitle })
        setTextColor(R.id.widget_label, palette.muted)
        setTextColor(R.id.widget_title, palette.ink)
        setTextColor(R.id.widget_subtitle, palette.muted)
        setTextColor(R.id.widget_empty, palette.muted)

        val blocks = WidgetGrid.fit(payload.blocks, GRID_IDS.size)
        // Les marges entre blocs sont retirées de la place à partager : les oublier ferait
        // déborder la grille d'une vingtaine de points, et le dernier cours de la journée
        // serait rogné par le bord — exactement ce qu'on cherche à éviter.
        val available = (WidgetSizing.contentHeightDp(size) - blocks.size * GRID_SLOT_MARGIN_DP)
            .coerceAtLeast(0)
        val heights = WidgetGrid.heights(blocks, available)
        GRID_IDS.forEachIndexed { index, slot ->
            val block = blocks.getOrNull(index)
            val height = heights.getOrNull(index) ?: 0
            val levels = if (block == null || block.blank) 0 else WidgetGrid.levels(height)
            if (block == null || height <= 0) {
                setViewVisibility(slot.root, View.GONE)
                return@forEachIndexed
            }
            setViewVisibility(slot.root, View.VISIBLE)
            setViewLayoutHeight(slot.root, height.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            paint(slot, block, palette, levels)
        }
        setViewVisibility(R.id.widget_empty, if (blocks.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_empty, payload.subtitle)
    }

    /** Un bloc : sa couleur de matière, et les niveaux de texte que sa hauteur autorise. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun RemoteViews.paint(
        slot: GridSlot,
        block: WidgetBlock,
        palette: WidgetPalette,
        levels: Int,
    ) {
        val accent = block.accent?.toInt() ?: palette.accent
        // Un trou n'est pas une matière : fond neutre et barre effacée, sinon une pause
        // ressemblerait à un cours et la journée perdrait sa respiration.
        val fill = if (block.free || block.blank) {
            WidgetPalette.fade(palette.muted, palette.surface, FREE_BLOCK_FADE)
        } else {
            WidgetPalette.block(accent, palette.surface)
        }
        setColorStateList(slot.root, "setBackgroundTintList", ColorStateList.valueOf(fill))
        // La barre est blanche dans le drawable : c'est le filtre qui lui donne la couleur
        // de la matière, sans exiger un drawable par couleur.
        setInt(slot.bar, "setColorFilter", if (block.free || block.blank) fill else accent)
        setViewVisibility(slot.text, if (levels >= 1 && block.text.isNotBlank()) View.VISIBLE else View.GONE)
        setTextViewText(slot.text, block.text)
        setTextColor(slot.text, if (block.free) palette.muted else palette.ink)
        setViewVisibility(slot.meta, if (levels >= 2 && block.secondary.isNotBlank()) View.VISIBLE else View.GONE)
        setTextViewText(slot.meta, block.secondary)
        setTextColor(slot.meta, palette.muted)
        setViewVisibility(slot.detail, if (levels >= 3 && block.detail.isNotBlank()) View.VISIBLE else View.GONE)
        setTextViewText(slot.detail, block.detail)
        setTextColor(slot.detail, palette.muted)
    }

    /**
     * La semaine : cinq ou six grilles côte à côte, toutes sur la même échelle.
     *
     * L'ancien gabarit donnait un chiffre par jour — le nombre d'heures — ce qui est un
     * tableau, pas un emploi du temps. Ici la forme de la semaine se lit d'un regard, et
     * c'est le remplissage en tête et en pied de chaque colonne, calculé dans `core`, qui
     * garantit que les journées restent alignées entre elles.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun week(
        context: Context,
        payload: WidgetPayload,
        palette: WidgetPalette,
        size: WidgetSize,
    ) = RemoteViews(context.packageName, R.layout.widget_week).apply {
        dress(palette)
        setTextViewText(R.id.widget_label, payload.label)
        setTextViewText(R.id.widget_title, payload.title)
        setTextViewText(R.id.widget_subtitle, payload.trailing.ifBlank { payload.subtitle })
        setTextColor(R.id.widget_label, palette.muted)
        setTextColor(R.id.widget_title, palette.ink)
        setTextColor(R.id.widget_subtitle, palette.muted)
        setTextColor(R.id.widget_empty, palette.muted)

        val available = WidgetSizing.columnHeightDp(size)
        WEEK_COLUMN_IDS.forEachIndexed { index, columnId ->
            val column = payload.columns.getOrNull(index)
            if (column == null) {
                setViewVisibility(columnId, View.GONE)
                return@forEachIndexed
            }
            setViewVisibility(columnId, View.VISIBLE)
            setTextViewText(WEEK_DAY_IDS[index], column.name)
            // Le jour courant est la seule chose mise en couleur : c'est ce qu'on cherche
            // des yeux en premier, et une semaine où tout est accentué n'accentue rien.
            setTextColor(WEEK_DAY_IDS[index], if (column.today) palette.accent else palette.muted)

            val slots = WEEK_BLOCK_IDS[index]
            val blocks = WidgetGrid.fit(column.blocks, slots.size)
            val heights = WidgetGrid.heights(
                blocks,
                (available - blocks.size * WEEK_SLOT_MARGIN_DP).coerceAtLeast(0),
                WEEK_MIN_BLOCK_DP,
            )
            slots.forEachIndexed { position, blockId ->
                val block = blocks.getOrNull(position)
                val height = heights.getOrNull(position) ?: 0
                if (block == null || height <= 0) {
                    setViewVisibility(blockId, View.GONE)
                    return@forEachIndexed
                }
                setViewVisibility(blockId, View.VISIBLE)
                setViewLayoutHeight(blockId, height.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                if (block.blank) {
                    // Un espace nu garde sa place — c'est lui qui aligne les colonnes — mais
                    // n'affiche ni fond ni texte : un trou doit se voir comme un trou.
                    setColorStateList(blockId, "setBackgroundTintList", ColorStateList.valueOf(palette.surface))
                    setTextViewText(blockId, "")
                    return@forEachIndexed
                }
                val accent = block.accent?.toInt() ?: palette.accent
                val fill = if (block.free) {
                    WidgetPalette.fade(palette.muted, palette.surface, FREE_BLOCK_FADE)
                } else {
                    WidgetPalette.block(accent, palette.surface)
                }
                setColorStateList(blockId, "setBackgroundTintList", ColorStateList.valueOf(fill))
                setTextViewText(blockId, block.text)
                setTextColor(blockId, if (block.free) palette.muted else palette.ink)
            }
        }
        setViewVisibility(R.id.widget_empty, if (payload.columns.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_empty, payload.subtitle)
    }

    /** Un trou tire vers le fond : il occupe sa place sans réclamer le regard. */
    private const val FREE_BLOCK_FADE = 0.88f

    /** Une colonne de semaine est étroite : ses blocs peuvent descendre plus bas qu'ailleurs. */
    private const val WEEK_MIN_BLOCK_DP = 10

    /** Marges déclarées dans les gabarits : elles se retirent de la hauteur à partager. */
    private const val GRID_SLOT_MARGIN_DP = 2
    private const val WEEK_SLOT_MARGIN_DP = 1

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
