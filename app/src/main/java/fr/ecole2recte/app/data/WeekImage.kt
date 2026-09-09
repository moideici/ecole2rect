package fr.ecole2recte.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import fr.ecole2recte.app.core.widget.WidgetColumn
import java.io.File

/**
 * Une semaine d'emploi du temps en image, à partager.
 *
 * L'usage est banal et l'application ne le permettait pas : envoyer son emploi du temps à
 * quelqu'un. Une capture d'écran donne une image tronquée, aux couleurs du téléphone, avec la
 * barre de notification en haut. Ici l'image est dessinée pour être lue par un tiers — la
 * semaine entière, à l'échelle du temps, sur un fond propre.
 *
 * Les colonnes viennent du même calcul que le widget de semaine : la mise en page d'une
 * semaine se décide à un seul endroit. Ce fichier ne fait que peindre.
 *
 * Aucune donnée personnelle n'y figure au-delà de ce que l'image montre : ni nom d'élève, ni
 * établissement, ni identifiant. Ce qu'on partage, c'est un emploi du temps, pas un profil.
 */
class WeekImage(private val context: Context) {

    fun share(columns: List<WidgetColumn>, title: String, subtitle: String): Intent? {
        if (columns.isEmpty()) return null
        val bitmap = runCatching { render(columns, title, subtitle) }.getOrNull() ?: return null
        val directory = File(context.cacheDir, "pieces-jointes").apply { mkdirs() }
        val file = File(directory, "semaine.png")
        val written = runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        bitmap.recycle()
        if (!written) return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Le dessin.
     *
     * Les dimensions sont fixes et généreuses : une image partagée est regardée sur l'écran
     * de quelqu'un d'autre, souvent plus petit, et parfois recompressée par la messagerie qui
     * la transporte. Dessiner large et laisser réduire vaut mieux que l'inverse.
     */
    private fun render(columns: List<WidgetColumn>, title: String, subtitle: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = TITLE_COLOR
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 46f
        canvas.drawText(title, MARGIN, MARGIN + 46f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = MUTED_COLOR
        paint.textSize = 30f
        canvas.drawText(subtitle, MARGIN, MARGIN + 92f, paint)

        val top = MARGIN + 130f
        val bottom = HEIGHT - MARGIN
        val gridHeight = bottom - top - DAY_LABEL_HEIGHT
        val columnWidth = (WIDTH - 2 * MARGIN - COLUMN_GAP * (columns.size - 1)) / columns.size

        columns.forEachIndexed { index, column ->
            val left = MARGIN + index * (columnWidth + COLUMN_GAP)

            paint.color = if (column.today) ACCENT_COLOR else MUTED_COLOR
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 28f
            val label = column.name.uppercase()
            canvas.drawText(label, left + (columnWidth - paint.measureText(label)) / 2, top + 28f, paint)

            // Chaque colonne partage la même échelle : c'est le remplissage calculé dans
            // `core` qui garantit que les journées restent alignées entre elles.
            val total = column.blocks.sumOf { it.minutes }.coerceAtLeast(1)
            var y = top + DAY_LABEL_HEIGHT
            column.blocks.forEach { block ->
                val height = gridHeight * block.minutes / total
                if (block.blank || height < 1f) {
                    y += height
                    return@forEach
                }
                val rect = RectF(left, y, left + columnWidth, y + height - BLOCK_GAP)
                paint.color = if (block.free) FREE_COLOR else tint(block.accent?.toInt() ?: ACCENT_COLOR)
                canvas.drawRoundRect(rect, RADIUS, RADIUS, paint)

                if (!block.free) {
                    paint.color = block.accent?.toInt() ?: ACCENT_COLOR
                    val bar = RectF(left, y, left + BAR_WIDTH, y + height - BLOCK_GAP)
                    canvas.drawRoundRect(bar, RADIUS, RADIUS, paint)
                }

                paint.color = if (block.free) MUTED_COLOR else TITLE_COLOR
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 26f
                // Un bloc trop court n'accueille pas de texte : mieux vaut une bande muette
                // qu'un mot coupé au milieu des jambages.
                if (height >= 34f) {
                    canvas.drawText(
                        ellipsize(block.text, paint, columnWidth - BAR_WIDTH - 2 * PADDING),
                        left + BAR_WIDTH + PADDING,
                        y + 30f,
                        paint,
                    )
                }
                if (height >= 62f && block.secondary.isNotBlank()) {
                    paint.typeface = Typeface.DEFAULT
                    paint.color = MUTED_COLOR
                    paint.textSize = 22f
                    canvas.drawText(
                        ellipsize(block.secondary, paint, columnWidth - BAR_WIDTH - 2 * PADDING),
                        left + BAR_WIDTH + PADDING,
                        y + 58f,
                        paint,
                    )
                }
                y += height
            }
        }
        return bitmap
    }

    /** Le fond d'un bloc : la couleur de la matière ramenée vers le blanc, comme un surligneur. */
    private fun tint(color: Int): Int {
        fun blend(shift: Int): Int {
            val from = (color shr shift) and 0xFF
            return (from + (0xFF - from) * BLOCK_FADE).toInt().coerceIn(0, 255)
        }
        return Color.rgb(blend(16), blend(8), blend(0))
    }

    /** Coupe au caractère près plutôt qu'au mot : une colonne étroite ne pardonne pas. */
    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (text.isBlank() || paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
        return text.take(end) + "…"
    }

    private companion object {
        const val WIDTH = 1400
        const val HEIGHT = 1000
        const val MARGIN = 48f
        const val COLUMN_GAP = 10f
        const val BLOCK_GAP = 4f
        const val DAY_LABEL_HEIGHT = 44f
        const val BAR_WIDTH = 8f
        const val PADDING = 12f
        const val RADIUS = 10f
        const val BLOCK_FADE = 0.84f

        const val BACKGROUND = 0xFFF7F8FA.toInt()
        const val TITLE_COLOR = 0xFF18202B.toInt()
        const val MUTED_COLOR = 0xFF7F8998.toInt()
        const val ACCENT_COLOR = 0xFF5C62E8.toInt()
        const val FREE_COLOR = 0xFFECEFF3.toInt()
    }
}
