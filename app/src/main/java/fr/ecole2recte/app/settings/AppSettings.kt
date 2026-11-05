package fr.ecole2recte.app.settings

import android.content.Context
import fr.ecole2recte.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Réglages de l'application, non secrets, gardés sur l'appareil. */
class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("ecole2recte_settings", Context.MODE_PRIVATE)

    private val mutableThemeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(preferences.getString("theme_mode", null) ?: "") }.getOrDefault(ThemeMode.System),
    )
    val themeMode: StateFlow<ThemeMode> = mutableThemeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
        mutableThemeMode.value = mode
    }
}
