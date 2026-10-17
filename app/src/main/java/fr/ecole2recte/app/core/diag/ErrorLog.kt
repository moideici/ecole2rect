package fr.ecole2recte.app.core.diag

import fr.ecole2recte.app.core.protocol.Encoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Une erreur telle qu'elle sera montrée : datée, située, et déjà expurgée. */
data class LoggedError(
    val at: Long,
    val section: String,
    val message: String,
    val detail: String = "",
)

/**
 * Journal des erreurs de la session.
 *
 * Quand l'API privée change, l'application se met à échouer sans que personne ne sache où :
 * ce journal donne la réponse en trois lignes, copiables dans un ticket. Il vit en mémoire —
 * un journal d'erreurs qui survit au redémarrage devient vite un fichier qu'on oublie de
 * purger — et tout ce qui y entre passe par la même expurgation que les diagnostics réseau.
 */
class ErrorLog(private val capacity: Int = 50) {

    private val _entries = MutableStateFlow<List<LoggedError>>(emptyList())
    val entries: StateFlow<List<LoggedError>> = _entries.asStateFlow()

    fun record(section: String, message: String, detail: String? = null, at: Long = System.currentTimeMillis()) {
        val entry = LoggedError(
            at = at,
            section = section,
            message = Encoding.redactSecrets(message),
            detail = detail?.let { Encoding.redactSecrets(it) }.orEmpty().take(2000),
        )
        _entries.value = (_entries.value + entry).takeLast(capacity)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
