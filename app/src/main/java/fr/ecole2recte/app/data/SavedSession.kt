package fr.ecole2recte.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * La session ouverte avec ÉcoleDirecte, gardée (chiffrée avec le mot de passe) pour être rejouée
 * au lancement suivant au lieu de se reconnecter : moins de connexions, donc moins de risques de
 * blocage et moins de questions de sécurité.
 */
data class SavedSession(
    val token: String,
    val cookies: Map<String, String>,
    val gtk: String,
    /** Empreinte réseau utilisée : une session n'est rejouée qu'avec la même. */
    val userAgent: String,
    /** Quand le jeton a été obtenu par une vraie connexion. */
    val issuedAt: Long,
    /** Dernière fois qu'ÉcoleDirecte a répondu correctement avec ce jeton. */
    val lastOkAt: Long,
    val accounts: List<Account>,
) {
    /** Garde la plus récente de deux sessions du même compte (l'application et la synchro écrivent toutes deux). */
    fun newest(other: SavedSession?): SavedSession =
        if (other == null || lastOkAt >= other.lastOkAt) this else other
}

/**
 * Ce que le téléphone a mesuré de la durée de vie du jeton. ÉcoleDirecte ne la documente pas :
 * on la déduit des expirations réellement rencontrées et des rejeux réussis.
 */
data class TokenLifetimes(
    /** Plus courte inactivité après laquelle un jeton a été trouvé expiré (le jeton a donc vécu moins). */
    val expiredAfterIdleMs: Long? = null,
    /** Plus courte ancienneté (depuis la connexion) à laquelle un jeton a été trouvé expiré. */
    val expiredAfterAgeMs: Long? = null,
    /** Plus longue inactivité après laquelle un jeton gardé a encore fonctionné. */
    val replayedAfterIdleMs: Long? = null,
)

/**
 * Décide si une session gardée vaut la peine d'être rejouée. Prudente : fenêtre de 10 minutes tant
 * qu'on ne sait rien, élargie par les rejeux réussis, réduite par les expirations constatées, et
 * jamais si le jeton a déjà expiré en quelques minutes.
 */
object TokenReplayPolicy {
    const val BASE_WINDOW_MS = 10 * 60_000L
    const val MIN_USEFUL_MS = 3 * 60_000L
    const val MAX_WINDOW_MS = 4 * 60 * 60_000L

    /** Inactivité maximale tolérée pour rejouer ; 0 : ne jamais rejouer. */
    fun idleWindowMs(known: TokenLifetimes): Long {
        var window = BASE_WINDOW_MS
        known.replayedAfterIdleMs?.let { window = maxOf(window, minOf(it * 2, MAX_WINDOW_MS)) }
        known.expiredAfterIdleMs?.let { expired ->
            if (expired < MIN_USEFUL_MS) return 0
            window = minOf(window, expired * 8 / 10)
        }
        return window
    }

    fun canReplay(session: SavedSession, now: Long, known: TokenLifetimes, currentUserAgent: String): Boolean {
        if (session.token.isBlank() || session.userAgent != currentUserAgent) return false
        val idle = now - session.lastOkAt
        if (idle < 0 || idle > idleWindowMs(known)) return false
        known.expiredAfterAgeMs?.let { expired ->
            if (expired < MIN_USEFUL_MS || now - session.issuedAt > expired * 8 / 10) return false
        }
        return true
    }
}

/** Où les mesures de durée de vie du jeton sont conservées. Abstrait pour les tests. */
interface TokenLifetimeLog {
    fun load(): TokenLifetimes

    /** Un appel a trouvé le jeton expiré [idleMs] après sa dernière réussite, [ageMs] après la connexion. */
    fun recordExpiry(idleMs: Long, ageMs: Long)

    /** Un jeton gardé a encore fonctionné après [idleMs] d'inactivité. */
    fun recordReplay(idleMs: Long)
}

class InMemoryTokenLog(initial: TokenLifetimes = TokenLifetimes()) : TokenLifetimeLog {
    private var known = initial
    override fun load() = known

    override fun recordExpiry(idleMs: Long, ageMs: Long) {
        // Une expiration quelques secondes après un appel réussi ne dit rien sur l'inactivité
        // tolérée (le jeton a pu atteindre sa limite absolue ou être remplacé) : seule l'ancienneté compte.
        val idle = idleMs.takeIf { it >= TokenReplayPolicy.MIN_USEFUL_MS }
        known = known.copy(
            expiredAfterIdleMs = idle?.let { minOf(known.expiredAfterIdleMs ?: it, it) } ?: known.expiredAfterIdleMs,
            expiredAfterAgeMs = minOf(known.expiredAfterAgeMs ?: ageMs, ageMs),
        )
    }

    override fun recordReplay(idleMs: Long) {
        known = known.copy(replayedAfterIdleMs = maxOf(known.replayedAfterIdleMs ?: idleMs, idleMs))
    }
}

/** Mesures conservées sur le téléphone (aucune donnée personnelle : seulement des durées). */
class PrefsTokenLog(context: Context) : TokenLifetimeLog {
    private val preferences = context.getSharedPreferences("ecole2recte_token_lifetime", Context.MODE_PRIVATE)
    private val memory = InMemoryTokenLog(
        TokenLifetimes(
            expiredAfterIdleMs = preferences.getLong("expired_idle", -1).takeIf { it >= 0 },
            expiredAfterAgeMs = preferences.getLong("expired_age", -1).takeIf { it >= 0 },
            replayedAfterIdleMs = preferences.getLong("replayed_idle", -1).takeIf { it >= 0 },
        ),
    )

    override fun load() = memory.load()

    @Synchronized
    override fun recordExpiry(idleMs: Long, ageMs: Long) {
        memory.recordExpiry(idleMs, ageMs)
        save()
    }

    @Synchronized
    override fun recordReplay(idleMs: Long) {
        memory.recordReplay(idleMs)
        save()
    }

    private fun save() {
        val known = memory.load()
        preferences.edit()
            .putLong("expired_idle", known.expiredAfterIdleMs ?: -1)
            .putLong("expired_age", known.expiredAfterAgeMs ?: -1)
            .putLong("replayed_idle", known.replayedAfterIdleMs ?: -1)
            .apply()
    }
}

// --- Format de stockage (la chaîne entière est chiffrée par SecureCredentialStore) ------------

internal fun SavedSession.toJson(): JSONObject = JSONObject()
    .put("token", token)
    .put("gtk", gtk)
    .put("userAgent", userAgent)
    .put("issuedAt", issuedAt)
    .put("lastOkAt", lastOkAt)
    .put("cookies", JSONObject(cookies))
    .put(
        "accounts",
        JSONArray(
            accounts.map { a ->
                JSONObject().put("id", a.id).put("type", a.type).put("firstName", a.firstName).put("lastName", a.lastName)
                    .put(
                        "students",
                        JSONArray(
                            a.students.map { s ->
                                JSONObject().put("id", s.id).put("firstName", s.firstName)
                                    .put("lastName", s.lastName).put("className", s.className)
                            },
                        ),
                    )
            },
        ),
    )

internal fun JSONObject.toSavedSession(): SavedSession? {
    val token = optString("token").takeIf { it.isNotBlank() } ?: return null
    val cookiesJson = optJSONObject("cookies") ?: JSONObject()
    val accountsJson = optJSONArray("accounts") ?: JSONArray()
    val accounts = (0 until accountsJson.length()).mapNotNull { accountsJson.optJSONObject(it) }.map { a ->
        val studentsJson = a.optJSONArray("students") ?: JSONArray()
        Account(
            id = a.optString("id"),
            type = a.optString("type"),
            firstName = a.optString("firstName"),
            lastName = a.optString("lastName"),
            students = (0 until studentsJson.length()).mapNotNull { studentsJson.optJSONObject(it) }.map { s ->
                Student(s.optString("id"), s.optString("firstName"), s.optString("lastName"), s.optString("className"))
            },
        )
    }
    if (accounts.isEmpty()) return null
    return SavedSession(
        token = token,
        cookies = cookiesJson.keys().asSequence().associateWith { cookiesJson.optString(it) },
        gtk = optString("gtk"),
        userAgent = optString("userAgent"),
        issuedAt = optLong("issuedAt"),
        lastOkAt = optLong("lastOkAt"),
        accounts = accounts,
    )
}
