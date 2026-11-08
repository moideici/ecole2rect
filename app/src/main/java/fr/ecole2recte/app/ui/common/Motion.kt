package fr.ecole2recte.app.ui.common

import fr.ecole2recte.app.domain.FrenchFormat
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.delay
import java.util.Locale

/** Vrai quand l'utilisateur a supprimé les animations, dans Android ou dans les réglages de l'application. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val system = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val own by rememberAppContainer().settings.reduceMotion.collectAsState()
    return system || own
}

/** Entrée en cascade : la carte monte et apparaît, décalée selon son rang dans la liste. */
fun Modifier.entrance(index: Int): Modifier = composed {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(index.coerceAtMost(8) * 45L)
            progress.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22.dp.toPx()
    }
}

/** Léger rétrécissement tant que l'élément est pressé. */
fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(110), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Compte de 0 jusqu'à une valeur décimale française (« 15,8 ») ; tout autre texte reste tel quel. */
@Composable
fun rememberAnimatedDecimal(text: String): String {
    val reduceMotion = rememberReduceMotion()
    val target = text.replace(',', '.').toFloatOrNull()
    val animated = remember(text) { Animatable(if (target == null || reduceMotion) target ?: 0f else 0f) }
    LaunchedEffect(text) {
        if (target != null && !reduceMotion) animated.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val digits = text.substringAfter(',', "").length.coerceIn(1, 2)
    return if (target == null) text else FrenchFormat.decimal(animated.value.toDouble(), digits)
}

/** Anneau de progression animé autour d'un contenu (prochain cours). */
@Composable
fun ProgressRing(progress: Float, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val sweep = remember { Animatable(if (reduceMotion) progress else 0f) }
    LaunchedEffect(progress) {
        if (!reduceMotion) sweep.animateTo(progress, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(Color.White, -90f, 360f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Fond qui brille de gauche à droite pendant un chargement. */
fun Modifier.shimmer(): Modifier = composed {
    val colors = AppTheme.colors
    val base = colors.ink.copy(alpha = if (colors.isDark) .10f else .07f)
    val highlight = colors.ink.copy(alpha = if (colors.isDark) .20f else .14f)
    val reduceMotion = rememberReduceMotion()
    val shift = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val value by transition.animateFloat(
            initialValue = -400f,
            targetValue = 1200f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerShift",
        )
        value
    }
    background(Brush.linearGradient(listOf(base, highlight, base), Offset(shift, 0f), Offset(shift + 400f, 200f)))
}

/** Squelette de page : remplace le rond de chargement par la silhouette du contenu. */
@Composable
fun SkeletonPage() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp)) {
        Box(Modifier.height(14.dp).fillMaxWidth(.3f).clipped(8.dp).shimmer())
        Box(Modifier.padding(top = 10.dp).height(30.dp).fillMaxWidth(.6f).clipped(10.dp).shimmer())
        Box(Modifier.padding(top = 6.dp).height(14.dp).fillMaxWidth(.4f).clipped(8.dp).shimmer())
        repeat(4) {
            Box(Modifier.padding(top = 14.dp).height(74.dp).fillMaxWidth().clipped(16.dp).shimmer())
        }
    }
}

private fun Modifier.clipped(radius: Dp): Modifier = this.then(Modifier.graphicsLayer {
    shape = RoundedCornerShape(radius)
    clip = true
})
