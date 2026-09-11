package fr.ecole2recte.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.core.settings.ThemeChoice

/**
 * Le thème de l'application.
 *
 * Une seule direction visuelle : un fond calme, des cartes blanches très arrondies, un
 * indigo unique pour les actions, et des couleurs de matière qui viennent uniquement des
 * données. Le mode sombre n'est pas une inversion mécanique — les fonds sont des bleus
 * profonds, pas du noir, pour que les cartes gardent leur relief.
 */

private val IndigoLight = Color(0xFF5C62E8)
private val IndigoDark = Color(0xFF9AA0FF)

private val LightScheme = lightColorScheme(
    primary = IndigoLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECEEFF),
    onPrimaryContainer = Color(0xFF3A3FB0),
    secondary = Color(0xFF5B6478),
    onSecondary = Color.White,
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF18202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18202B),
    surfaceVariant = Color(0xFFEFF2F7),
    // Ce gris porte *tout* le texte secondaire de l'application : échéances, heures, aperçus,
    // étiquettes de widgets — du 10 et du 12 sp. À #7F8998 il donnait 3,5:1 sur le blanc, sous
    // le minimum lisible de 4,5:1 pour ce corps. Il faut d'ailleurs le mesurer sur les trois
    // fonds clairs de l'application, pas seulement sur le blanc : #646E80 garde la teinte
    // bleutée et donne 5,1:1 sur la surface, 4,8:1 sur le fond, 4,6:1 sur les cartes grises.
    // Le projet sait calculer un ratio WCAG — il le faisait pour les couleurs de widgets,
    // pas pour lui-même.
    onSurfaceVariant = Color(0xFF646E80),
    outline = Color(0xFFCED4DE),
    outlineVariant = Color(0xFFE8EBF0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF8C1D18),
)

private val DarkScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color(0xFF171B3A),
    primaryContainer = Color(0xFF2B3160),
    onPrimaryContainer = Color(0xFFDFE1FF),
    secondary = Color(0xFFA8B2C4),
    onSecondary = Color(0xFF1A202B),
    background = Color(0xFF10141B),
    onBackground = Color(0xFFEDF1F7),
    surface = Color(0xFF171D26),
    onSurface = Color(0xFFEDF1F7),
    surfaceVariant = Color(0xFF1F2733),
    onSurfaceVariant = Color(0xFF98A2B3),
    outline = Color(0xFF3A4453),
    outlineVariant = Color(0xFF262F3B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF3B1F1D),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Couleurs que Material ne nomme pas, mais dont l'application a besoin partout. */
@Immutable
data class AppPalette(
    /** Carte sombre de mise en avant (« à ne pas oublier »). */
    val spotlight: Color,
    val onSpotlight: Color,
    val onSpotlightMuted: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

private val LightPalette = AppPalette(
    spotlight = Color(0xFF202935),
    onSpotlight = Color(0xFFFFFFFF),
    onSpotlightMuted = Color(0xFFAAB3C1),
    // Même raison : 3,2:1 et 3,7:1 sur blanc pour deux couleurs qui portent des phrases
    // courtes et importantes — la tendance d'une moyenne, « encore à justifier ». Assombries,
    // elles passent à 5,7:1 et 5,3:1 sur les fonds clairs, sans changer de teinte.
    success = Color(0xFF177548),
    successContainer = Color(0xFFE7F8EF),
    warning = Color(0xFF9A5512),
    warningContainer = Color(0xFFFFF3E4),
)

private val DarkPalette = AppPalette(
    spotlight = Color(0xFF232C3A),
    onSpotlight = Color(0xFFF2F5FA),
    onSpotlightMuted = Color(0xFF9AA5B5),
    success = Color(0xFF6ED3A6),
    successContainer = Color(0xFF1B2E27),
    warning = Color(0xFFE7A85C),
    warningContainer = Color(0xFF2E2418),
)

private val LocalAppPalette = staticCompositionLocalOf { LightPalette }

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
    )
}

/**
 * @param dynamicColor reprend les couleurs du fond d'écran (Android 12+). Désactivé par
 *   défaut : l'indigo fait partie de l'identité de l'application, et les teintes du système
 *   n'ont aucune raison de bien cohabiter avec les couleurs de matières.
 */
@Composable
fun Ecole2recteTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalAppPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Accès aux couleurs complémentaires : `AppTheme.palette.spotlight`. */
object AppTheme {
    val palette: AppPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}
