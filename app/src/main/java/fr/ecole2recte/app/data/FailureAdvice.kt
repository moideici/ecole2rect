package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.Freshness
import fr.ecole2recte.app.domain.FreshnessLabel
import fr.ecole2recte.app.domain.StaleCause
import java.io.IOException
import java.time.ZoneId

/** Ce que l'utilisateur peut faire tout de suite devant une erreur. */
enum class AdviceAction(val label: String) {
    /** Relancer la lecture (un seul essai par toucher, jamais de boucle). */
    Retry("Réessayer"),

    /** Rouvrir la session ou saisir un nouveau mot de passe. */
    Reconnect("Reconnecter"),

    /** Aller voir ce que l'application reçoit, sans donnée personnelle. */
    OpenDiagnostic("Ouvrir Plus > Diagnostic"),

    /** Rien à faire de plus : insister aggraverait le problème. */
    None(""),
}

/** Un message d'erreur précis : un titre court, une explication, une action utile. */
data class Advice(val title: String, val detail: String, val action: AdviceAction)

/**
 * Traduit les échecs ([FailureKind], [StaleCause], exceptions) en messages clairs pour l'élève ou
 * le parent, avec la bonne action. Un seul endroit : l'écran de chargement, le bandeau hors ligne
 * et le journal de synchronisation disent la même chose.
 */
object FailureAdvice {
    val network = Advice(
        "Pas de réseau",
        "Vérifie ta connexion.",
        AdviceAction.Retry,
    )
    val service = Advice(
        "ÉcoleDirecte indisponible",
        "Réessaie dans quelques minutes.",
        AdviceAction.Retry,
    )
    val invalidCredentials = Advice(
        "Mot de passe refusé",
        "Il a peut-être changé. L'application ne réessaiera pas toute seule.",
        AdviceAction.Reconnect,
    )
    val blocked = Advice(
        "Compte verrouillé",
        "Souvent trop de tentatives : attends ou débloque-le sur le site.",
        AdviceAction.None,
    )
    val securityRequired = Advice(
        "Vérification de sécurité requise",
        "Reconnecte-toi et réponds à la question de sécurité.",
        AdviceAction.Reconnect,
    )
    val staleVersion = Advice(
        "Version d'API refusée (517)",
        "Cherche une mise à jour ou change la version dans Plus > Diagnostic.",
        AdviceAction.OpenDiagnostic,
    )
    val expired = Advice(
        "Session expirée",
        "Reconnecte-toi pour relire les données.",
        AdviceAction.Reconnect,
    )
    val unusable = Advice(
        "Réponse inexploitable",
        "Voir Plus > Diagnostic.",
        AdviceAction.OpenDiagnostic,
    )
    val other = Advice(
        "Lecture impossible",
        "Voir Plus > Diagnostic.",
        AdviceAction.OpenDiagnostic,
    )
    val generic = Advice("Chargement impossible", "Réessaie dans un instant.", AdviceAction.Retry)

    fun forKind(kind: FailureKind): Advice = when (kind) {
        FailureKind.Network -> network
        FailureKind.Service -> service
        FailureKind.InvalidCredentials -> invalidCredentials
        FailureKind.Blocked -> blocked
        FailureKind.SecurityRefused -> securityRequired
        FailureKind.StaleVersion -> staleVersion
        FailureKind.Other -> other
    }

    fun forStale(cause: StaleCause?): Advice = when (cause) {
        null, StaleCause.Network -> network
        StaleCause.Service -> service
        StaleCause.Expired -> expired
        StaleCause.Blocked -> blocked
        StaleCause.StaleVersion -> staleVersion
        StaleCause.Unusable -> unusable
        StaleCause.Other -> other
    }

    /** Le message à montrer pour une exception levée pendant un chargement. */
    fun forThrowable(error: Throwable): Advice = when {
        error is SessionExpiredException -> expired
        error is UnusableResponseException -> unusable
        error is EcoleDirecteException && error.kind == FailureKind.Service && error.cause is SessionExpiredException -> expired
        // Un message précis écrit à l'avance (« comptes professeur non pris en charge »…) vaut mieux qu'un texte générique.
        error is EcoleDirecteException && error.kind == FailureKind.Other && !error.message.isNullOrBlank() ->
            Advice("Lecture impossible", error.message.orEmpty(), AdviceAction.Retry)
        error is EcoleDirecteException -> forKind(error.kind)
        error is IOException -> network
        else -> generic
    }

    /**
     * L'erreur de l'écran de connexion : le titre dit le cas (mot de passe refusé, compte verrouillé,
     * service indisponible…), le détail garde le message précis reçu quand il en dit plus que le texte type.
     */
    fun forLogin(kind: FailureKind, message: String): Advice {
        val base = forKind(kind)
        val detail = when (kind) {
            FailureKind.InvalidCredentials -> "${message.trim()} L'application ne réessaiera pas toute seule."
            FailureKind.SecurityRefused, FailureKind.StaleVersion, FailureKind.Other -> message.ifBlank { base.detail }
            else -> base.detail
        }
        return base.copy(detail = detail)
    }

    /** Le texte du bandeau d'ancienneté pour des données lues dans l'enregistrement. */
    fun banner(freshness: Freshness, now: Long, zone: ZoneId = ZoneId.systemDefault()): Advice {
        val advice = forStale(freshness.cause)
        val title = if (freshness.cause == null || freshness.cause == StaleCause.Network) "Hors ligne" else advice.title
        return Advice(title, FreshnessLabel.describe(freshness.lastSyncMillis, now, zone), advice.action)
    }
}
