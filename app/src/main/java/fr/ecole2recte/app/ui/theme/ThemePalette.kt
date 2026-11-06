package fr.ecole2recte.app.ui.theme

import kotlin.math.pow

/**
 * Les cinq thèmes de couleurs au choix (palette d'accent + surfaces teintées), chacun décliné en
 * clair, sombre et noir pur. Ce fichier est volontairement sans dépendance à Compose : les valeurs
 * sont des entiers ARGB, lisibles aussi par la couche widget (voir [ThemePalettes.of]).
 */
enum class ColorTheme(val label: String, val description: String) {
    Indigo("Indigo", "Le thème d'origine : bleu-violet, doux."),
    Ocean("Océan", "Bleu-vert, frais et clair."),
    Forest("Forêt", "Vert, apaisant."),
    Sunset("Coucher de soleil", "Orange et rose, chaleureux."),
    Slate("Ardoise", "Gris-bleu, sobre."),
    ;

    companion object {
        val Default = Indigo
        fun parse(name: String?): ColorTheme = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** L'aspect réellement affiché : le mode « Système » est déjà résolu en clair ou en sombre. */
enum class Appearance(val isDark: Boolean) { Light(false), Dark(true), Black(true) }

/**
 * Toutes les couleurs d'un thème dans un aspect donné, en ARGB (`0xFFRRGGBB`).
 * Les rôles sont ceux de l'application : [ink] et [muted] pour les textes, [accent] pour les textes
 * et icônes colorés, [accentFill] pour les aplats sous du texte blanc, [onAccent] pour le texte posé
 * sur [accent] (boutons du thème sombre).
 */
data class ThemePalette(
    val theme: ColorTheme,
    val appearance: Appearance,
    val ink: Int,
    val muted: Int,
    val background: Int,
    val surface: Int,
    val accent: Int,
    val accentFill: Int,
    val onAccent: Int,
    val positive: Int,
    val error: Int,
    val inverseSurface: Int,
    val warningSurface: Int,
    val warningTitle: Int,
    val warningText: Int,
    /** Les dix couleurs de matière, communes à tous les thèmes (clair ou sombre). */
    val subject: List<Int>,
) {
    val isDark: Boolean get() = appearance.isDark
    val white: Int get() = WHITE
}

const val WHITE = 0xFFFFFFFF.toInt()

/** Les dix couleurs de matière du mode clair, puis du mode sombre : partagées avec les widgets. */
val LIGHT_SUBJECT_ARGB = listOf(
    0xFF7779E8, 0xFF63A6E9, 0xFFEFA36A, 0xFF5CBD92, 0xFFE07A8F,
    0xFF9B7BD8, 0xFF3FB6B2, 0xFFD9B44A, 0xFF8AA0B5, 0xFFD9825B,
).map { it.toInt() }

val DARK_SUBJECT_ARGB = listOf(
    0xFF9A9CF5, 0xFF82BDF0, 0xFFF3B98C, 0xFF7FD3AD, 0xFFF09BAC,
    0xFFB9A0EC, 0xFF6FD3CF, 0xFFE8CB78, 0xFFADBFD0, 0xFFEBA37F,
).map { it.toInt() }

object ThemePalettes {
    private fun argb(rgb: Long): Int = (0xFF000000 or rgb).toInt()

    private val light = mapOf(
        ColorTheme.Indigo to LightBase(0x18202B, 0x5B6675, 0xF5F7FB, 0xFFFFFF, 0x5A60E6, 0x5C62E8, 0x1F7F59, 0xB3404C, 0x202935, 0xFFF7ED),
        ColorTheme.Ocean to LightBase(0x10242B, 0x4F6670, 0xEEF6F8, 0xFFFFFF, 0x0A7585, 0x0A7585, 0x1B7A55, 0xB3404C, 0x143039, 0xFFF7ED),
        ColorTheme.Forest to LightBase(0x14231A, 0x53665A, 0xF1F7F1, 0xFFFFFF, 0x2E7D32, 0x2E7D32, 0x1B6E47, 0xB3404C, 0x1B2D20, 0xFFF7ED),
        ColorTheme.Sunset to LightBase(0x2A1A1F, 0x6B5058, 0xFFF5F0, 0xFFFFFF, 0xC2410C, 0xC2410C, 0x1F7F59, 0xB3263F, 0x3A2025, 0xFFF1E0),
        ColorTheme.Slate to LightBase(0x1B232C, 0x566371, 0xF1F4F7, 0xFFFFFF, 0x3F5F85, 0x3F5F85, 0x1B7A55, 0xB3404C, 0x232C36, 0xFFF7ED),
    )

    private val dark = mapOf(
        ColorTheme.Indigo to DarkBase(0xE9EDF3, 0xA5AFBD, 0x0F131A, 0x1A212B, 0xA2A6FF, 0x4A50D6, 0x5FD3A2, 0xFF8C96, 0x2A3342, 0x0B0D12),
        ColorTheme.Ocean to DarkBase(0xE4F1F3, 0x9FB8BF, 0x0A1518, 0x13252A, 0x5FD0E0, 0x0B7285, 0x5FD3A2, 0xFF8C96, 0x1F3A42, 0x070F11),
        ColorTheme.Forest to DarkBase(0xE6F0E8, 0xA3B8A8, 0x0D140E, 0x162118, 0x7FD68A, 0x2E7D32, 0x7FD68A, 0xFF8C96, 0x223326, 0x0A100B),
        ColorTheme.Sunset to DarkBase(0xF6E9E6, 0xC2A9A9, 0x1A0F10, 0x281A1B, 0xFF9E7A, 0xC2410C, 0x5FD3A2, 0xFF8C96, 0x3A2325, 0x130B0C),
        ColorTheme.Slate to DarkBase(0xE6EBF0, 0xA4AFBB, 0x12171C, 0x1C242C, 0x9DB7D5, 0x3F5F85, 0x5FD3A2, 0xFF8C96, 0x2C3642, 0x0D1115),
    )

    private class LightBase(
        val ink: Long, val muted: Long, val background: Long, val surface: Long, val accent: Long, val accentFill: Long,
        val positive: Long, val error: Long, val inverse: Long, val warningSurface: Long,
    )

    private class DarkBase(
        val ink: Long, val muted: Long, val background: Long, val surface: Long, val accent: Long, val accentFill: Long,
        val positive: Long, val error: Long, val inverse: Long, val blackSurface: Long,
    )

    /** Les couleurs d'un thème dans un aspect donné. */
    fun of(theme: ColorTheme, appearance: Appearance): ThemePalette = when (appearance) {
        Appearance.Light -> light.getValue(theme).let {
            ThemePalette(
                theme, appearance, argb(it.ink), argb(it.muted), argb(it.background), argb(it.surface), argb(it.accent),
                argb(it.accentFill), onAccent = WHITE, positive = argb(it.positive), error = argb(it.error),
                inverseSurface = argb(it.inverse), warningSurface = argb(it.warningSurface), warningTitle = argb(0x8A4F12),
                warningText = argb(0x5F4A32), subject = LIGHT_SUBJECT_ARGB,
            )
        }
        Appearance.Dark, Appearance.Black -> dark.getValue(theme).let {
            val black = appearance == Appearance.Black
            ThemePalette(
                theme, appearance, argb(it.ink), argb(it.muted),
                background = if (black) argb(0x000000) else argb(it.background),
                surface = if (black) argb(it.blackSurface) else argb(it.surface),
                accent = argb(it.accent), accentFill = argb(it.accentFill), onAccent = argb(0x10131F),
                positive = argb(it.positive), error = argb(it.error), inverseSurface = argb(it.inverse),
                warningSurface = argb(0x33281A), warningTitle = argb(0xFFC27A), warningText = argb(0xE9D6BE),
                subject = DARK_SUBJECT_ARGB,
            )
        }
    }

    /** Les quinze combinaisons (cinq thèmes, trois aspects) : pour les tests et les aperçus. */
    val all: List<ThemePalette> = ColorTheme.entries.flatMap { theme -> Appearance.entries.map { of(theme, it) } }
}

/** Rapports de contraste WCAG 2.x entre deux couleurs ARGB. */
object Contrast {
    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    /** De 1 (identique) à 21 (noir sur blanc). AA exige 4,5 pour du texte, 3 pour une icône. */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun mix(from: Int, to: Int, t: Double): Int {
        fun part(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (part(16) shl 16) or (part(8) shl 8) or part(0)
    }

    /**
     * Rapproche [color] du noir (fond clair) ou du blanc (fond sombre) jusqu'à atteindre [minRatio] :
     * la couleur d'une matière reste reconnaissable, mais devient lisible quand elle sert de texte.
     */
    fun readableOn(color: Int, background: Int, minRatio: Double = 4.5): Int {
        if (ratio(color, background) >= minRatio) return color
        val target = if (luminance(background) > 0.5) 0xFF000000.toInt() else WHITE
        var t = 0.0
        while (t < 1.0) {
            t += 0.05
            val candidate = mix(color, target, t.coerceAtMost(1.0))
            if (ratio(candidate, background) >= minRatio) return candidate
        }
        return target
    }
}

/** Taille du texte choisie dans l'application, appliquée en plus de la taille du système. */
enum class TextScale(val label: String, val multiplier: Float) {
    Small("Petit", 0.88f),
    Normal("Normal", 1.0f),
    Large("Grand", 1.15f),
    XLarge("Très grand", 1.3f),
    ;

    /** L'échelle réellement appliquée au texte : celle du système multipliée par ce choix, bornée. */
    fun effective(systemFontScale: Float): Float = (systemFontScale * multiplier).coerceIn(MIN_SCALE, MAX_SCALE)

    companion object {
        const val MIN_SCALE = 0.7f
        const val MAX_SCALE = 3.0f
        fun parse(name: String?): TextScale = entries.firstOrNull { it.name == name } ?: Normal
    }
}

/** Mode d'affichage choisi par l'utilisateur : le système, clair, sombre ou noir pur (écrans OLED). */
enum class ThemeMode(val label: String) {
    System("Système"),
    Light("Clair"),
    Dark("Sombre"),
    Black("Noir"),
    ;

    /** L'aspect à afficher, sachant si le système est en mode sombre. */
    fun appearance(systemDark: Boolean): Appearance = when (this) {
        System -> if (systemDark) Appearance.Dark else Appearance.Light
        Light -> Appearance.Light
        Dark -> Appearance.Dark
        Black -> Appearance.Black
    }

    companion object {
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}
