package fr.ecole2recte.app.domain

import java.text.Normalizer
import java.util.Locale

data class SubjectIdentity(val code: String, val subCode: String, val name: String) {
    val key: String get() = listOf(code, subCode, name).joinToString("\u001f")

    companion object {
        fun normalized(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").trim().lowercase(Locale.FRENCH).replace(Regex("\\s+"), " ")

        fun match(code: String, name: String, subjects: List<SubjectIdentity>): Int? {
            val byCode = subjects.indices.filter { code.isNotBlank() && subjects[it].code == code }
            val candidates = byCode.ifEmpty { subjects.indices.filter { normalized(subjects[it].name) == normalized(name) } }
            return candidates.firstOrNull { normalized(subjects[it].name) == normalized(name) }
                ?: candidates.firstOrNull { subjects[it].subCode.isEmpty() }
                ?: candidates.firstOrNull()
        }
    }
}
