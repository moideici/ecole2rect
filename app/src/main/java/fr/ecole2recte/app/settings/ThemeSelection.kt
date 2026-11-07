package fr.ecole2recte.app.settings

import android.content.Context
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.ui.theme.ThemePalette
import fr.ecole2recte.app.ui.theme.ThemePalettes

/**
 * Le thème choisi dans Plus > Apparence, lisible sans passer par l'interface : c'est ce que la couche
 * widget appelle pour peindre ses vues dans les couleurs de l'application.
 * `ThemeSelection.palette(context, systemDark)` donne les couleurs en ARGB ([ThemePalette]).
 */
object ThemeSelection {
    const val PREFS = "ecole2recte_settings"
    const val MODE_KEY = "theme_mode"
    const val COLOR_THEME_KEY = "color_theme"

    fun mode(context: Context): ThemeMode =
        ThemeMode.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODE_KEY, null))

    fun colorTheme(context: Context): ColorTheme =
        ColorTheme.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(COLOR_THEME_KEY, null))

    /** [systemDark] : le mode sombre du système, utile quand le choix est « Système ». */
    fun palette(context: Context, systemDark: Boolean): ThemePalette =
        ThemePalettes.of(colorTheme(context), mode(context).appearance(systemDark))
}
