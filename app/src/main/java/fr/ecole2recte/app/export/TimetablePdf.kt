package fr.ecole2recte.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** Couleur ou niveaux de gris lisibles à l'impression noir et blanc. */
data class PdfStyle(val grayscale: Boolean = false)

/**
 * Dessine les pages de [TimetableLayout] avec `android.graphics.pdf.PdfDocument` (aucune dépendance).
 * Ce fichier ne décide d'aucune position : il lit la mise en page, et c'est elle qui est testée.
 */
object TimetablePdf {
    private const val INK = 0xFF1A1A1A.toInt()
    private const val GRID = 0xFFBDBDBD.toInt()
    private const val MUTED = 0xFF555555.toInt()
    private val grayLevels = intArrayOf(0xFFE6E6E6.toInt(), 0xFFCFCFCF.toInt(), 0xFFF2F2F2.toInt(), 0xFFBEBEBE.toInt(), 0xFFDADADA.toInt(), 0xFFC8C8C8.toInt())

    /** [colorOf] donne la couleur d'impression (ARGB) d'une matière ; ignorée en niveaux de gris. */
    fun render(pages: List<PageLayout>, style: PdfStyle, colorOf: (subject: String, colorIndex: Int) -> Int): ByteArray {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, layout ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(layout.width.toInt(), layout.height.toInt(), index + 1).create())
                draw(page.canvas, layout, style, colorOf)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false, style: Paint.Style = Paint.Style.FILL) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.style = style
        strokeWidth = 0.6f
    }

    private fun fillFor(style: PdfStyle, subject: String, index: Int, colorOf: (String, Int) -> Int): Int =
        if (style.grayscale) grayLevels[Math.floorMod(index, grayLevels.size)] else blend(colorOf(subject, index), Color.WHITE, 0.72f)

    private fun borderFor(style: PdfStyle, subject: String, index: Int, colorOf: (String, Int) -> Int): Int =
        if (style.grayscale) MUTED else colorOf(subject, index)

    private fun blend(color: Int, with: Int, amount: Float): Int {
        fun mix(a: Int, b: Int) = (a + (b - a) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(mix(Color.red(color), Color.red(with)), mix(Color.green(color), Color.green(with)), mix(Color.blue(color), Color.blue(with)))
    }

    /** Raccourcit [text] avec « … » pour qu'il tienne dans [width] points. */
    private fun fit(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > width) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun draw(canvas: Canvas, layout: PageLayout, style: PdfStyle, colorOf: (String, Int) -> Int) {
        canvas.drawColor(Color.WHITE)
        val margin = TimetableLayout.MARGIN
        canvas.drawText(layout.title, margin, margin + 14f, paint(16f, bold = true))
        canvas.drawText(layout.subtitle, margin, margin + 28f, paint(9f, MUTED))
        layout.note?.let { canvas.drawText(fit(it, paint(8.5f, MUTED), layout.width - 2 * margin), margin, margin + 38f, paint(8.5f, MUTED)) }

        // En-têtes des jours
        val headerPaint = paint(9.5f, bold = true)
        val headerBackground = paint(1f, 0xFFF0F0F0.toInt())
        layout.headers.forEach { header ->
            canvas.drawRect(header.x, layout.gridTop - TimetableLayout.HEADER_HEIGHT, header.x + header.w, layout.gridTop, headerBackground)
            canvas.drawText(header.label, header.x + 4f, layout.gridTop - 6f, headerPaint)
        }
        // Heures
        val gridPaint = paint(1f, GRID, style = Paint.Style.STROKE)
        val hourPaint = paint(8f, MUTED)
        layout.hours.forEach { hour ->
            canvas.drawLine(layout.gridLeft - 4f, hour.y, layout.gridRight, hour.y, gridPaint)
            canvas.drawText(hour.label, layout.gridLeft - 6f - hourPaint.measureText(hour.label), hour.y + 3f, hourPaint)
        }
        layout.headers.forEach { canvas.drawLine(it.x, layout.gridTop - TimetableLayout.HEADER_HEIGHT, it.x, layout.gridBottom, gridPaint) }
        canvas.drawLine(layout.gridRight, layout.gridTop - TimetableLayout.HEADER_HEIGHT, layout.gridRight, layout.gridBottom, gridPaint)

        // Cours
        layout.blocks.forEach { block ->
            val rect = RectF(block.x, block.y, block.x + block.w, block.y + block.h)
            canvas.drawRoundRect(rect, 3f, 3f, paint(1f, fillFor(style, block.subject, block.colorIndex, colorOf)))
            canvas.drawRoundRect(rect, 3f, 3f, paint(1f, borderFor(style, block.subject, block.colorIndex, colorOf), style = Paint.Style.STROKE).apply { strokeWidth = 0.9f })
            canvas.save()
            canvas.clipRect(rect)
            block.lines.forEachIndexed { i, line ->
                val textPaint = paint(8.2f, bold = line.bold)
                val baseline = block.y + TimetableLayout.BLOCK_PADDING + (i + 1) * TimetableLayout.LINE_HEIGHT - 2.5f
                canvas.drawText(fit(line.text, textPaint, block.w - 6f), block.x + 3f, baseline, textPaint)
            }
            canvas.restore()
        }

        // Légende
        layout.legend.forEach { item ->
            val swatch = RectF(item.x, item.y, item.x + 9f, item.y + 9f)
            canvas.drawRect(swatch, paint(1f, fillFor(style, item.subject, item.colorIndex, colorOf)))
            canvas.drawRect(swatch, paint(1f, borderFor(style, item.subject, item.colorIndex, colorOf), style = Paint.Style.STROKE))
            val text = if (item.teacher.isBlank()) item.subject else "${item.subject} · ${item.teacher}"
            canvas.drawText(fit(text, paint(8f), TimetableLayout.LEGEND_COLUMN - 16f), item.x + 13f, item.y + 8f, paint(8f))
        }
        canvas.drawText(layout.footer, margin, layout.height - margin, paint(7.5f, MUTED))
    }
}
