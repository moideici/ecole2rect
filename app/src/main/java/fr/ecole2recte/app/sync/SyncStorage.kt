package fr.ecole2recte.app.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Ce que la synchronisation lit et écrit comme réglages. Abstrait pour les tests. */
interface SyncPrefs {
    val isEnabled: Boolean

    /** Ce qu'on veut recevoir. Par défaut tout, sans mode nuit (les tests n'ont pas d'heure). */
    val rules: NotificationRules get() = NotificationRules(quietNight = false)

    /** Arrête la synchronisation et retient pourquoi. */
    fun disable(reason: String)
    fun recordSync(at: Long)
}

/** Réglages de la synchronisation en arrière-plan, observables par l'écran. Désactivée par défaut. */
class SyncPreferences(context: Context) : SyncPrefs {
    private val preferences = context.getSharedPreferences("ecole2recte_sync_prefs", Context.MODE_PRIVATE)

    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    private val mutableLastSync = MutableStateFlow(preferences.getLong("last_sync", 0L).takeIf { it > 0 })
    private val mutableReason = MutableStateFlow(preferences.getString("reason", null))

    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    val lastSyncMillis: StateFlow<Long?> = mutableLastSync.asStateFlow()

    /** Pourquoi la synchronisation s'est arrêtée toute seule, s'il y a lieu. */
    val disabledReason: StateFlow<String?> = mutableReason.asStateFlow()

    override val isEnabled: Boolean get() = mutableEnabled.value

    private val mutableRules = MutableStateFlow(
        NotificationRules(
            schedule = preferences.getBoolean("rule_schedule", true),
            grades = preferences.getBoolean("rule_grades", true),
            messages = preferences.getBoolean("rule_messages", true),
            homework = preferences.getBoolean("rule_homework", true),
            quietNight = preferences.getBoolean("rule_quiet_night", true),
        ),
    )
    val rulesFlow: StateFlow<NotificationRules> = mutableRules.asStateFlow()
    override val rules: NotificationRules get() = mutableRules.value

    fun setRules(value: NotificationRules) {
        preferences.edit()
            .putBoolean("rule_schedule", value.schedule)
            .putBoolean("rule_grades", value.grades)
            .putBoolean("rule_messages", value.messages)
            .putBoolean("rule_homework", value.homework)
            .putBoolean("rule_quiet_night", value.quietNight)
            .apply()
        mutableRules.value = value
    }

    /** Intervalle entre deux vérifications, en heures : 2, 4 ou 8. */
    private val mutableInterval = MutableStateFlow(preferences.getInt("interval_hours", 4))
    val intervalHours: StateFlow<Int> = mutableInterval.asStateFlow()

    fun setIntervalHours(hours: Int) {
        preferences.edit().putInt("interval_hours", hours).apply()
        mutableInterval.value = hours
    }

    fun setEnabled(value: Boolean) {
        val editor = preferences.edit().putBoolean("enabled", value)
        if (value) editor.remove("reason")
        editor.apply()
        mutableEnabled.value = value
        if (value) mutableReason.value = null
    }

    override fun disable(reason: String) {
        preferences.edit().putBoolean("enabled", false).putString("reason", reason).apply()
        mutableEnabled.value = false
        mutableReason.value = reason
    }

    override fun recordSync(at: Long) {
        preferences.edit().putLong("last_sync", at).apply()
        mutableLastSync.value = at
    }
}

/** Mémoire de ce qui a déjà été notifié, par compte et par élève. */
interface NotificationStateStore {
    fun load(owner: String): NotifiedIds?
    fun save(owner: String, ids: NotifiedIds)
}

class NotificationState(context: Context) : NotificationStateStore {
    private val preferences = context.getSharedPreferences("ecole2recte_notified", Context.MODE_PRIVATE)

    override fun load(owner: String): NotifiedIds? = runCatching {
        val json = JSONObject(preferences.getString(owner, null) ?: return null)
        NotifiedIds(
            grades = json.strings("grades"),
            messages = json.strings("messages"),
            canceled = json.strings("canceled"),
            homework = json.strings("homework"),
            lessons = json.optJSONObject("lessons")?.let { lessons ->
                lessons.keys().asSequence().associateWith { lessons.getString(it) }
            }.orEmpty(),
        )
    }.getOrNull()

    override fun save(owner: String, ids: NotifiedIds) {
        val json = JSONObject()
            .put("grades", JSONArray(ids.grades.toList()))
            .put("messages", JSONArray(ids.messages.toList()))
            .put("canceled", JSONArray(ids.canceled.toList()))
            .put("homework", JSONArray(ids.homework.toList()))
            .put("lessons", JSONObject(ids.lessons))
        preferences.edit().putString(owner, json.toString()).apply()
    }

    private fun JSONObject.strings(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length()).map { array.getString(it) }.toCollection(LinkedHashSet())
    }
}

/** Ce que les widgets affichent : un instantané écrit à chaque synchronisation. */
data class WidgetLesson(val subject: String, val timeLabel: String, val place: String)

data class WidgetTask(val subject: String, val title: String, val due: String)

data class WidgetSnapshot(val updatedAt: Long, val nextClass: WidgetLesson?, val tasks: List<WidgetTask>) {
    fun toJson(): String = JSONObject()
        .put("updatedAt", updatedAt)
        .put(
            "nextClass",
            nextClass?.let { JSONObject().put("subject", it.subject).put("time", it.timeLabel).put("place", it.place) }
                ?: JSONObject.NULL,
        )
        .put(
            "tasks",
            JSONArray(tasks.map { JSONObject().put("subject", it.subject).put("title", it.title).put("due", it.due) }),
        )
        .toString()

    companion object {
        fun fromJson(json: String): WidgetSnapshot? = runCatching {
            val root = JSONObject(json)
            val next = root.optJSONObject("nextClass")
            val tasks = root.optJSONArray("tasks") ?: JSONArray()
            WidgetSnapshot(
                updatedAt = root.optLong("updatedAt"),
                nextClass = next?.let { WidgetLesson(it.optString("subject"), it.optString("time"), it.optString("place")) },
                tasks = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
                    .map { WidgetTask(it.optString("subject"), it.optString("title"), it.optString("due")) },
            )
        }.getOrNull()
    }
}

class WidgetSnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences("ecole2recte_widgets", Context.MODE_PRIVATE)

    fun read(): WidgetSnapshot? = preferences.getString("snapshot", null)?.let(WidgetSnapshot::fromJson)

    fun write(snapshot: WidgetSnapshot) {
        preferences.edit().putString("snapshot", snapshot.toJson()).apply()
    }

    fun clear() {
        preferences.edit().remove("snapshot").apply()
    }
}
