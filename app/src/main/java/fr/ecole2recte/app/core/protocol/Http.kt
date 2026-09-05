package fr.ecole2recte.app.core.protocol

/**
 * Frontière réseau du client.
 *
 * Le protocole ÉcoleDirecte est décrit intégralement dans [EcoleDirecteApi], qui ne connaît
 * que cette interface. Les tests injectent un moteur qui rejoue des réponses enregistrées :
 * la CI n'a donc jamais besoin d'un vrai compte, ni même d'un accès réseau.
 */
interface HttpEngine {
    /** Exécute la requête ou lève [NetworkFailure] si le réseau est indisponible. */
    fun execute(request: HttpRequest): HttpResponse

    /**
     * Télécharge un contenu binaire. Une pièce jointe n'est pas du texte : la décoder en
     * chaîne la corromprait silencieusement, ce qui donnerait un PDF illisible plutôt qu'une
     * erreur franche.
     */
    fun download(request: HttpRequest): HttpBinaryResponse =
        throw NetworkFailure("Téléchargement non pris en charge par ce transport.")
}

/** Réponse binaire : les octets, et le nom de fichier proposé par le serveur s'il en donne un. */
class HttpBinaryResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val bytes: ByteArray,
) {
    /** Nom extrait de `Content-Disposition`, quand le serveur en propose un. */
    val suggestedName: String?
        get() = headers.entries
            .firstOrNull { it.key.equals("Content-Disposition", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.substringAfter("filename=", "")
            ?.trim('"', ' ', ';')
            ?.takeIf { it.isNotBlank() }
}

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: String,
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

    fun headers(name: String): List<String> = headers.entries
        .filter { it.key.equals(name, ignoreCase = true) }
        .flatMap { it.value }
}

/** Panne de transport : pas de réponse du tout, à distinguer d'un refus applicatif. */
class NetworkFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
