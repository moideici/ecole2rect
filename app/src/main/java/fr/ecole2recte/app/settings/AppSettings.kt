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

    /** Supprime les animations de l'application, en plus du réglage Android du même nom. */
    private val mutableReduceMotion = MutableStateFlow(preferences.getBoolean("reduce_motion", false))
    val reduceMotion: StateFlow<Boolean> = mutableReduceMotion.asStateFlow()

    fun setReduceMotion(enabled: Boolean) {
        preferences.edit().putBoolean("reduce_motion", enabled).apply()
        mutableReduceMotion.value = enabled
    }

    /** Verrouille l'application derrière l'empreinte, le visage ou le code de l'appareil. */
    private val mutableAppLock = MutableStateFlow(preferences.getBoolean("app_lock", false))
    val appLock: StateFlow<Boolean> = mutableAppLock.asStateFlow()

    fun setAppLock(enabled: Boolean) {
        preferences.edit().putBoolean("app_lock", enabled).apply()
        mutableAppLock.value = enabled
    }

    private val mutableHomeLayout = MutableStateFlow(HomeLayout.parse(preferences.getString("home_layout", null)))
    val homeLayout: StateFlow<HomeLayout> = mutableHomeLayout.asStateFlow()

    fun setHomeLayout(layout: HomeLayout) {
        preferences.edit().putString("home_layout", layout.serialize()).apply()
        mutableHomeLayout.value = layout
    }
}
