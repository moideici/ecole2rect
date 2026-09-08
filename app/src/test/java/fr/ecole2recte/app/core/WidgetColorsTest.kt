package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.Contrast
import fr.ecole2recte.app.core.settings.WidgetColorSettings
import fr.ecole2recte.app.core.settings.WidgetPaletteChoices
import fr.ecole2recte.app.core.settings.WidgetTheme
import fr.ecole2recte.app.core.store.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les couleurs des widgets.
 *
 * Un widget vit devant un fond d'écran que l'application n'a pas choisi : ces réglages
 * existent pour ça. Ce qui doit être vérifié n'est pas la teinte — c'est le fait qu'on ne
 * puisse jamais aboutir à du texte invisible sans en être averti.
 */
class WidgetColorsTest {

    private val black = 0xFF000000L
    private val white = 0xFFFFFFFFL

    @Test
    fun `sans reglage, le widget suit le systeme`() {
        val colors = WidgetColorSettings()
        assertTrue(colors.isDark(systemDark = true))
        assertFalse(colors.isDark(systemDark = false))
        assertFalse(colors.custom)
    }

    @Test
    fun `un theme force ignore le systeme`() {
        assertFalse(WidgetColorSettings(theme = WidgetTheme.LIGHT).isDark(systemDark = true))
        assertTrue(WidgetColorSettings(theme = WidgetTheme.DARK).isDark(systemDark = false))
    }

    @Test
    fun `en couleurs libres, c'est la clarte du fond qui decide`() {
        // Sinon un fond noir choisi à la main hériterait des accents du thème clair.
        val sombre = WidgetColorSettings(theme = WidgetTheme.CUSTOM, background = 0xFF11131AL)
        val clair = WidgetColorSettings(theme = WidgetTheme.CUSTOM, background = 0xFFFDF3E3L)
        assertTrue(sombre.isDark(systemDark = false))
        assertFalse(clair.isDark(systemDark = true))
    }

    @Test
    fun `un fond choisi sans texte choisi donne le texte lisible`() {
        val sombre = WidgetColorSettings(theme = WidgetTheme.CUSTOM, background = black)
        val clair = WidgetColorSettings(theme = WidgetTheme.CUSTOM, background = white)
        assertEquals(white, sombre.inkOrDefault())
        assertEquals(black, clair.inkOrDefault())
    }

    @Test
    fun `un texte choisi n'est jamais remplace`() {
        val colors = WidgetColorSettings(theme = WidgetTheme.CUSTOM, background = black, ink = 0xFFEFA36AL)
        assertEquals(0xFFEFA36AL, colors.inkOrDefault())
    }

    @Test
    fun `sans fond ni texte, rien n'est inventé`() {
        assertNull(WidgetColorSettings(theme = WidgetTheme.CUSTOM).inkOrDefault())
    }

    // ── Contraste ────────────────────────────────────────────────────────────

    @Test
    fun `le noir et le blanc sont au contraste maximal`() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.05)
        assertEquals(1.0, Contrast.ratio(black, black), 0.001)
    }

    @Test
    fun `le contraste ne depend pas de l'ordre`() {
        assertEquals(Contrast.ratio(black, 0xFF7779E8L), Contrast.ratio(0xFF7779E8L, black), 0.0001)
    }

    @Test
    fun `un gris sur un gris tombe sous le seuil de lisibilite`() {
        assertTrue(Contrast.ratio(0xFF808080L, 0xFF999999L) < Contrast.MINIMUM_RATIO)
        assertTrue(Contrast.ratio(white, 0xFF18202BL) >= Contrast.MINIMUM_RATIO)
    }

    @Test
    fun `chaque fond propose a un texte automatique lisible`() {
        // La palette ne doit contenir aucune couleur sur laquelle ni le noir ni le blanc ne
        // se lisent : proposer un choix qui rend le widget illisible serait un piège.
        WidgetPaletteChoices.BACKGROUNDS.forEach { background ->
            val ink = Contrast.readableInk(background)
            assertTrue(
                "fond ${background.toString(16)} illisible",
                Contrast.ratio(background, ink) >= Contrast.MINIMUM_RATIO,
            )
        }
    }

    // ── Persistance ──────────────────────────────────────────────────────────

    @Test
    fun `les couleurs survivent a l'enregistrement`() {
        val settings = AppSettings(
            widgetColors = WidgetColorSettings(
                theme = WidgetTheme.CUSTOM,
                background = 0xFF1E2A24L,
                ink = 0xFFEDF1F7L,
            ),
        )
        assertEquals(settings.widgetColors, SettingsCodec.decode(SettingsCodec.encode(settings)).widgetColors)
    }

    @Test
    fun `un texte automatique reste automatique apres relecture`() {
        // Une couleur absente est une clé absente : la relire comme du noir figerait un
        // choix que personne n'a fait.
        val settings = AppSettings(
            widgetColors = WidgetColorSettings(theme = WidgetTheme.CUSTOM, background = black),
        )
        val relu = SettingsCodec.decode(SettingsCodec.encode(settings)).widgetColors
        assertNull(relu.ink)
        assertEquals(white, relu.inkOrDefault())
    }

    @Test
    fun `des reglages d'avant la fonctionnalite se relisent sans couleurs`() {
        val ancien = """{"theme":"DARK","biometricLock":true}"""
        assertEquals(WidgetColorSettings(), SettingsCodec.decode(ancien).widgetColors)
    }
}
