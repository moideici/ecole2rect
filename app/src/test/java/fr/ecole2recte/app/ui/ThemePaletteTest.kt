package fr.ecole2recte.app.ui

import fr.ecole2recte.app.ui.theme.Appearance
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.Contrast
import fr.ecole2recte.app.ui.theme.TextScale
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.ui.theme.ThemePalette
import fr.ecole2recte.app.ui.theme.ThemePalettes
import fr.ecole2recte.app.ui.theme.WHITE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les cinq thèmes, dans leurs trois aspects : contraste AA (4,5:1) pour tout texte, couleurs de matière lisibles. */
class ThemePaletteTest {
    private fun name(p: ThemePalette) = "${p.theme.label} / ${p.appearance}"

    private fun check(p: ThemePalette, what: String, foreground: Int, background: Int, minimum: Double = 4.5) {
        val ratio = Contrast.ratio(foreground, background)
        assertTrue("${name(p)} : $what = ${"%.2f".format(java.util.Locale.ROOT, ratio)} (minimum $minimum)", ratio >= minimum)
    }

    @Test
    fun `il y a cinq themes decline chacun en trois aspects`() {
        assertEquals(5, ColorTheme.entries.size)
        assertEquals(15, ThemePalettes.all.size)
        assertEquals(15, ThemePalettes.all.map { it.theme to it.appearance }.toSet().size)
        assertEquals(ColorTheme.Indigo, ColorTheme.Default)
        assertEquals(listOf("Indigo", "Océan", "Forêt", "Coucher de soleil", "Ardoise"), ColorTheme.entries.map { it.label })
    }

    @Test
    fun `tous les textes atteignent le contraste AA sur chaque theme et chaque aspect`() {
        ThemePalettes.all.forEach { p ->
            check(p, "texte principal sur le fond", p.ink, p.background)
            check(p, "texte principal sur la carte", p.ink, p.surface)
            check(p, "texte secondaire sur le fond", p.muted, p.background)
            check(p, "texte secondaire sur la carte", p.muted, p.surface)
            check(p, "accent (liens, icônes) sur le fond", p.accent, p.background)
            check(p, "accent sur la carte", p.accent, p.surface)
            check(p, "positif sur le fond", p.positive, p.background)
            check(p, "positif sur la carte", p.positive, p.surface)
            check(p, "erreur sur le fond", p.error, p.background)
            check(p, "erreur sur la carte", p.error, p.surface)
            check(p, "texte blanc sur un aplat d'accent", WHITE, p.accentFill)
            check(p, "texte des boutons sur l'accent", p.onAccent, p.accent)
            check(p, "texte blanc sur la carte d'alerte", WHITE, p.inverseSurface)
            check(p, "titre d'avertissement", p.warningTitle, p.warningSurface)
            check(p, "texte d'avertissement", p.warningText, p.warningSurface)
        }
    }

    @Test
    fun `les couleurs de matiere restent lisibles sur chaque theme`() {
        ThemePalettes.all.forEach { p ->
            assertEquals(10, p.subject.size)
            p.subject.forEachIndexed { index, color ->
                // Comme pastille ou icône : distincte de la carte.
                check(p, "matière $index comme pastille", color, p.surface, if (p.isDark) 3.0 else 1.9)
                // Comme texte : la teinte est rapprochée du noir ou du blanc jusqu'à 4,5.
                check(p, "matière $index comme texte sur la carte", Contrast.readableOn(color, p.surface), p.surface)
                check(p, "matière $index comme texte sur le fond", Contrast.readableOn(color, p.background), p.background)
            }
        }
    }

    @Test
    fun `une couleur deja lisible n'est pas modifiee, une pale est assombrie sans changer de teinte`() {
        val white = WHITE
        val green = 0xFF2E7D32.toInt()
        assertEquals(green, Contrast.readableOn(green, white))
        val pale = 0xFFEFA36A.toInt()
        val darker = Contrast.readableOn(pale, white)
        assertNotEquals(pale, darker)
        assertTrue(Contrast.ratio(darker, white) >= 4.5)
        // Toujours orangée : le rouge domine encore le bleu.
        assertTrue(((darker shr 16) and 0xFF) > (darker and 0xFF))
    }

    @Test
    fun `le noir pur a un vrai fond noir et le sombre non`() {
        ColorTheme.entries.forEach { theme ->
            assertEquals(0xFF000000.toInt(), ThemePalettes.of(theme, Appearance.Black).background)
            assertNotEquals(0xFF000000.toInt(), ThemePalettes.of(theme, Appearance.Dark).background)
            // Les cartes restent distinctes du fond noir.
            assertTrue(Contrast.ratio(ThemePalettes.of(theme, Appearance.Black).surface, 0xFF000000.toInt()) > 1.0)
            assertTrue(ThemePalettes.of(theme, Appearance.Black).isDark)
        }
    }

    @Test
    fun `chaque theme a sa propre identite`() {
        val accents = ColorTheme.entries.map { ThemePalettes.of(it, Appearance.Light).accent }
        assertEquals(5, accents.toSet().size)
        val backgrounds = ColorTheme.entries.map { ThemePalettes.of(it, Appearance.Dark).background }
        assertEquals(5, backgrounds.toSet().size)
    }

    @Test
    fun `le mode systeme suit le telephone, les autres non`() {
        assertEquals(Appearance.Dark, ThemeMode.System.appearance(systemDark = true))
        assertEquals(Appearance.Light, ThemeMode.System.appearance(systemDark = false))
        assertEquals(Appearance.Light, ThemeMode.Light.appearance(systemDark = true))
        assertEquals(Appearance.Dark, ThemeMode.Dark.appearance(systemDark = false))
        assertEquals(Appearance.Black, ThemeMode.Black.appearance(systemDark = false))
    }

    @Test
    fun `les reglages inconnus retombent sur les valeurs par defaut`() {
        assertEquals(ThemeMode.System, ThemeMode.parse(null))
        assertEquals(ThemeMode.System, ThemeMode.parse("n'importe quoi"))
        assertEquals(ThemeMode.Black, ThemeMode.parse("Black"))
        assertEquals(ColorTheme.Indigo, ColorTheme.parse(null))
        assertEquals(ColorTheme.Forest, ColorTheme.parse("Forest"))
        assertEquals(TextScale.Normal, TextScale.parse("zzz"))
        assertEquals(TextScale.XLarge, TextScale.parse("XLarge"))
    }

    @Test
    fun `la taille du texte s'ajoute a celle du systeme, bornee`() {
        assertEquals(1.0f, TextScale.Normal.effective(1.0f), 0.0001f)
        assertEquals(1.15f, TextScale.Large.effective(1.0f), 0.0001f)
        assertEquals(1.3f * 1.3f, TextScale.XLarge.effective(1.3f), 0.0001f)
        assertEquals(0.88f, TextScale.Small.effective(1.0f), 0.0001f)
        assertEquals(TextScale.MAX_SCALE, TextScale.XLarge.effective(2.5f), 0.0001f)
        assertEquals(TextScale.MIN_SCALE, TextScale.Small.effective(0.5f), 0.0001f)
        assertEquals(listOf("Petit", "Normal", "Grand", "Très grand"), TextScale.entries.map { it.label })
    }
}
