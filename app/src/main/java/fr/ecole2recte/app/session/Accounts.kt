package fr.ecole2recte.app.session

import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.SchoolModule
import fr.ecole2recte.app.data.closedModules
import fr.ecole2recte.app.data.Student
import java.util.Locale

/** Un compte enregistré sur l'appareil, tel que l'interface l'affiche. */
data class AccountSummary(
    val identifier: String,
    val displayName: String,
    val initials: String,
    val typeLabel: String,
    val isActive: Boolean,
    val students: List<Student>,
    val activeStudentId: String?,
)

/** Le profil dont les données sont affichées : un compte, ou un élève d'un compte famille. */
data class ActiveProfile(
    val identifier: String,
    val studentId: String?,
    val firstName: String,
    val displayName: String,
    val initials: String,
    /** Modules que l'établissement n'ouvre pas pour ce profil : l'application ne les appelle pas et les masque. */
    val closedModules: Set<SchoolModule> = emptySet(),
) {
    /** Change quand on change de compte ou d'élève : sert à repartir d'un état vierge. */
    val key: String get() = "$identifier/${studentId.orEmpty()}"
}

internal fun titleCase(value: String): String =
    value.trim().lowercase(Locale.FRENCH).split(' ').joinToString(" ") { word ->
        word.split('-').joinToString("-") { part -> part.replaceFirstChar { it.titlecase(Locale.FRENCH) } }
    }

internal fun initialsOf(firstName: String, lastName: String, fallback: String): String =
    (firstName.take(1) + lastName.take(1)).uppercase(Locale.FRENCH).ifBlank { fallback.take(1).uppercase(Locale.FRENCH) }

internal fun SavedLogin.toSummary(isActive: Boolean): AccountSummary {
    val profile = profile
    val students = profile?.students.orEmpty()
    val name = listOfNotNull(
        profile?.firstName?.takeIf { it.isNotBlank() }?.let(::titleCase),
        profile?.lastName?.takeIf { it.isNotBlank() }?.let(::titleCase),
    ).joinToString(" ").ifBlank { identifier }
    return AccountSummary(
        identifier = identifier,
        displayName = name,
        initials = initialsOf(profile?.firstName.orEmpty(), profile?.lastName.orEmpty(), identifier),
        typeLabel = when {
            students.isNotEmpty() -> "Famille"
            profile?.type == "E" -> "Élève"
            profile?.type == "P" -> "Professeur"
            else -> "Compte"
        },
        isActive = isActive,
        students = students,
        activeStudentId = activeStudentId?.takeIf { id -> students.any { it.id == id } } ?: students.firstOrNull()?.id,
    )
}

internal fun SavedLogin.toActiveProfile(): ActiveProfile {
    val summary = toSummary(isActive = true)
    val student = summary.students.firstOrNull { it.id == summary.activeStudentId }
    return if (student != null) {
        ActiveProfile(
            identifier = identifier,
            studentId = student.id,
            firstName = titleCase(student.firstName),
            displayName = "${titleCase(student.firstName)} ${titleCase(student.lastName)}".trim(),
            initials = initialsOf(student.firstName, student.lastName, identifier),
            closedModules = closedModules(student.modules.ifEmpty { profile?.modules.orEmpty() }),
        )
    } else {
        ActiveProfile(
            identifier = identifier,
            studentId = null,
            firstName = profile?.firstName?.takeIf { it.isNotBlank() }?.let(::titleCase) ?: identifier,
            displayName = summary.displayName,
            initials = summary.initials,
            closedModules = closedModules(profile?.modules.orEmpty()),
        )
    }
}
