package fr.ecole2recte.app.core.protocol

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.CloudFile
import fr.ecole2recte.app.core.model.Document
import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.NewsItem
import fr.ecole2recte.app.core.model.Textbook
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolLifeEvent
import fr.ecole2recte.app.core.model.SessionContent
import fr.ecole2recte.app.core.parse.Parsers
import fr.ecole2recte.app.core.parse.string
import fr.ecole2recte.app.core.parse.strings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Devoirs et contenu de séance d'un même jour, ramenés par un seul appel. */
data class DayContent(
    val homework: List<HomeworkItem>,
    val sessions: List<SessionContent>,
)

/** Une pièce jointe téléchargée, encore en mémoire. */
class DownloadedFile(val name: String, val bytes: ByteArray)

/** Réponse mémorisée d'une question de sécurité, rejouable aux reconnexions. */
data class TrustedAnswer(val cn: String, val cv: String) {
    fun toJson(): JSONObject = JSONObject().put("cn", cn).put("cv", cv)
}

/** Nature d'un échec, pour que l'appelant sache s'il doit se reconnecter ou abandonner. */
enum class ApiErrorKind {
    /** Pas de réseau, ou serveur injoignable : les données en cache restent valables. */
    NETWORK,

    /** Jeton expiré ou refusé : une reconnexion silencieuse peut sauver la situation. */
    SESSION_EXPIRED,
    INVALID_CREDENTIALS,
    ACCOUNT_BLOCKED,

    /** Le compte n'a pas le droit d'accéder à cette ressource (profil famille, module fermé). */
    ACCESS_DENIED,

    /** Réponse inattendue : le protocole a probablement changé. */
    PROTOCOL,
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(
        val kind: ApiErrorKind,
        val message: String,
        val diagnostic: String? = null,
    ) : ApiResult<Nothing>
}

sealed interface LoginOutcome {
    data class Success(val accounts: List<Account>) : LoginOutcome
    data class SecurityQuestion(val question: String, val choices: List<String>) : LoginOutcome
    data class Failure(
        val kind: ApiErrorKind,
        val message: String,
        val diagnostic: String? = null,
    ) : LoginOutcome
}

/** Codes métier de l'API : un HTTP 200 ne veut pas dire que l'opération a réussi. */
object ApiCode {
    const val OK = 200
    const val TWO_FACTOR = 250
    const val INVALID_CREDENTIALS = 505
    const val BLOCKED = 516
    const val TOKEN_EXPIRED = 520
    const val TOKEN_INVALID = 525
    const val BLOCKED_ALT = 535
    const val ACCESS_DENIED = 403
}

/**
 * Client du protocole privé ÉcoleDirecte.
 *
 * Tout ce qui est spécifique au protocole vit ici : chemins, verbes, en-têtes, jetons,
 * cookies et codes métier. Le reste de l'application ne manipule que des modèles et des
 * [ApiResult] ; le jour où l'API change, il n'y a que ce fichier et le parsing à reprendre.
 *
 * La classe est volontairement bloquante : l'appelant décide du fil d'exécution.
 */
class EcoleDirecteApi(
    private val engine: HttpEngine,
    private val apiBase: String = DEFAULT_API_BASE,
    private val apiVersion: String = DEFAULT_API_VERSION,
) {

    private val cookies = LinkedHashMap<String, String>()
    private var gtk: String = ""

    var token: String? = null
        private set
    var twoFactorToken: String? = null
        private set

    /** Réponse validée par le serveur, à conserver pour les reconnexions silencieuses. */
    var approvedAnswer: TrustedAnswer? = null
        private set

    val hasSession: Boolean get() = token != null

    /** Oublie la session sans toucher aux identifiants enregistrés. */
    fun clearSession() {
        cookies.clear()
        gtk = ""
        token = null
        twoFactorToken = null
    }

    // ── Connexion ────────────────────────────────────────────────────────────

    fun login(identifier: String, password: String, trustedAnswer: TrustedAnswer? = null): LoginOutcome {
        val payload = JSONObject()
            .put("identifiant", identifier)
            .put("motdepasse", password)
            .put("isReLogin", false)
            .put("uuid", "")
            .put("fa", trustedAnswer?.let { JSONArray().put(it.toJson()) } ?: JSONArray())

        val response = try {
            refreshGtk()
            request(
                path = "/login.awp",
                payload = payload,
                headers = mapOf("X-GTK" to gtk),
                keyOrder = LOGIN_KEY_ORDER,
            )
        } catch (failure: NetworkFailure) {
            return LoginOutcome.Failure(
                ApiErrorKind.NETWORK,
                "Connexion réseau impossible. Vérifie ta connexion et réessaie.",
            )
        }

        return when (response.code) {
            ApiCode.OK -> {
                val accounts = Parsers.accounts(response.body)
                if (accounts.isEmpty()) {
                    LoginOutcome.Failure(
                        ApiErrorKind.PROTOCOL,
                        "Connexion acceptée mais aucun compte lisible.",
                        response.diagnostic(),
                    )
                } else {
                    if (trustedAnswer != null) approvedAnswer = trustedAnswer
                    LoginOutcome.Success(accounts)
                }
            }

            ApiCode.TWO_FACTOR -> securityQuestion(response)

            ApiCode.INVALID_CREDENTIALS -> LoginOutcome.Failure(
                ApiErrorKind.INVALID_CREDENTIALS,
                "Identifiant ou mot de passe invalide.",
                response.diagnostic(),
            )

            ApiCode.BLOCKED, ApiCode.BLOCKED_ALT -> LoginOutcome.Failure(
                ApiErrorKind.ACCOUNT_BLOCKED,
                "Compte bloqué par ÉcoleDirecte. Passe par le site officiel pour le débloquer.",
                response.diagnostic(),
            )

            else -> LoginOutcome.Failure(
                ApiErrorKind.PROTOCOL,
                response.message.ifBlank { "Connexion impossible." },
                response.diagnostic(),
            )
        }
    }

    /** Demande la question de sécurité après un code 250. */
    private fun securityQuestion(loginResponse: Envelope): LoginOutcome {
        val response = try {
            request(
                path = "/connexion/doubleauth.awp",
                verb = "get",
                payload = JSONObject(),
                headers = authHeaders(),
            )
        } catch (failure: NetworkFailure) {
            return LoginOutcome.Failure(ApiErrorKind.NETWORK, "Réseau indisponible pendant la vérification.")
        }
        val data = response.body.optJSONObject("data")
            ?: return LoginOutcome.Failure(
                ApiErrorKind.PROTOCOL,
                response.message.ifBlank { "Question de sécurité indisponible." },
                loginResponse.diagnostic(),
            )
        val question = Encoding.base64Decode(data.string("question"))
            ?: data.string("question").ifBlank { "Réponds à la question demandée par ÉcoleDirecte." }
        val choices = data.optJSONArray("propositions").strings()
            .mapNotNull { Encoding.base64Decode(it) ?: it.takeIf { raw -> raw.isNotBlank() } }
        return LoginOutcome.SecurityQuestion(question, choices)
    }

    /**
     * Envoie la réponse choisie, puis rejoue la connexion avec le couple `cn`/`cv` validé.
     *
     * La réponse n'est jamais devinée : elle vient d'un choix de l'utilisateur, ou d'une
     * proposition du serveur qui correspond exactement à ce qu'il a saisi.
     */
    fun answerSecurityQuestion(choice: String, identifier: String, password: String): LoginOutcome {
        if (token == null || twoFactorToken == null) {
            return LoginOutcome.Failure(ApiErrorKind.SESSION_EXPIRED, "Session de vérification expirée. Recommence la connexion.")
        }
        val response = try {
            request(
                path = "/connexion/doubleauth.awp",
                verb = "post",
                payload = JSONObject().put("choix", Encoding.base64Encode(choice)),
                headers = authHeaders(),
            )
        } catch (failure: NetworkFailure) {
            return LoginOutcome.Failure(ApiErrorKind.NETWORK, "Réseau indisponible pendant la vérification.")
        }
        if (response.code != ApiCode.OK) {
            return LoginOutcome.Failure(
                ApiErrorKind.PROTOCOL,
                response.message.ifBlank { "Réponse de sécurité refusée." },
                response.diagnostic(),
            )
        }
        val data = response.body.optJSONObject("data")
            ?: return LoginOutcome.Failure(ApiErrorKind.PROTOCOL, "Réponse de sécurité incomplète.", response.diagnostic())
        val answer = TrustedAnswer(cn = data.string("cn"), cv = data.string("cv"))
        if (answer.cn.isBlank() || answer.cv.isBlank()) {
            return LoginOutcome.Failure(ApiErrorKind.PROTOCOL, "Réponse de sécurité incomplète.", response.diagnostic())
        }
        approvedAnswer = answer
        return login(identifier, password, answer)
    }

    // ── Données scolaires ────────────────────────────────────────────────────

    fun timetable(studentId: String, from: LocalDate, to: LocalDate): ApiResult<List<Lesson>> = call(
        path = "/E/$studentId/emploidutemps.awp",
        payload = JSONObject()
            .put("dateDebut", Dates.isoDate(from))
            .put("dateFin", Dates.isoDate(to))
            .put("avecTrous", false),
    ) { Parsers.lessons(it) }

    fun homework(studentId: String): ApiResult<List<HomeworkItem>> = call(
        path = "/Eleves/$studentId/cahierdetexte.awp",
        payload = JSONObject(),
    ) { Parsers.homeworkSummaries(it) }

    /**
     * Le détail d'un jour du cahier de textes : les devoirs à rendre *et* ce qui a été fait
     * en cours. Un seul appel rend les deux ; les séparer coûterait une requête de plus pour
     * la moitié de l'information.
     */
    fun dayContent(studentId: String, date: LocalDate): ApiResult<DayContent> = call(
        path = "/Eleves/$studentId/cahierdetexte/${Dates.isoDate(date)}.awp",
        payload = JSONObject(),
    ) { body ->
        DayContent(
            homework = Parsers.homeworkForDate(body, date),
            sessions = Parsers.sessionContents(body, date),
        )
    }

    fun schoolLife(studentId: String): ApiResult<List<SchoolLifeEvent>> = call(
        path = "/eleves/$studentId/viescolaire.awp",
        payload = JSONObject(),
    ) { Parsers.schoolLife(it) }

    fun documents(studentId: String): ApiResult<List<Document>> = call(
        path = "/elevesDocuments.awp",
        payload = JSONObject().put("anneeScolaire", "").put("idEleve", studentId),
    ) { Parsers.documents(it) }

    fun grades(studentId: String): ApiResult<Parsers.GradesPayload> = call(
        path = "/eleves/$studentId/notes.awp",
        payload = JSONObject().put("anneeScolaire", ""),
    ) { Parsers.grades(it) }

    /**
     * Une page de messages reçus. La messagerie d'une année scolaire dépasse largement une
     * page : sans le paramètre, l'application prétendrait que l'histoire s'arrête au
     * centième message.
     */
    fun messages(
        account: Account,
        page: Int = 0,
        folder: MessageFolder = MessageFolder.RECEIVED,
        folderId: String = "0",
    ): ApiResult<List<MessageItem>> = call(
        path = messagesPath(account),
        payload = JSONObject().put("anneeMessages", ""),
        params = messagesParams(page, folder, folderId),
    ) { Parsers.messages(it, folder) }

    /** Une page de messages et les classeurs qui arrivent avec elle. */
    data class MessagePage(
        val messages: List<MessageItem>,
        val folders: List<Pair<String, String>>,
    )

    /**
     * La même page, lue en entier.
     *
     * Les classeurs sont dans le **même** conteneur que les messages : les demander
     * séparément était un second aller-retour réseau complet pour des données déjà reçues.
     * Le commentaire de [messageFolders] l'affirmait déjà ; le code ne le faisait pas.
     */
    fun messagePage(
        account: Account,
        page: Int = 0,
        folder: MessageFolder = MessageFolder.RECEIVED,
        folderId: String = "0",
    ): ApiResult<MessagePage> = call(
        path = messagesPath(account),
        payload = JSONObject().put("anneeMessages", ""),
        params = messagesParams(page, folder, folderId),
    ) { MessagePage(Parsers.messages(it, folder), Parsers.messageFolders(it)) }

    private fun messagesPath(account: Account): String =
        "/${mailboxSegment(account)}/${account.mailboxId}/messages.awp"

    private fun messagesParams(page: Int, folder: MessageFolder, folderId: String) = linkedMapOf(
        "force" to "false",
        "typeRecuperation" to folder.apiValue,
        "idClasseur" to folderId,
        "orderBy" to "date",
        "order" to "desc",
        "page" to page.toString(),
        "itemsPerPage" to MESSAGES_PER_PAGE.toString(),
        "onlyRead" to "",
        "query" to "",
    )

    fun messageBody(
        account: Account,
        messageId: String,
        folder: MessageFolder = MessageFolder.RECEIVED,
    ): ApiResult<MessageItem?> = call(
        path = "/${mailboxSegment(account)}/${account.mailboxId}/messages/$messageId.awp",
        payload = JSONObject(),
        params = linkedMapOf("mode" to "destinataire"),
    ) { Parsers.messageBody(it, folder) }

    /**
     * Les classeurs de la messagerie, tels que l'utilisateur les a créés sur le site.
     * Ils arrivent avec la première page de messages : pas d'appel supplémentaire.
     */
    fun messageFolders(account: Account): ApiResult<List<Pair<String, String>>> = call(
        path = "/${mailboxSegment(account)}/${account.mailboxId}/messages.awp",
        payload = JSONObject().put("anneeMessages", ""),
        params = linkedMapOf(
            "force" to "false",
            "typeRecuperation" to MessageFolder.RECEIVED.apiValue,
            "idClasseur" to "0",
            "orderBy" to "date",
            "order" to "desc",
            "page" to "0",
            "itemsPerPage" to "1",
            "onlyRead" to "",
            "query" to "",
        ),
    ) { Parsers.messageFolders(it) }

    // ── Modules de l'établissement ───────────────────────────────────────────

    /**
     * Les actualités de l'établissement.
     *
     * Ce module n'est pas ouvert partout : un refus n'est pas une panne, seulement une
     * section vide — c'est pourquoi chaque section porte son erreur sans annuler les autres.
     */
    fun news(studentId: String): ApiResult<List<NewsItem>> = call(
        path = "/E/$studentId/viedeletablissement.awp",
        payload = JSONObject(),
    ) { Parsers.news(it) }

    fun textbooks(studentId: String): ApiResult<List<Textbook>> = call(
        path = "/E/$studentId/manuelsnumeriques.awp",
        payload = JSONObject(),
    ) { Parsers.textbooks(it) }

    /** L'espace de stockage, celui que le site appelle « cloud ». */
    fun cloud(account: Account): ApiResult<List<CloudFile>> = call(
        path = "/${mailboxSegment(account)}/${account.mailboxId}/cloud.awp",
        payload = JSONObject(),
    ) { Parsers.cloudFiles(it) }

    /**
     * Coche ou décoche un devoir côté serveur. L'écriture reste facultative : l'état local
     * fait foi pour l'affichage, ce qui laisse l'application utilisable hors ligne.
     */
    fun setHomeworkDone(studentId: String, homeworkId: String, done: Boolean): ApiResult<Unit> = call(
        path = "/Eleves/$studentId/cahierdetexte.awp",
        verb = "put",
        payload = JSONObject()
            .put("idDevoirsEffectues", if (done) JSONArray().put(homeworkId) else JSONArray())
            .put("idDevoirsNonEffectues", if (done) JSONArray() else JSONArray().put(homeworkId)),
    ) { }

    /**
     * Télécharge une pièce jointe.
     *
     * L'endpoint renvoie soit le fichier, soit — en cas de refus — un petit JSON. On
     * distingue les deux avant d'écrire quoi que ce soit sur le disque : rien n'est plus
     * déroutant qu'un PDF de trente octets qui contient un message d'erreur.
     */
    fun downloadAttachment(attachment: Attachment): ApiResult<DownloadedFile> {
        if (token == null) return ApiResult.Failure(ApiErrorKind.SESSION_EXPIRED, "Session absente.")
        val response = try {
            engine.download(
                HttpRequest(
                    url = url(
                        "/telechargement.awp",
                        linkedMapOf(
                            "verbe" to "get",
                            "fichierId" to attachment.id,
                            "leTypeDeFichier" to attachment.apiDownloadType,
                        ),
                    ),
                    method = "POST",
                    headers = buildMap {
                        putAll(browserHeaders())
                        put("Content-Type", "application/x-www-form-urlencoded")
                        cookieHeader()?.let { put("Cookie", it) }
                        putAll(authHeaders())
                    },
                    body = "data=" + Encoding.formEncode(
                        Encoding.pythonJson(JSONObject().put("forceDownload", 0)),
                    ),
                ),
            )
        } catch (failure: NetworkFailure) {
            return ApiResult.Failure(ApiErrorKind.NETWORK, failure.message ?: "Réseau indisponible.")
        }
        if (response.status !in 200..299) {
            return ApiResult.Failure(
                ApiErrorKind.PROTOCOL,
                "Le serveur a refusé le téléchargement (HTTP ${response.status}).",
            )
        }
        val bytes = response.bytes
        if (bytes.isEmpty()) {
            return ApiResult.Failure(ApiErrorKind.PROTOCOL, "Fichier vide.")
        }
        jsonError(bytes)?.let { message -> return ApiResult.Failure(ApiErrorKind.ACCESS_DENIED, message) }
        return ApiResult.Ok(
            DownloadedFile(
                name = response.suggestedName ?: attachment.name,
                bytes = bytes,
            ),
        )
    }

    /** Un corps qui commence par une accolade et porte un code n'est pas un fichier. */
    private fun jsonError(bytes: ByteArray): String? {
        if (bytes.size > 4096 || bytes.firstOrNull() != '{'.code.toByte()) return null
        val body = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return null
        if (body.optInt("code", ApiCode.OK) == ApiCode.OK) return null
        return body.optString("message").ifBlank { "Téléchargement refusé." }
    }

    // ── Plomberie ────────────────────────────────────────────────────────────

    /**
     * La messagerie appartient au titulaire de la connexion, pas au profil consulté : un
     * parent lit celle de la famille, même quand l'application affiche l'un de ses enfants.
     */
    private fun mailboxSegment(account: Account): String =
        if (account.viewedByFamily || !account.isStudent) "familles" else "eleves"

    private fun <T> call(
        path: String,
        payload: JSONObject,
        verb: String? = "get",
        params: Map<String, String> = emptyMap(),
        transform: (JSONObject) -> T,
    ): ApiResult<T> {
        if (token == null) {
            return ApiResult.Failure(ApiErrorKind.SESSION_EXPIRED, "Session absente.")
        }
        val response = try {
            request(path = path, verb = verb, payload = payload, headers = authHeaders(), params = params)
        } catch (failure: NetworkFailure) {
            return ApiResult.Failure(ApiErrorKind.NETWORK, failure.message ?: "Réseau indisponible.")
        }
        return when (response.code) {
            ApiCode.OK -> runCatching { ApiResult.Ok(transform(response.body)) }
                .getOrElse {
                    ApiResult.Failure(
                        ApiErrorKind.PROTOCOL,
                        "Réponse illisible : le format a peut-être changé.",
                        response.diagnostic(),
                    )
                }

            ApiCode.TOKEN_EXPIRED, ApiCode.TOKEN_INVALID -> ApiResult.Failure(
                ApiErrorKind.SESSION_EXPIRED,
                "Session expirée.",
            )

            ApiCode.ACCESS_DENIED -> ApiResult.Failure(
                ApiErrorKind.ACCESS_DENIED,
                response.message.ifBlank { "Accès refusé pour ce compte." },
                response.diagnostic(),
            )

            else -> ApiResult.Failure(
                ApiErrorKind.PROTOCOL,
                response.message.ifBlank { "Réponse inattendue du serveur (code ${response.code})." },
                response.diagnostic(),
            )
        }
    }

    /**
     * Le jeton GTK est obtenu par un GET préalable, qui pose aussi les cookies de session.
     * Sans ce couple exact (en-tête + cookie), la connexion est rejetée par le WAF.
     */
    private fun refreshGtk() {
        val response = engine.execute(
            HttpRequest(
                url = url("/login.awp", mapOf("gtk" to "1")),
                method = "GET",
                headers = browserHeaders(),
            ),
        )
        captureCookies(response)
        gtk = response.header("X-GTK") ?: cookies["GTK"].orEmpty()
        if (gtk.isBlank()) throw NetworkFailure("Le jeton GTK est vide.")
    }

    private fun request(
        path: String,
        verb: String? = null,
        payload: JSONObject,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String> = emptyMap(),
        keyOrder: List<String> = emptyList(),
    ): Envelope {
        val query = LinkedHashMap<String, String>()
        verb?.let { query["verbe"] = it }
        query.putAll(params)
        val encodedPayload = Encoding.formEncode(Encoding.pythonJson(payload, keyOrder))
        val requestHeaders = buildMap {
            putAll(browserHeaders())
            put("Content-Type", "application/x-www-form-urlencoded")
            if (gtk.isNotBlank()) put("X-GTK", gtk)
            cookieHeader()?.let { put("Cookie", it) }
            putAll(headers)
        }
        val response = engine.execute(
            HttpRequest(
                url = url(path, query),
                method = "POST",
                headers = requestHeaders,
                body = "data=$encodedPayload",
            ),
        )
        captureCookies(response)
        response.header("X-GTK")?.let { gtk = it }
        response.header("X-Token")?.let { token = it }
        response.header("2FA-Token")?.let { twoFactorToken = it }
        val body = runCatching { JSONObject(response.body) }.getOrElse { JSONObject() }
        body.optString("token").takeIf { it.isNotBlank() }?.let { token = it }
        return Envelope(
            body = body,
            httpStatus = response.status,
            rawBody = response.body,
            requestSummary = summarize(path, payload, requestHeaders.keys, encodedPayload.length),
        )
    }

    private fun url(path: String, params: Map<String, String>): String {
        val all = LinkedHashMap(params)
        all["v"] = apiVersion
        val query = all.entries.joinToString("&") { (key, value) ->
            "${Encoding.formEncode(key)}=${Encoding.formEncode(value)}"
        }
        return "$apiBase$path?$query"
    }

    private fun authHeaders(): Map<String, String> = buildMap {
        token?.let { put("X-Token", it) }
        twoFactorToken?.let { put("2FA-Token", it) }
    }

    /**
     * L'API refuse les clients qui ne ressemblent pas au site web. Ces en-têtes sont donc
     * une contrainte du serveur, pas un déguisement : ils sont fixes et documentés ici.
     */
    private fun browserHeaders(): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json, text/plain, */*",
        "Origin" to "https://www.ecoledirecte.com",
        "Referer" to "https://www.ecoledirecte.com/",
        "X-Client" to "Android",
    )

    private fun cookieHeader(): String? = cookies.entries
        .joinToString("; ") { (name, value) -> "$name=$value" }
        .takeIf { it.isNotBlank() }

    private fun captureCookies(response: HttpResponse) {
        response.headers("Set-Cookie").forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            val name = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "")
            if (name.isNotBlank() && name.lowercase() !in COOKIE_ATTRIBUTES) {
                if (value.isBlank()) cookies.remove(name) else cookies[name] = value
            }
        }
    }

    private fun summarize(
        path: String,
        payload: JSONObject,
        headerNames: Set<String>,
        encodedLength: Int,
    ): String = buildString {
        append("POST $path")
        append(" ; champs=")
        append(payload.keys().asSequence().toList().joinToString(","))
        append(" ; identifiant=${payload.optString("identifiant").length} car.")
        append(" ; motdepasse=${payload.optString("motdepasse").length} car.")
        append(" ; corps=$encodedLength octets")
        append(" ; en-têtes=")
        append(headerNames.joinToString(",") { it.lowercase() })
    }

    /** Réponse brute plus le nécessaire pour bâtir un diagnostic sans secrets. */
    private data class Envelope(
        val body: JSONObject,
        val httpStatus: Int,
        val rawBody: String,
        val requestSummary: String,
    ) {
        val code: Int get() = body.optInt("code", if (httpStatus in 200..299) ApiCode.OK else httpStatus)
        val message: String get() = body.optString("message").orEmpty()

        fun diagnostic(): String = buildString {
            appendLine("HTTP $httpStatus")
            appendLine("Requête : $requestSummary")
            appendLine("Réponse :")
            append(Encoding.redactSecrets(rawBody).take(2000).ifBlank { "(vide)" })
        }
    }

    companion object {
        const val DEFAULT_API_BASE = "https://api.ecoledirecte.com/v3"
        const val DEFAULT_API_VERSION = "4.75.0"
        const val MESSAGES_PER_PAGE = 50
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        /** Ordre des champs du corps de connexion, tel que l'envoie le client web. */
        private val LOGIN_KEY_ORDER = listOf("identifiant", "motdepasse", "isReLogin", "uuid", "fa")
        private val COOKIE_ATTRIBUTES = setOf(
            "path", "domain", "expires", "max-age", "samesite", "version", "comment", "secure", "httponly",
        )
    }
}
