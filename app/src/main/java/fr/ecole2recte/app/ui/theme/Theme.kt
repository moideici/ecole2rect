package fr.ecole2recte.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Jetons de couleur de l'application. L'identité reste la même (indigo, cartes arrondies) ;
 * ce qui change : les couleurs s'adaptent au mode sombre et les gris secondaires respectent
 * le contraste minimal (4,5:1) de WCAG AA.
 */
@Immutable
class AppColors(
    val ink: Color,
    val muted: Color,
    val background: Color,
    val surface: Color,
    /** Accent pour du texte, des liens et des icônes. */
    val accent: Color,
    /** Accent pour les aplats sous du texte blanc (boutons, carte du prochain cours). */
    val accentFill: Color,
    val positive: Color,
    val error: Color,
    /** Carte sombre d'alerte (« à ne pas oublier ») : sombre dans les deux modes. */
    val inverseSurface: Color,
    val warningSurface: Color,
    val warningTitle: Color,
    val warningText: Color,
    val subject: List<Color>,
    val isDark: Boolean,
)

private val LightColors = AppColors(
    ink = Color(0xFF18202B),
    muted = Color(0xFF5B6675),
    background = Color(0xFFF5F7FB),
    surface = Color.White,
    accent = Color(0xFF5C62E8),
    accentFill = Color(0xFF5C62E8),
    positive = Color(0xFF1F7F59),
    error = Color(0xFFB3404C),
    inverseSurface = Color(0xFF202935),
    warningSurface = Color(0xFFFFF7ED),
    warningTitle = Color(0xFF8A4F12),
    warningText = Color(0xFF5F4A32),
    subject = listOf(
        Color(0xFF7779E8), Color(0xFF63A6E9), Color(0xFFEFA36A), Color(0xFF5CBD92), Color(0xFFE07A8F),
        Color(0xFF9B7BD8), Color(0xFF3FB6B2), Color(0xFFD9B44A), Color(0xFF8AA0B5), Color(0xFFD9825B),
    ),
    isDark = false,
)

private val DarkColors = AppColors(
    ink = Color(0xFFE9EDF3),
    muted = Color(0xFFA5AFBD),
    background = Color(0xFF0F131A),
    surface = Color(0xFF1A212B),
    accent = Color(0xFFA2A6FF),
    accentFill = Color(0xFF4A50D6),
    positive = Color(0xFF5FD3A2),
    error = Color(0xFFFF8C96),
    inverseSurface = Color(0xFF2A3342),
    warningSurface = Color(0xFF33281A),
    warningTitle = Color(0xFFFFC27A),
    warningText = Color(0xFFE9D6BE),
    subject = listOf(
        Color(0xFF9A9CF5), Color(0xFF82BDF0), Color(0xFFF3B98C), Color(0xFF7FD3AD), Color(0xFFF09BAC),
        Color(0xFFB9A0EC), Color(0xFF6FD3CF), Color(0xFFE8CB78), Color(0xFFADBFD0), Color(0xFFEBA37F),
    ),
    isDark = true,
)

private val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Espacements et rayons communs. */
object Dimens {
    val screenPadding: Dp = 18.dp
    val cardRadius: Dp = 15.dp
    val bigCardRadius: Dp = 22.dp
    val minTouchTarget: Dp = 48.dp
}

/** Les couleurs choisies par l'utilisateur pour les matières du profil affiché, par nom de matière. */
val LocalSubjectColorOverrides = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

fun subjectKey(name: String): String = name.trim().lowercase()

/** La couleur d'une matière : le choix de l'utilisateur, à défaut la couleur attribuée d'office. */
@Composable
@ReadOnlyComposable
fun subjectColor(name: String, defaultIndex: Int): Color {
    val palette = AppTheme.colors.subject
    val index = LocalSubjectColorOverrides.current[subjectKey(name)] ?: defaultIndex
    return palette[Math.floorMod(index, palette.size)]
}

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

/** Mode d'affichage choisi par l'utilisateur (réglage ajouté au lot 14 ; système par défaut). */
enum class ThemeMode { System, Light, Dark }

@Composable
fun Ecole2recteTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color(0xFF10131F),
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            outline = colors.muted,
            error = colors.error,
            secondaryContainer = colors.accentFill.copy(alpha = .45f),
            onSecondaryContainer = colors.ink,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            outline = colors.muted,
            error = colors.error,
            secondaryContainer = colors.accent.copy(alpha = .16f),
            onSecondaryContainer = colors.ink,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
