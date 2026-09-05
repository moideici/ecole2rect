package fr.ecole2recte.app.core.store

import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.AttachmentSource
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.Document
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolLifeEvent
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.model.SessionContent
import fr.ecole2recte.app.core.model.SubjectAverage
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sérialisation du cache local.
 *
 * Le cache est écrit dans le format de l'application, pas dans celui d'ÉcoleDirecte : une
 * évolution du protocole ne rend donc pas les données déjà enregistrées illisibles. Le
 * numéro de version permet d'ignorer proprement un cache trop ancien plutôt que de tenter
 * de le réinterpréter.
 */
object SnapshotCodec {

    /**
     * Version 2 : ajout de la vie scolaire, des contenus de séance et des documents. Un
     * cache d'une version antérieure est ignoré plutôt que réinterprété — il sera reconstruit
     * à la première synchronisation.
     */
    const val VERSION = 2

    fun encode(snapshot: SchoolSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        snapshot.account?.let { put("account", encodeAccount(it)) }
        put("lessons", snapshot.lessons.jsonArray(::encodeLesson))
        put("homework", snapshot.homework.jsonArray(::encodeHomework))
        put("grades", snapshot.grades.jsonArray(::encodeGrade))
        put("periods", snapshot.periods.jsonArray(::encodePeriod))
        put("subjectAverages", snapshot.subjectAverages.jsonArray(::encodeSubjectAverage))
        put("messages", snapshot.messages.jsonArray(::encodeMessage))
        put("schoolLife", snapshot.schoolLife.jsonArray(::encodeSchoolLife))
        put("sessionContents", snapshot.sessionContents.jsonArray(::encodeSession))
        put("documents", snapshot.documents.jsonArray(::encodeDocument))
        put(
            "syncedAt",
            JSONObject().apply {
                snapshot.syncedAt.forEach { (section, millis) -> put(section.name, millis) }
            },
        )
    }.toString()

    fun decode(raw: String?): SchoolSnapshot? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("version") != VERSION) return null
        return runCatching {
            SchoolSnapshot(
                account = root.optJSONObject("account")?.let(::decodeAccount),
                lessons = root.optJSONArray("lessons").decode(::decodeLesson),
                homework = root.optJSONArray("homework").decode(::decodeHomework),
                grades = root.optJSONArray("grades").decode(::decodeGrade),
                periods = root.optJSONArray("periods").decode(::decodePeriod),
                subjectAverages = root.optJSONArray("subjectAverages").decode(::decodeSubjectAverage),
                messages = root.optJSONArray("messages").decode(::decodeMessage),
                schoolLife = root.optJSONArray("schoolLife").decode(::decodeSchoolLife),
                sessionContents = root.optJSONArray("sessionContents").decode(::decodeSession),
                documents = root.optJSONArray("documents").decode(::decodeDocument),
                syncedAt = decodeSyncedAt(root.optJSONObject("syncedAt")),
            )
        }.getOrNull()
    }

    // ── Encodage ─────────────────────────────────────────────────────────────

    private fun <T> List<T>.jsonArray(encode: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(encode(it)) } }

    private fun encodeAccount(account: Account) = JSONObject().apply {
        put("id", account.id)
        put("type", account.type)
        put("firstName", account.firstName)
        put("lastName", account.lastName)
        put("className", account.className)
        put("schoolName", account.schoolName)
    }

    private fun encodeLesson(lesson: Lesson) = JSONObject().apply {
        put("id", lesson.id)
        put("subject", lesson.subject)
        put("teacher", lesson.teacher)
        put("room", lesson.room)
        put("start", lesson.start.toString())
        put("end", lesson.end.toString())
        put("canceled", lesson.canceled)
        put("exam", lesson.exam)
        put("note", lesson.note)
    }

    private fun encodeHomework(item: HomeworkItem) = JSONObject().apply {
        put("id", item.id)
        put("subject", item.subject)
        put("dueDate", item.dueDate.toString())
        item.givenOn?.let { put("givenOn", it.toString()) }
        put("content", item.content)
        put("exam", item.exam)
        put("done", item.done)
        put("attachments", item.attachments.jsonArray(::encodeAttachment))
    }

    private fun encodeAttachment(attachment: Attachment) = JSONObject().apply {
        put("id", attachment.id)
        put("name", attachment.name)
        put("type", attachment.type)
        put("source", attachment.source.name)
    }

    private fun encodeGrade(grade: Grade) = JSONObject().apply {
        put("id", grade.id)
        put("subject", grade.subject)
        put("label", grade.label)
        grade.value?.let { put("value", it) }
        put("scale", grade.scale)
        put("coefficient", grade.coefficient)
        grade.classAverage?.let { put("classAverage", it) }
        grade.date?.let { put("date", it.toString()) }
        put("periodCode", grade.periodCode)
        put("counted", grade.counted)
        put("comment", grade.comment)
    }

    private fun encodePeriod(period: Period) = JSONObject().apply {
        put("code", period.code)
        put("name", period.name)
        period.average?.let { put("average", it) }
        period.classAverage?.let { put("classAverage", it) }
        period.start?.let { put("start", it.toString()) }
        period.end?.let { put("end", it.toString()) }
        put("current", period.current)
    }

    private fun encodeSubjectAverage(average: SubjectAverage) = JSONObject().apply {
        put("periodCode", average.periodCode)
        put("subject", average.subject)
        average.average?.let { put("average", it) }
        average.classAverage?.let { put("classAverage", it) }
        average.coefficient?.let { put("coefficient", it) }
        put("teacher", average.teacher)
        put("appreciation", average.appreciation)
    }

    private fun encodeMessage(message: MessageItem) = JSONObject().apply {
        put("id", message.id)
        put("subject", message.subject)
        put("sender", message.sender)
        message.date?.let { put("date", it.toString()) }
        put("read", message.read)
        put("preview", message.preview)
        put("body", message.body)
        put("attachments", message.attachments.jsonArray(::encodeAttachment))
    }

    private fun encodeSchoolLife(event: SchoolLifeEvent) = JSONObject().apply {
        put("id", event.id)
        put("kind", event.kind.name)
        put("label", event.label)
        put("start", event.start.toString())
        event.end?.let { put("end", it.toString()) }
        put("justified", event.justified)
        put("reason", event.reason)
        put("comment", event.comment)
    }

    private fun encodeSession(session: SessionContent) = JSONObject().apply {
        put("id", session.id)
        put("subject", session.subject)
        put("date", session.date.toString())
        put("content", session.content)
        put("attachments", session.attachments.jsonArray(::encodeAttachment))
    }

    private fun encodeDocument(document: Document) = JSONObject().apply {
        put("id", document.id)
        put("name", document.name)
        put("category", document.category)
        document.date?.let { put("date", it.toString()) }
        put("type", document.type)
    }

    // ── Décodage ─────────────────────────────────────────────────────────────

    private fun <T> JSONArray?.decode(decode: (JSONObject) -> T?): List<T> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { entry -> decode(entry)?.let(::add) }
            }
        }
    }

    private fun decodeAccount(entry: JSONObject) = Account(
        id = entry.optString("id"),
        type = entry.optString("type"),
        firstName = entry.optString("firstName"),
        lastName = entry.optString("lastName"),
        className = entry.optString("className"),
        schoolName = entry.optString("schoolName"),
    )

    private fun decodeLesson(entry: JSONObject): Lesson? {
        val start = entry.dateTime("start") ?: return null
        return Lesson(
            id = entry.optString("id"),
            subject = entry.optString("subject"),
            teacher = entry.optString("teacher"),
            room = entry.optString("room"),
            start = start,
            end = entry.dateTime("end") ?: start.plusHours(1),
            canceled = entry.optBoolean("canceled"),
            exam = entry.optBoolean("exam"),
            note = entry.optString("note"),
        )
    }

    private fun decodeHomework(entry: JSONObject): HomeworkItem? {
        val dueDate = entry.date("dueDate") ?: return null
        return HomeworkItem(
            id = entry.optString("id"),
            subject = entry.optString("subject"),
            dueDate = dueDate,
            givenOn = entry.date("givenOn"),
            content = entry.optString("content"),
            exam = entry.optBoolean("exam"),
            done = entry.optBoolean("done"),
            attachments = entry.optJSONArray("attachments").decode(::decodeAttachment),
        )
    }

    private fun decodeAttachment(entry: JSONObject) = Attachment(
        id = entry.optString("id"),
        name = entry.optString("name"),
        type = entry.optString("type"),
        source = AttachmentSource.entries.firstOrNull { it.name == entry.optString("source") }
            ?: AttachmentSource.HOMEWORK,
    )

    private fun decodeGrade(entry: JSONObject) = Grade(
        id = entry.optString("id"),
        subject = entry.optString("subject"),
        label = entry.optString("label"),
        value = entry.number("value"),
        scale = entry.number("scale") ?: 20.0,
        coefficient = entry.number("coefficient") ?: 1.0,
        classAverage = entry.number("classAverage"),
        date = entry.date("date"),
        periodCode = entry.optString("periodCode"),
        counted = entry.optBoolean("counted", true),
        comment = entry.optString("comment"),
    )

    private fun decodePeriod(entry: JSONObject) = Period(
        code = entry.optString("code"),
        name = entry.optString("name"),
        average = entry.number("average"),
        classAverage = entry.number("classAverage"),
        start = entry.date("start"),
        end = entry.date("end"),
        current = entry.optBoolean("current"),
    )

    private fun decodeSubjectAverage(entry: JSONObject) = SubjectAverage(
        periodCode = entry.optString("periodCode"),
        subject = entry.optString("subject"),
        average = entry.number("average"),
        classAverage = entry.number("classAverage"),
        coefficient = entry.number("coefficient"),
        teacher = entry.optString("teacher"),
        appreciation = entry.optString("appreciation"),
    )

    private fun decodeMessage(entry: JSONObject) = MessageItem(
        id = entry.optString("id"),
        subject = entry.optString("subject"),
        sender = entry.optString("sender"),
        date = entry.dateTime("date"),
        read = entry.optBoolean("read"),
        preview = entry.optString("preview"),
        body = entry.optString("body"),
        attachments = entry.optJSONArray("attachments").decode(::decodeAttachment),
    )

    private fun decodeSchoolLife(entry: JSONObject): SchoolLifeEvent? {
        val start = entry.dateTime("start") ?: return null
        return SchoolLifeEvent(
            id = entry.optString("id"),
            kind = SchoolLifeKind.entries.firstOrNull { it.name == entry.optString("kind") }
                ?: SchoolLifeKind.OTHER,
            label = entry.optString("label"),
            start = start,
            end = entry.dateTime("end"),
            justified = entry.optBoolean("justified"),
            reason = entry.optString("reason"),
            comment = entry.optString("comment"),
        )
    }

    private fun decodeSession(entry: JSONObject): SessionContent? {
        val date = entry.date("date") ?: return null
        return SessionContent(
            id = entry.optString("id"),
            subject = entry.optString("subject"),
            date = date,
            content = entry.optString("content"),
            attachments = entry.optJSONArray("attachments").decode(::decodeAttachment),
        )
    }

    private fun decodeDocument(entry: JSONObject) = Document(
        id = entry.optString("id"),
        name = entry.optString("name"),
        category = entry.optString("category"),
        date = entry.date("date"),
        type = entry.optString("type"),
    )

    private fun decodeSyncedAt(entry: JSONObject?): Map<DataSection, Long> {
        val source = entry ?: return emptyMap()
        return DataSection.entries.mapNotNull { section ->
            val millis = source.optLong(section.name, 0L)
            if (millis > 0L) section to millis else null
        }.toMap()
    }

    private fun JSONObject.number(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.date(key: String): LocalDate? =
        optString(key).takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JSONObject.dateTime(key: String): LocalDateTime? =
        optString(key).takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
}
