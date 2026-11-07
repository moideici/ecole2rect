package fr.ecole2recte.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/** Les dix couleurs de matière, claires puis sombres : partagées avec les widgets. */
val LightSubjectColors = LIGHT_SUBJECT_ARGB.map { Color(it) }

val DarkSubjectColors = DARK_SUBJECT_ARGB.map { Color(it) }

/** Les couleurs de l'application pour une palette (un thème dans un aspect). */
fun ThemePalette.toAppColors(): AppColors = AppColors(
    ink = Color(ink),
    muted = Color(muted),
    background = Color(background),
    surface = Color(surface),
    accent = Color(accent),
    accentFill = Color(accentFill),
    positive = Color(positive),
    error = Color(error),
    inverseSurface = Color(inverseSurface),
    warningSurface = Color(warningSurface),
    warningTitle = Color(warningTitle),
    warningText = Color(warningText),
    subject = subject.map { Color(it) },
    isDark = isDark,
)

private val LocalAppColors = staticCompositionLocalOf { ThemePalettes.of(ColorTheme.Indigo, Appearance.Light).toAppColors() }

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

/**
 * La couleur d'une matière quand elle sert de TEXTE : la même teinte, assombrie (ou éclaircie en
 * mode sombre) juste assez pour atteindre le contraste AA (4,5) sur la surface. Les pastilles et
 * aplats gardent la couleur d'origine ([subjectColor]).
 */
@Composable
@ReadOnlyComposable
fun subjectTextColor(name: String, defaultIndex: Int): Color {
    val colors = AppTheme.colors
    return Color(Contrast.readableOn(subjectColor(name, defaultIndex).toArgb(), colors.surface.toArgb()))
}

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

@Composable
fun Ecole2recteTheme(
    mode: ThemeMode = ThemeMode.System,
    colorTheme: ColorTheme = ColorTheme.Default,
    textScale: TextScale = TextScale.Normal,
    content: @Composable () -> Unit,
) {
    val palette = ThemePalettes.of(colorTheme, mode.appearance(isSystemInDarkTheme()))
    val colors = palette.toAppColors()
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color(palette.onAccent),
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
    // La taille du texte choisie dans l'application s'ajoute à celle du système.
    val base = LocalDensity.current
    val density = remember(base, textScale) { Density(base.density, textScale.effective(base.fontScale)) }
    CompositionLocalProvider(LocalAppColors provides colors, LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
