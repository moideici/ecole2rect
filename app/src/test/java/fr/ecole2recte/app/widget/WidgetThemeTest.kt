package fr.ecole2recte.app.widget

import fr.ecole2recte.app.ui.theme.Appearance
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.Contrast
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.ui.theme.ThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeTest {
    private fun ratio(a: Int, b: Int) = Contrast.ratio(a, b)

    @Test
    fun `chaque texte est lisible sur son fond dans les quinze palettes`() {
        ThemePalettes.all.forEach { palette ->
            val r = WidgetThemes.roles(palette)
            val name = "${palette.theme}/${palette.appearance}"
            assertTrue("encre $name", ratio(r.ink, r.surface) >= 4.5)
            assertTrue("texte discret $name", ratio(r.muted, r.surface) >= 4.5)
            assertTrue("accent $name", ratio(r.accent, r.surface) >= 4.5)
            assertTrue("alerte $name", ratio(r.alert, r.surface) >= 4.5)
            assertTrue("positif $name", ratio(r.positive, r.surface) >= 4.5)
            assertTrue("avertissement $name", ratio(r.warning, r.surface) >= 4.5)
            assertTrue("texte sur héros $name", ratio(r.onHero, r.hero) >= 4.5)
            assertTrue("texte doux sur héros $name", ratio(r.onHeroSoft, r.hero) >= 4.5)
            assertTrue("accent sur aplat doux $name", ratio(r.accent, r.accentSoft) >= 3.0)
            assertTrue("alerte sur aplat doux $name", ratio(r.alert, r.alertSoft) >= 3.0)
            r.subject.indices.forEach { i ->
                assertTrue("encre sur matière $i $name", ratio(r.ink, r.subjectSoft[i]) >= 4.5)
                assertTrue("texte sur matière pleine $i $name", ratio(r.onSubject[i], r.subject[i]) >= 4.5)
            }
        }
    }

    @Test
    fun `le mode Systeme donne clair le jour et sombre la nuit`() {
        val theme = WidgetThemes.of(ColorTheme.Indigo, ThemeMode.System)
        assertEquals(ThemePalettes.of(ColorTheme.Indigo, Appearance.Light).surface, theme.day.surface)
        assertEquals(ThemePalettes.of(ColorTheme.Indigo, Appearance.Dark).surface, theme.night.surface)
        assertNotEquals(theme.day.surface, theme.night.surface)
    }

    @Test
    fun `clair et sombre sont fixes le jour comme la nuit`() {
        val light = WidgetThemes.of(ColorTheme.Forest, ThemeMode.Light)
        assertEquals(light.day, light.night)
        val dark = WidgetThemes.of(ColorTheme.Forest, ThemeMode.Dark)
        assertEquals(dark.day, dark.night)
        assertNotEquals(light.day.surface, dark.day.surface)
    }

    @Test
    fun `le noir OLED donne un fond plus sombre que le sombre`() {
        val black = WidgetThemes.of(ColorTheme.Slate, ThemeMode.Black)
        val dark = WidgetThemes.of(ColorTheme.Slate, ThemeMode.Dark)
        assertEquals(black.day, black.night)
        assertTrue(Contrast.luminance(black.day.surface) < Contrast.luminance(dark.day.surface))
        assertEquals(ThemePalettes.of(ColorTheme.Slate, Appearance.Black).surface, black.day.surface)
    }

    @Test
    fun `chaque theme donne des couleurs differentes au widget`() {
        val heroes = ColorTheme.entries.map { WidgetThemes.of(it, ThemeMode.Light).day.hero }.toSet()
        assertEquals(ColorTheme.entries.size, heroes.size)
    }

    @Test
    fun `le melange reste opaque et borne`() {
        assertEquals(0xFF000000.toInt(), WidgetThemes.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.0))
        assertEquals(0xFFFFFFFF.toInt(), WidgetThemes.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1.0))
        assertEquals(0xFF, WidgetThemes.blend(0x00112233, 0x00445566, 0.5) ushr 24)
    }
}
