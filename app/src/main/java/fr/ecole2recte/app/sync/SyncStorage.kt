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

    /** Compte un échec de plus et renvoie le nombre d'échecs de suite. */
    fun recordFailure(): Int = 1

    /** Une synchronisation a réussi : on repart de zéro. */
    fun resetFailures() {}
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
            eveningHour = NotificationRules.clampEveningHour(preferences.getInt("rule_evening_hour", NotificationRules.DEFAULT_EVENING_HOUR)),
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
            .putInt("rule_evening_hour", NotificationRules.clampEveningHour(value.eveningHour))
            .apply()
        mutableRules.value = value.copy(eveningHour = NotificationRules.clampEveningHour(value.eveningHour))
    }

    /** Intervalle entre deux vérifications, en heures : 4, 8 ou 12 (jamais moins de 4, même si une ancienne version a gardé 2). */
    private val mutableInterval = MutableStateFlow(SyncIntervals.clamp(preferences.getInt("interval_hours", SyncIntervals.DEFAULT_HOURS)))
    val intervalHours: StateFlow<Int> = mutableInterval.asStateFlow()

    fun setIntervalHours(hours: Int) {
        val allowed = SyncIntervals.clamp(hours)
        preferences.edit().putInt("interval_hours", allowed).apply()
        mutableInterval.value = allowed
    }

    fun setEnabled(value: Boolean) {
        val editor = preferences.edit().putBoolean("enabled", value)
        if (value) editor.remove("reason").putInt("failures", 0)
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

    @Synchronized
    override fun recordFailure(): Int {
        val count = preferences.getInt("failures", 0) + 1
        preferences.edit().putInt("failures", count).apply()
        return count
    }

    @Synchronized
    override fun resetFailures() {
        if (preferences.getInt("failures", 0) != 0) preferences.edit().putInt("failures", 0).apply()
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

data class WidgetTask(
    val subject: String,
    val title: String,
    val due: String,
    val colorIndex: Int = 0,
    val dueDate: String = "",
    val exam: Boolean = false,
)

/** Un cours des prochains jours, avec ses heures absolues : le widget calcule « dans 25 min » à l'affichage. */
data class WidgetSlot(
    val date: String,
    val start: String,
    val end: String,
    val subject: String,
    val room: String,
    val teacher: String,
    val canceled: Boolean,
    val colorIndex: Int,
)

data class WidgetGradeItem(
    val subject: String, val value: String, val coefficient: String, val label: String, val colorIndex: Int,
    val id: String = "", val date: String = "", val classGap: Double? = null, val firstSeenAt: Long = 0,
)

data class WidgetMail(val sender: String, val subject: String)

data class WidgetSnapshot(
    val updatedAt: Long,
    val nextClass: WidgetLesson?,
    val tasks: List<WidgetTask>,
    /** Les cours d'aujourd'hui et des jours suivants. */
    val lessons: List<WidgetSlot> = emptyList(),
    val grades: List<WidgetGradeItem> = emptyList(),
    val average: String = "",
    val unread: Int = 0,
    val mails: List<WidgetMail> = emptyList(),
    val averageLabel: String = "",
    val officialAverage: String = "",
    val averageBehind: String = "",
    val meanPeriods: List<fr.ecole2recte.app.widget.WidgetMeanPeriod> = emptyList(),
    val profileKey: String = "",
    val profileLabel: String = "",
    /** Autres enfants déjà lus pendant le même passage de synchro. */
    val profiles: List<WidgetSnapshot> = emptyList(),
    val exams: List<WidgetTask> = emptyList(),
    val gradeIds: List<String> = emptyList(),
    val owner: String = "",
    val shortcutSubjects: List<String> = emptyList(),
) {
    fun toJson(): String = JSONObject()
        .put("updatedAt", updatedAt)
        .put("owner", owner)
        .put("shortcutSubjects", JSONArray(shortcutSubjects))
        .put("average", average)
        .put("averageLabel", averageLabel)
        .put("officialAverage", officialAverage)
        .put("averageBehind", averageBehind)
        .put("meanPeriods", JSONArray(meanPeriods.map { it.json() }))
        .put("profileKey", profileKey).put("profileLabel", profileLabel)
        .put("profiles", JSONArray(profiles.map { JSONObject(it.copy(profiles = emptyList()).toJson()) }))
        .put("exams", JSONArray(exams.map { JSONObject().put("subject", it.subject).put("dueDate", it.dueDate).put("title", it.title).put("color", it.colorIndex) }))
        .put("gradeIds", JSONArray(gradeIds))
        .put("unread", unread)
        .put(
            "lessons",
            JSONArray(
                lessons.map {
                    JSONObject().put("date", it.date).put("start", it.start).put("end", it.end).put("subject", it.subject)
                        .put("room", it.room).put("teacher", it.teacher).put("canceled", it.canceled).put("color", it.colorIndex)
                },
            ),
        )
        .put(
            "grades",
            JSONArray(
                grades.map {
                    JSONObject().put("subject", it.subject).put("value", it.value).put("coef", it.coefficient)
                        .put("label", it.label).put("color", it.colorIndex)
                        .put("id", it.id).put("date", it.date).put("gap", it.classGap).put("seen", it.firstSeenAt)
                },
            ),
        )
        .put("mails", JSONArray(mails.map { JSONObject().put("sender", it.sender).put("subject", it.subject) }))
        .put(
            "nextClass",
            nextClass?.let { JSONObject().put("subject", it.subject).put("time", it.timeLabel).put("place", it.place) }
                ?: JSONObject.NULL,
        )
        .put(
            "tasks",
            JSONArray(
                tasks.map {
                    JSONObject().put("subject", it.subject).put("title", it.title).put("due", it.due)
                        .put("color", it.colorIndex).put("dueDate", it.dueDate).put("exam", it.exam)
                },
            ),
        )
        .toString()

    companion object {
        fun fromJson(json: String): WidgetSnapshot? = runCatching {
            val root = JSONObject(json)
            val next = root.optJSONObject("nextClass")
            val tasks = root.optJSONArray("tasks") ?: JSONArray()
            WidgetSnapshot(
                updatedAt = root.optLong("updatedAt"),
                owner = root.optString("owner"),
                shortcutSubjects = root.optJSONArray("shortcutSubjects")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
                nextClass = next?.let { WidgetLesson(it.optString("subject"), it.optString("time"), it.optString("place")) },
                tasks = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
                    .map {
                        WidgetTask(
                            it.optString("subject"), it.optString("title"), it.optString("due"),
                            it.optInt("color"), it.optString("dueDate"), it.optBoolean("exam"),
                        )
                    },
                lessons = root.objects("lessons").map {
                    WidgetSlot(
                        it.optString("date"), it.optString("start"), it.optString("end"), it.optString("subject"),
                        it.optString("room"), it.optString("teacher"), it.optBoolean("canceled"), it.optInt("color"),
                    )
                },
                grades = root.objects("grades").map {
                    WidgetGradeItem(it.optString("subject"), it.optString("value"), it.optString("coef"), it.optString("label"), it.optInt("color"),
                        it.optString("id"), it.optString("date"), it.optDouble("gap").takeIf { number -> number.isFinite() }, it.optLong("seen"))
                },
                average = root.optString("average"),
                averageLabel = root.optString("averageLabel"),
                officialAverage = root.optString("officialAverage"),
                averageBehind = root.optString("averageBehind"),
                meanPeriods = root.objects("meanPeriods").map(fr.ecole2recte.app.widget.WidgetMeanPeriod::fromJson),
                profileKey = root.optString("profileKey"), profileLabel = root.optString("profileLabel"),
                profiles = root.objects("profiles").mapNotNull { fromJson(it.toString()) },
                exams = root.objects("exams").map { WidgetTask(it.optString("subject"), it.optString("title"), "", it.optInt("color"), it.optString("dueDate"), true) },
                gradeIds = root.optJSONArray("gradeIds")?.let { ids -> (0 until ids.length()).map { ids.optString(it) } }.orEmpty(),
                unread = root.optInt("unread"),
                mails = root.objects("mails").map { WidgetMail(it.optString("sender"), it.optString("subject")) },
            )
        }.getOrNull()
    }
}

private fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

class WidgetSnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences("ecole2recte_widgets", Context.MODE_PRIVATE)

    fun read(profile: String = ""): WidgetSnapshot? = preferences.getString(if (profile.isBlank()) "snapshot" else "profile:$profile", null)?.let(WidgetSnapshot::fromJson)

    fun profiles(): List<WidgetSnapshot> = preferences.all.keys.filter { it.startsWith("profile:") }.mapNotNull { read(it.removePrefix("profile:")) }.sortedBy { it.profileLabel }

    fun write(snapshot: WidgetSnapshot) {
        val editor = preferences.edit().putString("snapshot", snapshot.copy(profiles = emptyList()).toJson())
        (listOf(snapshot) + snapshot.profiles).filter { it.profileKey.isNotBlank() }.forEach {
            editor.putString("profile:${it.profileKey}", it.copy(profiles = emptyList()).toJson())
        }
        editor.apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun clearAccount(identifier: String) {
        val prefix = identifier.lowercase() + "/"
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("profile:$prefix") }.forEach { editor.remove(it) }
        if (read()?.profileKey?.startsWith(prefix) == true) editor.remove("snapshot")
        editor.apply()
    }
}
