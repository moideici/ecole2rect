package fr.ecole2recte.app.widget

enum class SmallWidgetVariant { One, Wide, Large }
data class SmallWidgetContent(val header: Boolean, val captionLines: Int, val detailLines: Int, val badge: Boolean, val valueSize: Int, val padding: Int)

/** Les dp déclarés sont des paliers de lanceur, jamais des dimensions imposées au contenu. */
object SmallWidgetLayout {
    fun content(widthDp: Float, heightDp: Float, landscape: Boolean, fontScale: Float): SmallWidgetContent {
        val scale = fontScale.coerceAtLeast(1f)
        val height = heightDp / scale
        val variant = variant(widthDp, heightDp, landscape, scale)
        val tiny = height < 100
        val padding = if (tiny) 4 else 8
        val header = !tiny && variant != SmallWidgetVariant.Wide
        val captionLines = when { height < 52 -> 0; height < 78 || variant == SmallWidgetVariant.Wide -> 1; else -> 2 }
        val badge = height >= 78
        val valueSize = if (tiny) ((height - padding * 2) / 1.3f).toInt().coerceIn(8, 16) else if (variant == SmallWidgetVariant.Large) 28 else 23
        val remaining = height - padding * 2 - valueSize * 1.2f - captionLines * 12 - (if (header) 18 else 0) - (if (badge) 12 else 0)
        val details = if (variant == SmallWidgetVariant.One) 0 else (remaining / 13).toInt().coerceIn(0, 4)
        return SmallWidgetContent(header, captionLines, details, badge, valueSize, padding)
    }

    fun variant(widthDp: Float, heightDp: Float, landscape: Boolean, fontScale: Float = 1f): SmallWidgetVariant {
        val scale = fontScale.coerceAtLeast(1f)
        val width = widthDp / scale
        val height = heightDp / scale
        return when {
            width >= 180 && height >= 180 -> SmallWidgetVariant.Large
            width >= 160 && (landscape || height >= 90) -> SmallWidgetVariant.Wide
            else -> SmallWidgetVariant.One
        }
    }
}
