package fr.ecole2recte.app.widget

import fr.ecole2recte.app.ui.theme.Appearance
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.Contrast
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.ui.theme.ThemePalette
import fr.ecole2recte.app.ui.theme.ThemePalettes
import fr.ecole2recte.app.ui.theme.WHITE

/**
 * Les couleurs d'un widget dans un aspect (clair ou sombre), en ARGB. Calculées à partir de la palette
 * du thème choisi dans l'application ([ThemePalette]) : fonctions pures, sans Glance, donc testables
 * sur le poste. Chaque texte est vérifié contre le fond sur lequel il est posé (contraste WCAG ≥ 4,5).
 */
data class WidgetRoles(
    /** Fond du widget. */
    val surface: Int,
    val ink: Int,
    val muted: Int,
    /** Couleur d'accent utilisable en texte sur [surface]. */
    val accent: Int,
    /** Aplat doux teinté d'accent (cours en cours, pastilles). */
    val accentSoft: Int,
    val alert: Int,
    val alertSoft: Int,
    /** Aplat neutre légèrement plus clair ou plus sombre que le fond. */
    val soft: Int,
    /** Fond plein des widgets « héros » (prochain cours, messages). */
    val hero: Int,
    val onHero: Int,
    val onHeroSoft: Int,
    val heroChip: Int,
    /** Les dix couleurs de matière, en aplat. */
    val subject: List<Int>,
    /** Chaque couleur de matière diluée dans le fond : le texte [ink] reste lisible dessus. */
    val subjectSoft: List<Int>,
    /** Le texte lisible posé sur l'aplat de la matière. */
    val onSubject: List<Int>,
)

/** Les couleurs d'un widget le jour et la nuit : le système choisit selon son mode sombre. */
data class WidgetTheme(val day: WidgetRoles, val night: WidgetRoles)

object WidgetThemes {
    /** Teinte sombre utilisée quand ni le blanc ni le fond ne conviennent. */
    private const val DARK_INK = 0xFF10131F.toInt()

    /** Mélange [from] avec [to] : 0 donne [from], 1 donne [to]. Toujours opaque. */
    fun blend(from: Int, to: Int, t: Double): Int {
        fun part(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (part(16) shl 16) or (part(8) shl 8) or part(0)
    }

    /** Le texte (blanc ou presque noir) qui contraste le mieux avec [background]. */
    fun onColor(background: Int): Int =
        if (Contrast.ratio(WHITE, background) >= Contrast.ratio(DARK_INK, background)) WHITE else DARK_INK

    /** Les rôles d'un widget pour une palette de l'application. */
    fun roles(p: ThemePalette): WidgetRoles {
        val surface = p.surface
        val hero = p.accentFill
        val onHero = onColor(hero)
        val softHero = blend(hero, onHero, 0.82)
        return WidgetRoles(
            surface = surface,
            ink = Contrast.readableOn(p.ink, surface),
            muted = Contrast.readableOn(p.muted, surface),
            accent = Contrast.readableOn(p.accent, surface),
            accentSoft = blend(surface, p.accent, 0.18),
            alert = Contrast.readableOn(p.error, surface),
            alertSoft = blend(surface, p.error, 0.16),
            soft = blend(surface, p.ink, 0.07),
            hero = hero,
            onHero = onHero,
            onHeroSoft = if (Contrast.ratio(softHero, hero) >= 4.5) softHero else onHero,
            heroChip = blend(hero, onHero, 0.22),
            subject = p.subject,
            subjectSoft = p.subject.map { blend(surface, it, SUBJECT_TINT) },
            onSubject = p.subject.map(::onColor),
        )
    }

    /** Part de la couleur de matière dans son aplat doux : assez pour se reconnaître, assez peu pour garder le texte lisible. */
    const val SUBJECT_TINT = 0.38

    /**
     * Les couleurs des widgets pour le thème et le mode choisis dans l'application. « Système » suit le
     * mode sombre du téléphone (clair le jour, sombre la nuit) ; « Noir » donne le fond noir pur des écrans OLED.
     */
    fun of(theme: ColorTheme, mode: ThemeMode): WidgetTheme {
        val light = roles(ThemePalettes.of(theme, Appearance.Light))
        val dark = roles(ThemePalettes.of(theme, Appearance.Dark))
        val black = roles(ThemePalettes.of(theme, Appearance.Black))
        return when (mode) {
            ThemeMode.System -> WidgetTheme(day = light, night = dark)
            ThemeMode.Light -> WidgetTheme(day = light, night = light)
            ThemeMode.Dark -> WidgetTheme(day = dark, night = dark)
            ThemeMode.Black -> WidgetTheme(day = black, night = black)
        }
    }
}
