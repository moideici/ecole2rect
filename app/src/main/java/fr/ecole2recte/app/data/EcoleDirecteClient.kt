package fr.ecole2recte.app.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Small protocol client kept separate from Compose so API changes do not leak into the UI.
 * EcoleDirecte exposes an undocumented endpoint; keep all protocol constants here.
 */
class EcoleDirecteClient(
    private val apiBase: String = "https://api.ecoledirecte.com/v3",
    private val apiVersion: String = "4.100.6",
) {
    private var cookie: String? = null
    var token: String? = null
        private set
    var twoFactorToken: String? = null
        private set
    var approvedAnswer: TrustedAnswer? = null
        private set

    fun login(identifier: String, password: String, trustedAnswer: TrustedAnswer? = null): LoginResult {
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
                LoginResult.SecurityQuestionRequired(
                    question = response.body.optJSONObject("data")?.optString("question").decodeBase64(),
                    choices = response.body.optJSONObject("data")?.optJSONArray("propositions").decodeChoices(),
                )
            }
            ApiCode.INVALID_CREDENTIALS -> LoginResult.Failure("Identifiant ou mot de passe invalide.")
            ApiCode.BLOCKED, ApiCode.BLOCKED_ALT -> LoginResult.Failure("Compte bloqué.")
            else -> LoginResult.Failure(response.body.optString("message").ifBlank { "Connexion impossible." })
        }
    }

    fun completeSecurityQuestion(choice: String, identifier: String, password: String): LoginResult {
        val xToken = token ?: return LoginResult.Failure("Session de vérification absente.")
        val twoFa = twoFactorToken ?: return LoginResult.Failure("Jeton de vérification absent.")
        val answer = JSONObject().put(
            "choix",
            Base64.encodeToString(choice.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
        )
        val response = post(
            path = "/connexion/doubleauth.awp",
            verb = "post",
            payload = answer,
            headers = mapOf("X-Token" to xToken, "2FA-Token" to twoFa),
        )
        if (response.body.optInt("code") != ApiCode.OK) {
            return LoginResult.Failure("Réponse de sécurité refusée.")
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
            val setCookie = connection.headerFields["Set-Cookie"].orEmpty().firstOrNull()
                ?: throw EcoleDirecteException("Le serveur n'a pas fourni de jeton GTK.")
            cookie = setCookie.substringBefore(";")
            setCookie.substringAfter("GTK=", "").substringBefore(";")
        } finally {
            connection.disconnect()
        }.let { value -> value.takeIf { it.isNotBlank() } }
            ?: throw EcoleDirecteException("Le jeton GTK est vide.")
    }

    private fun post(
        path: String,
        verb: String?,
        payload: JSONObject,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val query = if (verb == null) "?v=$apiVersion" else "?verbe=$verb&v=$apiVersion"
        val connection = openConnection("$path$query", "POST")
        return try {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            cookie?.let { connection.setRequestProperty("Cookie", it) }
            val body = "data=" + URLEncoder.encode(payload.toString(), Charsets.UTF_8.name())
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..399) connection.inputStream else connection.errorStream
            val json = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(
                body = runCatching { JSONObject(json) }.getOrElse { JSONObject() },
                headers = connection.headerFields.mapNotNull { (name, values) ->
                    name?.lowercase()?.let { it to values.firstOrNull().orEmpty() }
                }.toMap(),
            ).also { response ->
                response.body.optString("token").takeIf { it.isNotBlank() }?.let { token = it }
            }
        } catch (error: IOException) {
            throw EcoleDirecteException("Connexion réseau impossible.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection =
        (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ecole2recte/0.1 Android")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Origin", "https://www.ecoledirecte.com")
            setRequestProperty("Referer", "https://www.ecoledirecte.com/")
            doInput = true
            doOutput = method == "POST"
        }

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
                    ),
                )
            }
        }
    }

    private data class Response(val body: JSONObject, val headers: Map<String, String>)
}

data class TrustedAnswer(val cn: String, val cv: String) {
    fun toJson(): JSONObject = JSONObject().put("cn", cn).put("cv", cv).put("uniq", false)
}

data class Lesson(
    val subject: String,
    val room: String,
    val teacher: String,
    val start: String,
    val end: String,
)

sealed interface LoginResult {
    data class Success(val accounts: List<Account>) : LoginResult
    data class SecurityQuestionRequired(val question: String?, val choices: List<String>) : LoginResult
    data class Failure(val message: String) : LoginResult
}

data class Account(val id: String, val type: String, val firstName: String, val lastName: String)

class EcoleDirecteException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private object ApiCode {
    const val OK = 200
    const val AUTH_2FA = 250
    const val INVALID_CREDENTIALS = 505
    const val BLOCKED = 516
    const val TOKEN_INVALID = 520
    const val BLOCKED_ALT = 535
}

private fun String?.decodeBase64(): String? = this?.takeIf { it.isNotBlank() }?.let {
    runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
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
