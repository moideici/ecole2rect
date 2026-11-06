package fr.ecole2recte.app.sync

import android.content.Context
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.domain.FrenchFormat
import fr.ecole2recte.app.domain.FreshnessLabel
import java.time.Instant
import java.time.ZoneId

/** Ce qui s'est passé lors d'une synchronisation, en un mot précis. Aucune donnée personnelle ici. */
enum class SyncResultCode(val label: String, val group: Group) {
    Done("Réussie", Group.Done),
    NoNetwork("Sans réseau", Group.NoNetwork),
    ServiceDown("ÉcoleDirecte indisponible", Group.Error),
    PasswordRefused("Mot de passe refusé", Group.Stopped),
    Blocked("Compte verrouillé", Group.Stopped),
    ApiVersionRefused("Version d'API refusée (517)", Group.Stopped),
    SecurityRefused("Vérification de sécurité refusée", Group.Stopped),
    NeedsInteraction("Vérification de sécurité requise", Group.Stopped),
    TooManyFailures("Arrêtée après plusieurs échecs", Group.Stopped),
    Error("Erreur inattendue", Group.Error),
    ;

    /** Les quatre familles de résultat montrées en tête de ligne. */
    enum class Group(val label: String) {
        Done("réussie"),
        Stopped("arrêtée"),
        NoNetwork("sans réseau"),
        Error("erreur"),
    }

    companion object {
        fun fromFailure(kind: FailureKind): SyncResultCode = when (kind) {
            FailureKind.Network -> NoNetwork
            FailureKind.Service -> ServiceDown
            FailureKind.InvalidCredentials -> PasswordRefused
            FailureKind.Blocked -> Blocked
            FailureKind.SecurityRefused -> SecurityRefused
            FailureKind.StaleVersion -> ApiVersionRefused
            FailureKind.Other -> Error
        }

        fun parse(name: String): SyncResultCode = entries.firstOrNull { it.name == name } ?: Error
    }
}

/**
 * Une ligne du journal : quand, combien de temps, quel résultat, combien de nouveautés.
 * Jamais un nom, une note, un message ni un identifiant.
 */
data class SyncJournalEntry(
    val startedAt: Long,
    val durationMs: Long,
    val result: SyncResultCode,
    val newItems: Int,
) {
    /** « 5 nov. à 18 h 02 · 4 s · réussie · 2 nouveautés » ou « … · arrêtée : mot de passe refusé · aucune nouveauté ». */
    fun describe(zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(startedAt).atZone(zone)
        val date = "${FrenchFormat.dayMonthShort(moment.toLocalDate())} à ${FreshnessLabel.clock(moment.hour, moment.minute)}"
        val news = when (newItems) {
            0 -> "aucune nouveauté"
            1 -> "1 nouveauté"
            else -> "$newItems nouveautés"
        }
        val outcome = when (result) {
            SyncResultCode.Done -> "réussie"
            SyncResultCode.NoNetwork -> "sans réseau"
            else -> "${result.group.label} : ${result.label.replaceFirstChar { it.lowercase() }}"
        }
        return "$date · ${duration(durationMs)} · $outcome · $news"
    }

    companion object {
        fun duration(ms: Long): String = when {
            ms < 1_000 -> "moins d'1 s"
            ms < 60_000 -> "${ms / 1_000} s"
            else -> "${ms / 60_000} min ${FrenchFormat.twoDigits(((ms % 60_000) / 1_000).toInt())} s"
        }
    }
}

/** Où le journal est écrit (un petit texte). Abstrait pour les tests. */
interface JournalStorage {
    fun read(): String
    fun write(text: String)
}

class PrefsJournalStorage(context: Context) : JournalStorage {
    private val preferences = context.getSharedPreferences("ecole2recte_sync_journal", Context.MODE_PRIVATE)
    override fun read(): String = preferences.getString("lines", "").orEmpty()
    override fun write(text: String) {
        preferences.edit().putString("lines", text).apply()
    }
}

/**
 * Le journal des dernières synchronisations ([maxEntries], trente par défaut), gardé sur le téléphone.
 * Une ligne par passage au format `début;durée;résultat;nouveautés` : des nombres et un code, rien d'autre.
 */
class SyncJournal(private val storage: JournalStorage, private val maxEntries: Int = DEFAULT_MAX) {
    private val lock = Any()

    /** Les passages, du plus récent au plus ancien. */
    fun entries(): List<SyncJournalEntry> = synchronized(lock) { load().asReversed() }

    fun record(entry: SyncJournalEntry) = synchronized(lock) {
        val kept = (load() + entry).takeLast(maxEntries)
        storage.write(kept.joinToString("\n") { "${it.startedAt};${it.durationMs};${it.result.name};${it.newItems}" })
    }

    fun clear() = synchronized(lock) { storage.write("") }

    /** Du plus ancien au plus récent ; les lignes illisibles sont ignorées. */
    private fun load(): List<SyncJournalEntry> = storage.read().lineSequence().mapNotNull(::parseLine).toList().takeLast(maxEntries)

    private fun parseLine(line: String): SyncJournalEntry? {
        val parts = line.split(';')
        if (parts.size != 4) return null
        return SyncJournalEntry(
            startedAt = parts[0].toLongOrNull() ?: return null,
            durationMs = parts[1].toLongOrNull()?.coerceAtLeast(0) ?: return null,
            result = SyncResultCode.parse(parts[2]),
            newItems = parts[3].toIntOrNull()?.coerceAtLeast(0) ?: return null,
        )
    }

    companion object {
        const val DEFAULT_MAX = 30
    }
}
