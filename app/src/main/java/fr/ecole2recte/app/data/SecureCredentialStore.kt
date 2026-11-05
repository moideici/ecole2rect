package fr.ecole2recte.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

class SecureCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.getSharedPreferences("ecole2recte_secure", Context.MODE_PRIVATE)
    private val keyAlias = "ecole2recte_credentials"

    private class Snapshot(val logins: List<SavedLogin>, val active: String?)

    private fun load(): Snapshot {
        preferences.getString("accounts", null)?.let { encrypted ->
            runCatching {
                val json = JSONObject(decrypt(encrypted))
                val array = json.optJSONArray("logins") ?: JSONArray()
                return Snapshot(
                    logins = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toSavedLogin() },
                    active = json.optString("active").takeIf { it.isNotBlank() },
                )
            }
        }
        // Migration : l'ancienne version ne gardait qu'un seul compte, sous la clé « login ».
        val legacy = preferences.getString("login", null)?.let { encrypted ->
            runCatching { JSONObject(decrypt(encrypted)).toSavedLogin() }.getOrNull()
        }
        return Snapshot(listOfNotNull(legacy), legacy?.identifier)
    }

    private fun store(snapshot: Snapshot) {
        val json = JSONObject()
            .put("logins", JSONArray(snapshot.logins.map { it.toJson() }))
            .put("active", snapshot.active ?: JSONObject.NULL)
        preferences.edit().putString("accounts", encrypt(json.toString())).remove("login").apply()
    }

    override fun readAll(): List<SavedLogin> = load().logins

    override fun activeIdentifier(): String? = load().active

    override fun save(login: SavedLogin, makeActive: Boolean) {
        val current = load()
        val others = current.logins.filterNot { it.identifier.equals(login.identifier, ignoreCase = true) }
        val index = current.logins.indexOfFirst { it.identifier.equals(login.identifier, ignoreCase = true) }
        val logins = if (index >= 0) current.logins.toMutableList().also { it[index] = login } else others + login
        store(Snapshot(logins, if (makeActive || current.active == null) login.identifier else current.active))
    }

    override fun setActive(identifier: String) {
        val current = load()
        current.logins.firstOrNull { it.identifier.equals(identifier, ignoreCase = true) }?.let {
            store(Snapshot(current.logins, it.identifier))
        }
    }

    override fun remove(identifier: String) {
        val current = load()
        val logins = current.logins.filterNot { it.identifier.equals(identifier, ignoreCase = true) }
        val active = current.active?.takeIf { id -> logins.any { it.identifier.equals(id, ignoreCase = true) } }
            ?: logins.firstOrNull()?.identifier
        store(Snapshot(logins, active))
    }

    override fun clear() {
        preferences.edit().clear().apply()
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
                    .put(
                        "students",
                        JSONArray(
                            p.students.map { s ->
                                JSONObject().put("id", s.id).put("firstName", s.firstName)
                                    .put("lastName", s.lastName).put("className", s.className)
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
                    Student(s.optString("id"), s.optString("firstName"), s.optString("lastName"), s.optString("className"))
                },
            )
        },
        activeStudentId = optString("activeStudentId").takeIf { it.isNotBlank() },
        session = optJSONObject("session")?.toSavedSession(),
    )

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }
}
