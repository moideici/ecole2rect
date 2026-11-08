package fr.ecole2recte.app.domain

import fr.ecole2recte.app.data.ProfileKeyValueStore
import org.json.JSONObject

data class SubjectVisit(val id: String, val title: String, val count: Int, val lastOpened: Long)

class SubjectVisits(private val store: ProfileKeyValueStore) {
    fun read(owner: String): List<SubjectVisit> = store.values(owner).mapNotNull { (id, raw) ->
        runCatching { val json = JSONObject(raw); SubjectVisit(id, json.getString("title"), json.getInt("count"), json.getLong("last")) }.getOrNull()
    }
    fun record(owner: String, id: String, title: String, at: Long) {
        if (owner.isBlank() || DeepLinks.subject(id) == null || title.isBlank()) return
        val old = read(owner).firstOrNull { it.id == id }
        store.put(owner, mapOf(id to JSONObject().put("title", title).put("count", ((old?.count ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).put("last", at).toString()))
    }
    fun clear(identifier: String) = store.clear(identifier)
}

object ShortcutLogic {
    fun resolveSubject(requested: String, subjects: List<SubjectAverage>): String? =
        subjects.firstOrNull { it.id == requested }?.id
            ?: subjects.filter { it.subjectCode.isNotBlank() && it.subjectCode == requested }.singleOrNull()?.id
            ?: subjects.filter { SubjectIdentity.normalized(it.subject.name) == SubjectIdentity.normalized(requested) }.singleOrNull()?.id

    fun select(visits: List<SubjectVisit>, available: Set<String>, hidden: Boolean): List<SubjectVisit> =
        if (hidden) emptyList() else visits.filter { it.id in available && it.count > 0 && DeepLinks.subject(it.id) != null && it.title.isNotBlank() }
            .distinctBy { it.id }.sortedWith(compareByDescending<SubjectVisit> { it.count }.thenByDescending { it.lastOpened }.thenBy { it.id }).take(4)
}
