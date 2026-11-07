package fr.ecole2recte.app.diagnostic

/** Ce qu'on signale : sert d'étiquette dans le rapport, pas de filtre. */
enum class ReportCategory(val label: String) {
    WrongData("Données fausses ou vides"),
    Crash("Plantage ou blocage"),
    Login("Connexion"),
    Notifications("Notifications"),
    Widgets("Widgets"),
    Display("Affichage"),
    Idea("Idée ou amélioration"),
    Other("Autre"),
}

/** Ce que l'appareil dit de lui-même ; passé en paramètre pour que le texte reste testable. */
data class DeviceInfo(val appVersion: String, val androidVersion: String, val model: String)

/**
 * Le texte d'un signalement. Il n'est jamais envoyé par l'application : l'utilisateur le relit,
 * puis choisit lui-même où il part (partage, copie ou ticket). La seule partie libre est ce qu'il
 * a tapé ; le diagnostic joint ne contient que des clés, des types et des tailles, jamais une valeur.
 */
object ProblemReport {
    const val MAX_DESCRIPTION = 1500

    fun title(category: ReportCategory): String = "[ecole2recte] ${category.label}"

    fun build(
        category: ReportCategory,
        description: String,
        device: DeviceInfo,
        diagnostic: String?,
    ): String = buildString {
        appendLine("Type : ${category.label}")
        appendLine("Application : ecole2recte ${device.appVersion}")
        appendLine("Android : ${device.androidVersion} — ${device.model}")
        appendLine()
        val text = description.trim().take(MAX_DESCRIPTION)
        appendLine("Ce qui s'est passé :")
        appendLine(text.ifBlank { "(non renseigné)" })
        if (diagnostic != null) {
            appendLine()
            appendLine("--- Diagnostic technique (aucune valeur personnelle) ---")
            append(diagnostic.trimEnd())
        }
    }
}
