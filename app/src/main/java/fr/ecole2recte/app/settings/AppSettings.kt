package fr.ecole2recte.app.settings

import android.content.Context
import fr.ecole2recte.app.ui.schedule.ScheduleZoom
import fr.ecole2recte.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_API_VERSION = "4.75.0"

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

    /** Envoie aussi à ÉcoleDirecte les devoirs cochés « faits ». Éteint par défaut. */
    private val mutableSyncHomework = MutableStateFlow(preferences.getBoolean("sync_homework", false))
    val syncHomework: StateFlow<Boolean> = mutableSyncHomework.asStateFlow()
    val syncHomeworkAcknowledged: Boolean get() = preferences.getBoolean("sync_homework_ack", false)

    fun setSyncHomework(enabled: Boolean) {
        val editor = preferences.edit().putBoolean("sync_homework", enabled)
        if (enabled) editor.putBoolean("sync_homework_ack", true)
        editor.apply()
        mutableSyncHomework.value = enabled
    }

    /** Autorise l'écriture et l'envoi de messages depuis l'application. Éteint par défaut. */
    private val mutableSendMessages = MutableStateFlow(preferences.getBoolean("send_messages", false))
    val sendMessages: StateFlow<Boolean> = mutableSendMessages.asStateFlow()
    val sendMessagesAcknowledged: Boolean get() = preferences.getBoolean("send_messages_ack", false)

    fun setSendMessages(enabled: Boolean) {
        val editor = preferences.edit().putBoolean("send_messages", enabled)
        if (enabled) editor.putBoolean("send_messages_ack", true)
        editor.apply()
        mutableSendMessages.value = enabled
    }

    /** Ce qui est autorisé en écriture, lu au moment de l'action : un réglage éteint coupe l'écriture aussitôt. */
    val writeAccess: fr.ecole2recte.app.data.WriteAccess = object : fr.ecole2recte.app.data.WriteAccess {
        override fun homeworkSync() = mutableSyncHomework.value
        override fun messageSending() = mutableSendMessages.value
    }

    /** L'avis du premier lancement (application non officielle, usage des identifiants) a été accepté. */
    private val mutableNoticeAccepted = MutableStateFlow(preferences.getBoolean("notice_accepted", false))
    val noticeAccepted: StateFlow<Boolean> = mutableNoticeAccepted.asStateFlow()

    fun acceptNotice() {
        preferences.edit().putBoolean("notice_accepted", true).apply()
        mutableNoticeAccepted.value = true
    }

    /**
     * Masque l'aperçu de l'application dans la liste des applications récentes (et bloque les
     * captures d'écran) quand le verrou est actif. Éteint par défaut : il empêche aussi les captures.
     */
    private val mutableHidePreview = MutableStateFlow(preferences.getBoolean("hide_preview", false))
    val hidePreview: StateFlow<Boolean> = mutableHidePreview.asStateFlow()

    fun setHidePreview(enabled: Boolean) {
        preferences.edit().putBoolean("hide_preview", enabled).apply()
        mutableHidePreview.value = enabled
    }

    /** Version de l'API envoyée à ÉcoleDirecte ; modifiable si le service refuse l'ancienne (code 517). */
    private val mutableApiVersion = MutableStateFlow(preferences.getString("api_version", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_API_VERSION)
    val apiVersion: StateFlow<String> = mutableApiVersion.asStateFlow()

    fun setApiVersion(version: String) {
        val clean = version.trim().ifBlank { DEFAULT_API_VERSION }
        preferences.edit().putString("api_version", clean).apply()
        mutableApiVersion.value = clean
    }

    /** Échelle verticale de l'agenda (pincement ou boutons + / −), gardée d'une ouverture à l'autre. */
    private val mutableScheduleZoom = MutableStateFlow(ScheduleZoom.clamp(preferences.getFloat("schedule_zoom", ScheduleZoom.DEFAULT)))
    val scheduleZoom: StateFlow<Float> = mutableScheduleZoom.asStateFlow()

    fun setScheduleZoom(zoom: Float) {
        val clean = ScheduleZoom.clamp(zoom)
        if (clean == mutableScheduleZoom.value) return
        preferences.edit().putFloat("schedule_zoom", clean).apply()
        mutableScheduleZoom.value = clean
    }

    /** L'agenda s'ouvre sur la vue semaine plutôt que sur la vue jour. */
    private val mutableScheduleWeekView = MutableStateFlow(preferences.getBoolean("schedule_week_view", false))
    val scheduleWeekView: StateFlow<Boolean> = mutableScheduleWeekView.asStateFlow()

    fun setScheduleWeekView(week: Boolean) {
        preferences.edit().putBoolean("schedule_week_view", week).apply()
        mutableScheduleWeekView.value = week
    }

    private val mutableHomeLayout =MutableStateFlow(HomeLayout.parse(preferences.getString("home_layout", null)))
    val homeLayout: StateFlow<HomeLayout> = mutableHomeLayout.asStateFlow()

    fun setHomeLayout(layout: HomeLayout) {
        preferences.edit().putString("home_layout", layout.serialize()).apply()
        mutableHomeLayout.value = layout
    }
}

/** L'aperçu n'est masqué que si le verrou est actif ET que l'option est cochée. */
fun shouldSecureWindow(lockEnabled: Boolean, hidePreview: Boolean): Boolean = lockEnabled && hidePreview
