package fr.ecole2recte.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Date de naissance saisie par l'utilisateur, gardée pour répondre seule à une question de sécurité. */
data class BirthDate(val day: String, val month: String, val year: String) {
    val isComplete: Boolean get() = day.isNotBlank() && month.isNotBlank() && year.isNotBlank()
}

/** Ce qu'on retient du compte après une connexion réussie, pour l'afficher sans réseau. */
data class AccountProfile(
    val firstName: String,
    val lastName: String,
    val type: String,
    val students: List<Student> = emptyList(),
    val modules: Map<String, Boolean> = emptyMap(),
)

data class SavedLogin(
    val identifier: String,
    val password: String,
    val trustedAnswer: TrustedAnswer? = null,
    val birthDate: BirthDate? = null,
    val profile: AccountProfile? = null,
    /** Élève affiché pour un compte famille. */
    val activeStudentId: String? = null,
    /** Session rejouable au prochain lancement (voir [TokenReplayPolicy]). */
    val session: SavedSession? = null,
)

/** Stockage des comptes enregistrés sur l'appareil. Abstrait pour pouvoir être remplacé dans les tests. */
interface CredentialStore {
    fun readAll(): List<SavedLogin>
    fun activeIdentifier(): String?

    /** Ajoute ou met à jour le compte (même identifiant, sans tenir compte de la casse). */
    fun save(login: SavedLogin, makeActive: Boolean = true)
    fun setActive(identifier: String)
    fun remove(identifier: String)
    fun clear()

    /** Vrai si des comptes sont enregistrés mais n'ont pas pu être déchiffrés : il faut se reconnecter. */
    fun isUnreadable(): Boolean = false

    /** Modifie un compte existant sans changer le compte actif ; sans effet s'il n'existe plus. */
    fun update(identifier: String, transform: (SavedLogin) -> SavedLogin) {
        find(identifier)?.let { save(transform(it), makeActive = false) }
    }
}

fun CredentialStore.find(identifier: String): SavedLogin? =
    readAll().firstOrNull { it.identifier.equals(identifier, ignoreCase = true) }

/** Compte actif, ou à défaut le premier enregistré. */
fun CredentialStore.active(): SavedLogin? =
    activeIdentifier()?.let(::find) ?: readAll().firstOrNull()

/** Où le texte chiffré des comptes est gardé. Abstrait pour pouvoir être remplacé dans les tests. */
interface BlobStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

class PrefsBlobStorage(context: Context, name: String) : BlobStorage {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String) = preferences.edit().putString(key, value).apply()
    override fun remove(key: String) = preferences.edit().remove(key).apply()
    override fun clear() = preferences.edit().clear().apply()
}

/**
 * Les comptes enregistrés, chiffrés. Toutes les opérations sont sérialisées (l'application et la
 * synchro de fond écrivent chacune de leur côté) et **rien n'est jamais écrit sur des données
 * illisibles** : si le déchiffrement échoue, les comptes sont considérés comme absents mais le
 * texte chiffré reste intact, et seule une nouvelle connexion explicite le remplace.
 */
class SecureCredentialStore(
    private val storage: BlobStorage,
    private val cipher: ValueCipher,
) : CredentialStore {
    constructor(context: Context) : this(PrefsBlobStorage(context, "ecole2recte_secure"), KeystoreCipher())

    private class Snapshot(val logins: List<SavedLogin>, val active: String?, val unreadable: Boolean = false)

    private var cachedFrom: String? = null
    private var cachedSnapshot: Snapshot? = null

    private fun load(): Snapshot {
        val encrypted = storage.get("accounts")
        if (encrypted != null) {
            if (encrypted == cachedFrom) cachedSnapshot?.let { return it }
            val snapshot = runCatching {
                val json = JSONObject(cipher.decrypt(encrypted))
                val array = json.optJSONArray("logins") ?: JSONArray()
                Snapshot(
                    logins = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toSavedLogin() },
                    active = json.optString("active").takeIf { it.isNotBlank() },
                )
            }.getOrElse { Snapshot(emptyList(), null, unreadable = true) }
            cachedFrom = encrypted
            cachedSnapshot = snapshot
            return snapshot
        }
        // Migration : l'ancienne version ne gardait qu'un seul compte, sous la clé « login ».
        val legacyText = storage.get("login") ?: return Snapshot(emptyList(), null)
        return runCatching {
            val legacy = JSONObject(cipher.decrypt(legacyText)).toSavedLogin()
            Snapshot(listOf(legacy), legacy.identifier)
        }.getOrElse { Snapshot(emptyList(), null, unreadable = true) }
    }

    private fun store(snapshot: Snapshot) {
        val json = JSONObject()
            .put("logins", JSONArray(snapshot.logins.map { it.toJson() }))
            .put("active", snapshot.active ?: JSONObject.NULL)
        val encrypted = cipher.encrypt(json.toString())
        storage.put("accounts", encrypted)
        storage.remove("login")
        cachedFrom = encrypted
        cachedSnapshot = Snapshot(snapshot.logins, snapshot.active)
    }

    @Synchronized
    override fun readAll(): List<SavedLogin> = load().logins

    @Synchronized
    override fun activeIdentifier(): String? = load().active

    @Synchronized
    override fun isUnreadable(): Boolean = load().unreadable

    /** Enregistrer un compte est le seul geste qui remplace des données illisibles : c'est une reconnexion explicite. */
    @Synchronized
    override fun save(login: SavedLogin, makeActive: Boolean) {
        val current = load()
        val others = current.logins.filterNot { it.identifier.equals(login.identifier, ignoreCase = true) }
        val index = current.logins.indexOfFirst { it.identifier.equals(login.identifier, ignoreCase = true) }
        val logins = if (index >= 0) current.logins.toMutableList().also { it[index] = login } else others + login
        store(Snapshot(logins, if (makeActive || current.active == null) login.identifier else current.active))
    }

    @Synchronized
    override fun update(identifier: String, transform: (SavedLogin) -> SavedLogin) {
        val current = load()
        if (current.unreadable) return
        val index = current.logins.indexOfFirst { it.identifier.equals(identifier, ignoreCase = true) }
        if (index < 0) return
        val logins = current.logins.toMutableList().also { it[index] = transform(it[index]) }
        store(Snapshot(logins, current.active))
    }

    @Synchronized
    override fun setActive(identifier: String) {
        val current = load()
        if (current.unreadable) return
        current.logins.firstOrNull { it.identifier.equals(identifier, ignoreCase = true) }?.let {
            store(Snapshot(current.logins, it.identifier))
        }
    }

    @Synchronized
    override fun remove(identifier: String) {
        val current = load()
        if (current.unreadable) return
        val logins = current.logins.filterNot { it.identifier.equals(identifier, ignoreCase = true) }
        val active = current.active?.takeIf { id -> logins.any { it.identifier.equals(id, ignoreCase = true) } }
            ?: logins.firstOrNull()?.identifier
        store(Snapshot(logins, active))
    }

    @Synchronized
    override fun clear() {
        storage.clear()
        cachedFrom = null
        cachedSnapshot = null
    }

    private fun SavedLogin.toJson(): JSONObject {
        val json = JSONObject().put("identifier", identifier).put("password", password)
        trustedAnswer?.let { json.put("trustedAnswer", JSONObject().put("cn", it.cn).put("cv", it.cv)) }
        birthDate?.takeIf { it.isComplete }?.let {
            json.put("birthDate", JSONObject().put("day", it.day).put("month", it.month).put("year", it.year))
        }
        profile?.let { p ->
            json.put(
                "profile",
                JSONObject()
                    .put("firstName", p.firstName)
                    .put("lastName", p.lastName)
                    .put("type", p.type)
                    .put("modules", p.modules.toModulesJson())
                    .put(
                        "students",
                        JSONArray(
                            p.students.map { s ->
                                JSONObject().put("id", s.id).put("firstName", s.firstName)
                                    .put("lastName", s.lastName).put("className", s.className)
                                    .put("modules", s.modules.toModulesJson())
                            },
                        ),
                    ),
            )
        }
        activeStudentId?.let { json.put("activeStudentId", it) }
        session?.let { json.put("session", it.toJson()) }
        return json
    }

    private fun JSONObject.toSavedLogin(): SavedLogin = SavedLogin(
        identifier = getString("identifier"),
        password = getString("password"),
        trustedAnswer = optJSONObject("trustedAnswer")?.let { TrustedAnswer(it.getString("cn"), it.getString("cv")) },
        birthDate = optJSONObject("birthDate")?.let {
            BirthDate(it.getString("day"), it.getString("month"), it.getString("year"))
        },
        profile = optJSONObject("profile")?.let { p ->
            val students = p.optJSONArray("students") ?: JSONArray()
            AccountProfile(
                firstName = p.optString("firstName"),
                lastName = p.optString("lastName"),
                type = p.optString("type"),
                students = (0 until students.length()).mapNotNull { students.optJSONObject(it) }.map { s ->
                    Student(s.optString("id"), s.optString("firstName"), s.optString("lastName"), s.optString("className"), s.optJSONObject("modules").toModuleMap())
                },
                modules = p.optJSONObject("modules").toModuleMap(),
            )
        },
        activeStudentId = optString("activeStudentId").takeIf { it.isNotBlank() },
        session = optJSONObject("session")?.toSavedSession(),
    )
}
