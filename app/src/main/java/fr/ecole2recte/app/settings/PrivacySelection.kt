package fr.ecole2recte.app.settings

import android.content.Context
import fr.ecole2recte.app.domain.PrivacyMode

/**
 * Le mode « montrer à quelqu'un » lu sans passer par l'interface : la couche widget peut s'en servir
 * pour masquer les notes (`PrivacySelection.mode(context).grade("14/20")`).
 */
object PrivacySelection {
    const val MODE_KEY = "privacy_mode"
    const val MESSAGES_KEY = "privacy_hide_messages"

    fun mode(context: Context): PrivacyMode {
        val preferences = context.getSharedPreferences(ThemeSelection.PREFS, Context.MODE_PRIVATE)
        return PrivacyMode(preferences.getBoolean(MODE_KEY, false), preferences.getBoolean(MESSAGES_KEY, false))
    }
}
