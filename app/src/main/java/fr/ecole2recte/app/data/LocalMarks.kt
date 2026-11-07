package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.MessagePreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Petites données **locales** rangées par profil (compte et élève) : jamais envoyées nulle part.
 * Chaque profil a sa table clé → valeur ; [ownerKey] est la clé du profil actif (`identifiant/élève`).
 */
class ProfileKeyValueStore(private val storage: TextStorage) {
    private val state = MutableStateFlow(load())

    private fun load(): Map<String, Map<String, String>> = runCatching {
        val root = JSONObject(storage.read() ?: return emptyMap())
        root.keys().asSequence().associate { owner ->
            val values = root.getJSONObject(owner)
            owner to values.keys().asSequence().associateWith { values.getString(it) }
        }
    }.getOrDefault(emptyMap())

    private fun save(value: Map<String, Map<String, String>>) {
        val root = JSONObject()
        value.forEach { (owner, values) -> root.put(owner, JSONObject(values)) }
        storage.write(root.toString())
    }

    fun values(ownerKey: String): Map<String, String> = state.value[ownerKey.lowercase()].orEmpty()

    /** Les valeurs du profil, mises à jour en direct. */
    fun flow(ownerKey: String): Flow<Map<String, String>> = state.map { it[ownerKey.lowercase()].orEmpty() }.distinctUntilChanged()

    @Synchronized
    fun put(ownerKey: String, entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val owner = ownerKey.lowercase()
        val updated = state.value + (owner to (state.value[owner].orEmpty() + entries))
        state.value = updated
        save(updated)
    }

    @Synchronized
    fun remove(ownerKey: String, key: String) {
        val owner = ownerKey.lowercase()
        val current = state.value[owner] ?: return
        if (key !in current) return
        val left = current - key
        val updated = if (left.isEmpty()) state.value - owner else state.value + (owner to left)
        state.value = updated
        save(updated)
    }

    /** Efface tout ce qui appartient à un compte et à tous ses élèves. */
    @Synchronized
    fun clear(identifier: String) {
        val prefix = identifier.lowercase() + "/"
        val updated = state.value.filterKeys { !it.startsWith(prefix) }
        if (updated.size == state.value.size) return
        state.value = updated
        save(updated)
    }
}

/**
 * « Lu » et « non lu » choisis **sur le téléphone** dans la liste des messages. Rien n'est écrit chez
 * ÉcoleDirecte : c'est un marque-page personnel. Ouvrir un message le marque lu côté serveur (comportement
 * existant) et efface alors la marque locale, le serveur redevenant la référence.
 */
class MessageReadMarks(private val store: ProfileKeyValueStore) {
    /** `true` : marqué lu ici ; `false` : marqué non lu ici. */
    fun marks(ownerKey: String): Flow<Map<String, Boolean>> = store.flow(ownerKey).map(::decode)

    fun current(ownerKey: String): Map<String, Boolean> = decode(store.values(ownerKey))

    fun set(ownerKey: String, messageId: String, read: Boolean) {
        if (messageId.isBlank()) return
        store.put(ownerKey, mapOf(messageId to if (read) "1" else "0"))
    }

    /** Le serveur est redevenu la référence pour ce message (ouvert, donc lu côté ÉcoleDirecte). */
    fun forget(ownerKey: String, messageId: String) = store.remove(ownerKey, messageId)

    fun clear(identifier: String) = store.clear(identifier)

    private fun decode(raw: Map<String, String>): Map<String, Boolean> = raw.mapValues { it.value == "1" }
}

/** Applique les marques locales : elles l'emportent sur l'état lu/non lu envoyé par ÉcoleDirecte. */
fun applyReadMarks(messages: List<MessagePreview>, marks: Map<String, Boolean>): List<MessagePreview> =
    if (marks.isEmpty()) messages
    else messages.map { message ->
        val read = marks[message.id]
        if (read == null || message.id.isBlank()) message else message.copy(unread = !read)
    }

/**
 * Les notes que l'application a déjà vues, avec le moment de la première fois, par profil. Sert à
 * mettre en avant une note « Nouveau » pendant [NEW_GRADE_WINDOW_MS] après son apparition.
 */
class SeenGrades(private val store: ProfileKeyValueStore, private val now: () -> Long = System::currentTimeMillis) {
    fun firstSeen(ownerKey: String): Map<String, Long> =
        store.values(ownerKey).filterKeys { it != INIT }.mapNotNull { (id, at) -> at.toLongOrNull()?.let { id to it } }.toMap()

    /**
     * Retient les notes de [ids] jamais vues. **Le tout premier passage d'un profil ne marque rien de
     * nouveau** (tout l'historique serait « Nouveau ») : les notes existantes sont notées comme vues
     * « depuis toujours » (0). Renvoie la table complète des premières apparitions.
     */
    fun record(ownerKey: String, ids: Collection<String>): Map<String, Long> {
        val initialized = INIT in store.values(ownerKey)
        val known = firstSeen(ownerKey)
        val fresh = ids.filter { it.isNotBlank() && it !in known }
        val at = if (initialized) now() else 0L
        if (fresh.isNotEmpty()) {
            store.put(ownerKey, fresh.associateWith { at.toString() } + (INIT to "1"))
        }
        return firstSeen(ownerKey)
    }

    fun clear(identifier: String) = store.clear(identifier)

    private companion object {
        const val INIT = "_init"
    }
}

/** Une note est « nouvelle » pendant trois jours après la première fois où l'application l'a vue. */
const val NEW_GRADE_WINDOW_MS = 3L * 24 * 60 * 60 * 1000

/** Vrai si la note, vue pour la première fois à [firstSeenAt], l'est encore à [now]. 0 = vue au premier passage, jamais nouvelle. */
fun isNewGrade(firstSeenAt: Long?, now: Long): Boolean =
    firstSeenAt != null && firstSeenAt > 0 && now >= firstSeenAt && now - firstSeenAt < NEW_GRADE_WINDOW_MS

/** Le filtre choisi dans la liste des devoirs, retenu par profil. */
class HomeworkFilterStore(private val store: ProfileKeyValueStore) {
    fun get(ownerKey: String): String? = store.values(ownerKey)["filter"]

    fun set(ownerKey: String, value: String) = store.put(ownerKey, mapOf("filter" to value))

    fun clear(identifier: String) = store.clear(identifier)
}
