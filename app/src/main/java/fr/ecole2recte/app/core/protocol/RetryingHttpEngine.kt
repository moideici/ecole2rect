package fr.ecole2recte.app.core.protocol

/**
 * Réessai des pannes réseau passagères.
 *
 * Un train qui passe sous un tunnel, un Wi-Fi qui bascule vers la 4G : la requête échoue une
 * fois puis passerait très bien à la seconde suivante. Sans ce décorateur, l'utilisateur voit
 * « hors ligne » pour une coupure d'une demi-seconde, et une synchronisation de fond entière
 * est perdue.
 *
 * Trois règles :
 *
 *  1. **seules les pannes de transport sont réessayées** — un refus applicatif (identifiants
 *     invalides, jeton expiré) est une réponse, pas une panne : le rejouer serait au mieux
 *     inutile, au pire une tentative de forçage ;
 *  2. **une temporisation croissante**, pour ne pas transformer une panne du serveur en
 *     rafale de requêtes ;
 *  3. **un nombre d'essais borné**, connu et petit.
 *
 * L'attente est injectée, ce qui permet de tester la logique sans dormir.
 */
class RetryingHttpEngine(
    private val delegate: HttpEngine,
    private val attempts: Int = 3,
    private val delayMillis: (attempt: Int) -> Long = { attempt -> 300L * attempt * attempt },
    private val sleep: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : HttpEngine {

    override fun execute(request: HttpRequest): HttpResponse =
        retrying { delegate.execute(request) }

    override fun download(request: HttpRequest): HttpBinaryResponse =
        retrying { delegate.download(request) }

    private fun <T> retrying(block: () -> T): T {
        var failure: NetworkFailure? = null
        for (attempt in 1..attempts.coerceAtLeast(1)) {
            try {
                return block()
            } catch (error: NetworkFailure) {
                failure = error
                if (attempt < attempts) sleep(delayMillis(attempt))
            }
        }
        throw failure ?: NetworkFailure("Réseau indisponible.")
    }
}
