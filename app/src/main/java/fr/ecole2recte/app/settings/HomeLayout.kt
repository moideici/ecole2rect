package fr.ecole2recte.app.settings

/** Les blocs de l'accueil, dans leur ordre d'origine. */
enum class HomeSection(val label: String) {
    /** Un résumé par enfant ; n'apparaît que pour un compte famille. */
    Family("Ma famille"),
    NextClass("Prochain cours"),
    Today("Aujourd'hui"),
    Highlights("À savoir"),
    Reminder("À ne pas oublier"),
    Grades("Dernières notes"),
}

/** L'ordre et la visibilité des blocs de l'accueil, au choix de l'utilisateur. */
data class HomeLayout(
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = emptySet(),
) {
    val visible: List<HomeSection> get() = order.filter { it !in hidden }

    /** Monte (`-1`) ou descend (`+1`) un bloc d'un cran. */
    fun move(section: HomeSection, delta: Int): HomeLayout {
        val index = order.indexOf(section)
        val target = index + delta
        if (index < 0 || target !in order.indices) return this
        val moved = order.toMutableList()
        moved.removeAt(index)
        moved.add(target, section)
        return copy(order = moved)
    }

    fun toggle(section: HomeSection): HomeLayout =
        copy(hidden = if (section in hidden) hidden - section else hidden + section)

    fun serialize(): String = order.joinToString(",") { it.name } + ";" + hidden.joinToString(",") { it.name }

    companion object {
        /**
         * Relit un réglage enregistré. Tolérant : un bloc inconnu est ignoré et un bloc absent
         * (ajouté par une mise à jour) retrouve sa place à la fin, visible.
         */
        fun parse(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return HomeLayout()
            val parts = text.split(';')
            fun sections(raw: String?) = raw.orEmpty().split(',').mapNotNull { name ->
                HomeSection.entries.firstOrNull { it.name == name }
            }
            val order = sections(parts.getOrNull(0)).distinct()
            val complete = order + HomeSection.entries.filter { it !in order }
            return HomeLayout(complete, sections(parts.getOrNull(1)).toSet())
        }
    }
}
