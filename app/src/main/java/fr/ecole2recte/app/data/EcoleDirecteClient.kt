package fr.ecole2recte.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Ce que l'écran de connexion et la session attendent d'un client d'authentification. */
interface AuthClient {
    val approvedAnswer: TrustedAnswer?
    fun login(identifier: String, password: String, trustedAnswer: TrustedAnswer?): LoginResult
    fun completeSecurityQuestion(choice: String, identifier: String, password: String): LoginResult
}

/**
 * Small protocol client kept separate from Compose so API changes do not leak into the UI.
 * EcoleDirecte exposes an undocumented endpoint; keep all protocol constants here.
 */
class EcoleDirecteClient(
    private val apiBase: String = "https://api.ecoledirecte.com/v3",
    private val apiVersion: String = "4.75.0",
) : AuthClient {
    private val cookies = LinkedHashMap<String, String>()
    private var currentGtk: String = ""
    var token: String? = null
        private set
    var twoFactorToken: String? = null
        private set
    override var approvedAnswer: TrustedAnswer? = null
        private set

    override fun login(identifier: String, password: String, trustedAnswer: TrustedAnswer?): LoginResult {
        val payload = JSONObject()
            .put("identifiant", identifier)
            .put("motdepasse", password)
            .put("isReLogin", false)
            .put("uuid", "")
            .put("fa", trustedAnswer?.let { JSONArray().put(it.toJson()) } ?: JSONArray())

        val response = postLogin(payload)
        return when (response.body.optInt("code")) {
            ApiCode.OK -> {
                token = response.body.optString("token").takeIf { it.isNotBlank() } ?: response.headers["x-token"]
                LoginResult.Success(extractAccounts(response.body))
            }

            ApiCode.AUTH_2FA -> {
                token = response.headers["x-token"]
                twoFactorToken = response.headers["2fa-token"]
                val challengeResponse = post(
                    path = "/connexion/doubleauth.awp",
                    verb = "get",
                    payload = JSONObject(),
                    headers = authHeaders(),
                )
                val data = challengeResponse.body.optJSONObject("data")
                    ?: return LoginResult.Failure(
                        challengeResponse.body.optString("message")
                            .ifBlank { "Question de sécurité indisponible." },
                        challengeResponse.diagnostic(),
                        FailureKind.Service,
                    )
                LoginResult.SecurityQuestionRequired(
                    question = data.optString("question").decodeBase64()
                        ?: data.optString("question").takeIf { it.isNotBlank() },
                    choices = data.optJSONArray("propositions").decodeChoices(),
                )
            }
            ApiCode.INVALID_CREDENTIALS -> LoginResult.Failure(
                "Identifiant ou mot de passe invalide.",
                response.diagnostic(),
                FailureKind.InvalidCredentials,
            )
            ApiCode.BLOCKED, ApiCode.BLOCKED_ALT -> LoginResult.Failure(
                "Compte bloqué.",
                response.diagnostic(),
                FailureKind.Blocked,
            )
            else -> LoginResult.Failure(
                response.body.optString("message").ifBlank { "Connexion impossible." },
                response.diagnostic(),
                if (response.rawBody.isBlank() || response.httpStatus >= 500) FailureKind.Service else FailureKind.Other,
            )
        }
    }

    /**
     * Python's json.dumps(...), with its default `", "` and `": "` separators and
     * ensure_ascii=True, for the small JSON values used by this API.
     */
    private fun JSONObject.toPythonJson(): String = buildString {
        append('{')
        keys().asSequence().toList().forEachIndexed { index, key ->
            if (index > 0) append(", ")
            append(pythonQuote(key))
            append(": ")
            append(opt(key).toPythonJsonValue())
        }
        append('}')
    }

    private fun Any?.toPythonJsonValue(): String = when (this) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> toPythonJson()
        is JSONArray -> buildString {
            append('[')
            for (index in 0 until length()) {
                if (index > 0) append(", ")
                append(opt(index).toPythonJsonValue())
            }
            append(']')
        }
        is String -> pythonQuote(this)
        is Boolean -> if (this) "true" else "false"
        is Number -> toString()
        else -> pythonQuote(toString())
    }

    private fun pythonQuote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20 || character.code > 0x7E) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    override fun completeSecurityQuestion(choice: String, identifier: String, password: String): LoginResult {
        val xToken = token ?: return LoginResult.Failure("Session de vérification absente.", kind = FailureKind.Other)
        val twoFa = twoFactorToken ?: return LoginResult.Failure("Jeton de vérification absent.")
        val answer = JSONObject().put(
            "choix",
            Base64.getEncoder().encodeToString(choice.toByteArray(Charsets.UTF_8)),
        )
        val response = post(
            path = "/connexion/doubleauth.awp",
            verb = "post",
            payload = answer,
            headers = mapOf("X-Token" to xToken, "2FA-Token" to twoFa),
        )
        if (response.body.optInt("code") != ApiCode.OK) {
            return LoginResult.Failure("Réponse de sécurité refusée.", response.diagnostic(), FailureKind.SecurityRefused)
        }
        val data = response.body.optJSONObject("data")
            ?: return LoginResult.Failure("Réponse de sécurité incomplète.")
        val approved = TrustedAnswer(
            cn = data.optString("cn"),
            cv = data.optString("cv"),
        )
        approvedAnswer = approved
        return login(
            identifier = identifier,
            password = password,
            trustedAnswer = approved,
        )
    }

    fun getLessons(studentId: String, dateFrom: String, dateTo: String): List<Lesson> {
        val response = post(
            path = "/E/$studentId/emploidutemps.awp",
            verb = "get",
            payload = JSONObject()
                .put("dateDebut", dateFrom)
                .put("dateFin", dateTo)
                .put("avecTrous", false),
            headers = authHeaders(),
        )
        if (response.body.optInt("code", ApiCode.OK) !in listOf(0, ApiCode.OK)) {
            throw EcoleDirecteException("Impossible de récupérer l'emploi du temps.")
        }
        return response.body.lessonArray().mapNotNull { lesson ->
            Lesson(
                subject = lesson.optString("matiere").ifBlank { lesson.optString("text", "Cours") },
                room = lesson.optString("salle"),
                teacher = lesson.optString("prof"),
                start = lesson.optString("start_date").ifBlank { lesson.optString("date_debut") },
                end = lesson.optString("end_date").ifBlank { lesson.optString("date_fin") },
            )
        }
    }

    private fun postLogin(payload: JSONObject): Response =
        post("/login.awp", null, payload, mapOf("X-GTK" to freshGtk()))

    private fun freshGtk(): String {
        val connection = openConnection("/login.awp?gtk=1&v=$apiVersion", "GET")
        return try {
            connection.inputStream.use { it.readBytes() }
            captureCookies(connection.headerFields)
            currentGtk = headerValue(connection.headerFields, "X-GTK")
                .ifBlank { cookies["GTK"].orEmpty() }
            currentGtk
        } catch (error: IOException) {
            val status = runCatching { connection.responseCode }.getOrDefault(-1)
            if (status >= 400) {
                throw EcoleDirecteException("ÉcoleDirecte ne répond pas correctement (HTTP $status).", error, FailureKind.Service)
            }
            throw EcoleDirecteException("Connexion réseau impossible.", error, FailureKind.Network)
        } finally {
            connection.disconnect()
        }.let { value -> value.takeIf { it.isNotBlank() } }
            ?: throw EcoleDirecteException("Le jeton GTK est vide.", kind = FailureKind.Service)
    }

    private fun post(
        path: String,
        verb: String?,
        payload: JSONObject,
        headers: Map<String, String> = emptyMap(),
        includeVersion: Boolean = true,
    ): Response {
        val query = when {
            verb != null -> "?verbe=$verb&v=$apiVersion"
            includeVersion -> "?v=$apiVersion"
            else -> ""
        }
        val connection = openConnection("$path$query", "POST")
        return try {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            currentGtk.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("X-GTK", it) }
            cookieHeader().takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
            // requests.post(data={"data": json.dumps(payload)}) uses Python's
            // default JSON separators and application/x-www-form-urlencoded.
            val encodedPayload = formEncode(payload.toPythonJson())
            val body = "data=$encodedPayload"
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val httpStatus = connection.responseCode
            val stream = if (httpStatus in 200..399) connection.inputStream else connection.errorStream
            val json = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(
                body = runCatching { JSONObject(json) }.getOrElse { JSONObject() },
                headers = connection.headerFields.mapNotNull { (name, values) ->
                    name?.lowercase()?.let { it to values.firstOrNull().orEmpty() }
                }.toMap(),
                httpStatus = httpStatus,
                rawBody = json,
                requestSummary = requestSummary(payload, headers, encodedPayload.length),
            ).also { response ->
                captureCookies(connection.headerFields)
                headerValue(connection.headerFields, "X-GTK")
                    .takeIf { it.isNotBlank() }
                    ?.let { currentGtk = it }
                response.headers["x-token"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token = it }
                response.headers["2fa-token"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { twoFactorToken = it }
                response.body.optString("token").takeIf { it.isNotBlank() }?.let { token = it }
            }
        } catch (error: IOException) {
            throw EcoleDirecteException("Connexion réseau impossible.", error, FailureKind.Network)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            // Match the browser-like fingerprint required by the current API/WAF.
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            )
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Origin", "https://www.ecoledirecte.com")
            setRequestProperty("Referer", "https://www.ecoledirecte.com/")
            setRequestProperty("X-Client", "Android")
            doInput = true
            doOutput = method == "POST"
        }

    private fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    private fun captureCookies(fields: Map<String?, List<String>?>) {
        fields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
            .forEach { raw ->
                raw.split(';').forEach { part ->
                    val pair = part.trim()
                    val name = pair.substringBefore('=', "").trim()
                    val value = pair.substringAfter('=', "")
                    if (name.isNotBlank() && '=' in pair && name.lowercase() !in cookieAttributes) {
                        if (value.isBlank()) cookies.remove(name) else cookies[name] = value
                    }
                }
            }
    }

    private fun headerValue(fields: Map<String?, List<String>?>, name: String): String =
        fields.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            .orEmpty()

    private fun authHeaders(): Map<String, String> = buildMap {
        token?.let { put("X-Token", it) }
        twoFactorToken?.let { put("2FA-Token", it) }
    }

    private fun extractAccounts(body: JSONObject): List<Account> {
        val accounts = body.optJSONObject("data")?.optJSONArray("accounts") ?: return emptyList()
        return buildList {
            for (index in 0 until accounts.length()) {
                val account = accounts.optJSONObject(index) ?: continue
                add(
                    Account(
                        id = account.optString("id"),
                        type = account.optString("typeCompte"),
                        firstName = account.optString("prenom"),
                        lastName = account.optString("nom"),
                        // Forme à confirmer sur un vrai compte famille : profile.eleves[].
                        students = account.optJSONObject("profile")?.optJSONArray("eleves").objects().map { student ->
                            Student(
                                id = student.optString("id"),
                                firstName = student.optString("prenom"),
                                lastName = student.optString("nom"),
                                className = student.optString("classe"),
                            )
                        },
                    ),
                )
            }
        }
    }

    private data class Response(
        val body: JSONObject,
        val headers: Map<String, String>,
        val httpStatus: Int,
        val rawBody: String,
        val requestSummary: String,
    ) {
        fun diagnostic(): String {
            val safeHeaders = headers
                .filterKeys { it !in setOf("x-token", "2fa-token", "set-cookie", "cookie") }
                .entries
                .joinToString("\n") { (key, value) -> "$key: $value" }
            return buildString {
                appendLine("HTTP $httpStatus")
                appendLine("Requête: $requestSummary")
                appendLine("Réponse API:")
                val safeBody = redactSecrets(rawBody).take(4000).ifBlank { "(vide)" }
                appendLine(safeBody)
                if (safeHeaders.isNotBlank()) {
                    appendLine("En-têtes utiles:")
                    append(safeHeaders)
                }
            }
        }
    }

}

data class TrustedAnswer(val cn: String, val cv: String) {
    fun toJson(): JSONObject = JSONObject().put("cn", cn).put("cv", cv)
}

data class Lesson(
    val subject: String,
    val room: String,
    val teacher: String,
    val start: String,
    val end: String,
)

/** Nature d'un échec de connexion : décide si on peut réessayer et ce qu'on affiche. */
enum class FailureKind {
    /** Pas de réseau : on peut réessayer sans risque. */
    Network,

    /** ÉcoleDirecte répond mal (erreur serveur, réponse vide) : on peut réessayer plus tard. */
    Service,

    /** Identifiant ou mot de passe refusé : ne jamais réessayer sans que l'utilisateur les change. */
    InvalidCredentials,

    /** Compte bloqué par ÉcoleDirecte : ne jamais réessayer. */
    Blocked,

    /** Réponse à la question de sécurité refusée : ne jamais réessayer la même. */
    SecurityRefused,

    Other,
}

sealed interface LoginResult {
    data class Success(val accounts: List<Account>) : LoginResult
    data class SecurityQuestionRequired(val question: String?, val choices: List<String>) : LoginResult
    data class Failure(
        val message: String,
        val diagnostic: String? = null,
        val kind: FailureKind = FailureKind.Other,
    ) : LoginResult
}

/** Élève rattaché à un compte famille. */
data class Student(val id: String, val firstName: String, val lastName: String, val className: String = "")

data class Account(
    val id: String,
    val type: String,
    val firstName: String,
    val lastName: String,
    val students: List<Student> = emptyList(),
)

class EcoleDirecteException(
    message: String,
    cause: Throwable? = null,
    val kind: FailureKind = FailureKind.Other,
) : RuntimeException(message, cause)

private fun redactSecrets(value: String): String =
    value.replace(Regex("(?i)(motdepasse|password|token|x-token|2fa-token|cookie|set-cookie)\"?\\s*[:=]\\s*\"?[^\",}\\s]+"), "$1=<masqué>")

private fun formEncode(value: String): String {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val hex = "0123456789ABCDEF"
    return buildString(bytes.size * 3) {
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            if (unsigned == 0x20) {
                append('+')
                return@forEach
            }
            if (unsigned in 0x30..0x39 || unsigned in 0x41..0x5A || unsigned in 0x61..0x7A ||
                unsigned == '-'.code || unsigned == '_'.code || unsigned == '.'.code || unsigned == '~'.code
            ) {
                append(unsigned.toChar())
            } else {
                append('%')
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0x0F])
            }
        }
    }
}

private fun requestSummary(payload: JSONObject, headers: Map<String, String>, encodedLength: Int): String {
    val identifierLength = payload.optString("identifiant").length
    val passwordLength = payload.optString("motdepasse").length
    val faSize = payload.optJSONArray("fa")?.length() ?: 0
    val headerNames = headers.keys.joinToString(",") { it.lowercase() }
    return "keys=${payload.keys().asSequence().toList().joinToString(",")}; " +
        "identifierLength=$identifierLength; passwordLength=$passwordLength; faCount=$faSize; " +
        "encodedDataLength=$encodedLength; headers=$headerNames"
}

private val cookieAttributes = setOf(
    "path", "domain", "expires", "max-age", "samesite", "version", "comment",
)

private object ApiCode {
    const val OK = 200
    const val AUTH_2FA = 250
    const val INVALID_CREDENTIALS = 505
    const val BLOCKED = 516
    const val TOKEN_INVALID = 520
    const val BLOCKED_ALT = 535
}

private fun String?.decodeBase64(): String? = this?.takeIf { it.isNotBlank() }?.let {
    runCatching { String(Base64.getMimeDecoder().decode(it), Charsets.UTF_8) }.getOrNull()
}

private fun JSONArray?.decodeChoices(): List<String> = buildList {
    if (this@decodeChoices == null) return@buildList
    for (index in 0 until length()) {
        optString(index).decodeBase64()?.let(::add)
    }
}

private fun JSONObject.lessonArray(): List<JSONObject> {
    val direct = optJSONArray("lessons") ?: optJSONArray("data")
    if (direct != null) return direct.objects()
    val nested = optJSONObject("data") ?: return emptyList()
    return (nested.optJSONArray("lessons") ?: nested.optJSONArray("cours")).objects()
}

private fun JSONArray?.objects(): List<JSONObject> = buildList {
    if (this@objects == null) return@buildList
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

private fun JSONObject?.objects(): List<JSONObject> = this?.let { listOf(it) } ?: emptyList()
