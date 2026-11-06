package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.GradeCurves
import fr.ecole2recte.app.domain.GradeMath
import fr.ecole2recte.app.ui.theme.AppTheme

/** Un point d'une courbe : sa valeur (sur 20) et le nom affiché dessous. */
data class ChartPoint(val value: Double, val label: String)

/**
 * Courbe dessinée en Canvas, sans bibliothèque. [line] relie les points ; [dots] (facultatif) ajoute des
 * carrés vides, un par point, pour les notes elles-mêmes. Lisible en clair comme en sombre (couleurs du thème),
 * et décrite en une phrase par [description] pour un lecteur d'écran : le dessin ne porte pas seul l'information,
 * les valeurs sont aussi écrites sur le graphique.
 */
@Composable
fun LineChart(
    line: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    dots: List<Double>? = null,
    height: Dp = 104.dp,
) {
    if (line.isEmpty()) return
    val accent = AppTheme.colors.accent
    val ink = AppTheme.colors.ink
    val muted = AppTheme.colors.muted
    val surface = AppTheme.colors.surface
    val faint = muted.copy(alpha = .25f)
    val measurer = rememberTextMeasurer()
    val all = line.map { it.value } + dots.orEmpty()
    val (low, high) = GradeCurves.yRange(all)
    val few = line.size <= 6

    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
            val topPad = 18.dp.toPx()
            val bottomPad = 6.dp.toPx()
            val plotHeight = size.height - topPad - bottomPad
            val n = line.size
            fun x(index: Int) = size.width * (index + 0.5f) / n
            fun y(value: Double) = topPad + (1f - GradeCurves.position(value, low, high)) * plotHeight

            // Repères : le bas et le haut de l'échelle, en valeurs lisibles.
            listOf(low, high).forEach { bound ->
                drawLine(faint, Offset(0f, y(bound)), Offset(size.width, y(bound)), strokeWidth = 1.dp.toPx())
            }
            val tick = TextStyle(color = muted, fontSize = 10.sp)
            drawText(measurer, GradeMath.decimal(high), Offset(0f, y(high) - 14.dp.toPx()), tick)
            drawText(measurer, GradeMath.decimal(low), Offset(0f, y(low) - 14.dp.toPx()), tick)

            if (n > 1) {
                val path = Path()
                line.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(x(index), y(point.value)) else path.lineTo(x(index), y(point.value))
                }
                drawPath(path, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            // Les notes : des carrés vides (forme différente des ronds de la courbe, lisible sans couleur).
            dots?.forEachIndexed { index, value ->
                if (index < n) {
                    val side = 9.dp.toPx()
                    drawRect(ink.copy(alpha = .75f), Offset(x(index) - side / 2, y(value) - side / 2), Size(side, side), style = Stroke(width = 2.dp.toPx()))
                }
            }
            line.forEachIndexed { index, point ->
                drawCircle(surface, 7.dp.toPx(), Offset(x(index), y(point.value)))
                drawCircle(accent, 5.dp.toPx(), Offset(x(index), y(point.value)))
                if (few || index == 0 || index == n - 1) {
                    val label = measurer.measure(GradeMath.decimal(point.value), TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    val left = (x(index) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                    drawText(label, topLeft = Offset(left, (y(point.value) - 12.dp.toPx() - label.size.height).coerceAtLeast(0f)))
                }
            }
        }
        if (few) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                line.forEach {
                    Text(
                        it.label,
                        color = muted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.first().label, color = muted, fontSize = 11.sp)
                Text(line.last().label, color = muted, fontSize = 11.sp)
            }
        }
    }
}
