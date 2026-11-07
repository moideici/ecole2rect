package fr.ecole2recte.app.domain

/** Une section du document « Comment la moyenne est calculée ». */
data class MethodSection(val title: String, val paragraphs: List<String>)

/**
 * Le texte de l'écran « Comment la moyenne est calculée ? ». Il est identique à `docs/MOYENNES.md`
 * (un test le vérifie) : une ligne qui commence par « - » est une puce.
 */
object AverageMethod {
    val sections: List<MethodSection> = listOf(
        MethodSection(
            "Ce que l'application affiche",
            listOf(
                "Une moyenne est dite « officielle » quand ÉcoleDirecte la fournit : l'application la recopie telle quelle, sans la recalculer.",
                "Quand ÉcoleDirecte ne la fournit pas, l'application la calcule elle-même et l'écrit « calculée par l'app ».",
                "Une moyenne calculée par l'app est une estimation : elle est souvent légèrement inférieure à la moyenne officielle, ou simplement différente.",
            ),
        ),
        MethodSection(
            "D'où vient la moyenne générale",
            listOf(
                "- 1. Officielle : le champ moyenneGenerale de la période, ou celui d'ensembleMatieres quand la période n'en a pas.",
                "- 2. Sinon, calculée à partir des matières : moyenne des moyennes officielles de matière de la période, pondérée par le coefficient de la matière (1 quand il est inconnu ou nul).",
                "- 3. Sinon, calculée à partir des notes : toutes les notes de la période, pondérées par leur coefficient, sans les coefficients de matière.",
                "- 4. Sinon, l'application affiche « – ».",
            ),
        ),
        MethodSection(
            "D'où vient la moyenne d'une matière",
            listOf(
                "Si ÉcoleDirecte donne la moyenne de la matière pour la période, elle est recopiée (officielle).",
                "Sinon, l'application calcule la moyenne pondérée des notes de la matière dans la période (calculée par l'app).",
            ),
        ),
        MethodSection(
            "Comment l'application calcule",
            listOf(
                "- Chaque note est ramenée sur 20 : valeur ÷ barème × 20 (un barème absent vaut 20).",
                "- Chaque note est multipliée par son coefficient (1 quand il est absent) ; on additionne, puis on divise par la somme des coefficients.",
                "- Ne comptent pas : les absences et valeurs non numériques (Abs, Disp, NE, NN, NR…), les notes que l'établissement marque « non significatives » et les notes de coefficient nul.",
                "- Une note sans période connue n'entre dans le calcul d'aucune période.",
                "- Aucun zéro n'est inventé et aucun arrondi n'est fait en cours de calcul ; seul l'affichage est arrondi au dixième.",
            ),
        ),
        MethodSection(
            "Le choix de la période",
            listOf(
                "Par défaut, la période affichée est celle qui contient la date du jour et qui a des données ; à défaut la période marquée « en cours », puis la dernière commencée qui a des données.",
                "La période « Année » n'est pas proposée dans les puces : on choisit un trimestre ou un semestre.",
                "Toucher une puce change la période affichée sans nouvelle demande à ÉcoleDirecte : le bulletin est déjà là.",
            ),
        ),
        MethodSection(
            "Pourquoi la moyenne calculée peut être plus basse ou différente",
            listOf(
                "- Les coefficients de matière ne sont pas toujours visibles : l'établissement peut pondérer autrement que l'application.",
                "- Des notes de l'établissement ne sont pas visibles : devoirs pas encore publiés, notes masquées, évaluations par compétences.",
                "- Les arrondis peuvent différer : l'établissement arrondit parfois les moyennes de matière avant de les combiner.",
                "- Les périodes ne sont pas toutes connues de l'application : une période absente de la réponse n'a ni moyenne ni notes.",
                "- L'établissement peut appliquer des règles propres (note éliminée, bonus, option) que l'application ne connaît pas.",
            ),
        ),
        MethodSection(
            "Tendance, courbes et comparaison à la classe",
            listOf(
                "- La flèche de tendance compare la moyenne générale de la période affichée à celle de la dernière période précédente qui en a une, à partir d'un écart de 0,05.",
                "- La courbe par période relie les moyennes générales fournies par ÉcoleDirecte ; une période sans moyenne officielle n'a pas de point.",
                "- La courbe d'une matière est calculée par l'application : moyenne pondérée cumulée de ses notes datées, toutes périodes confondues, après chaque nouvelle note.",
                "- La moyenne de la classe est recopiée d'ÉcoleDirecte. L'écart d'une note à la classe est la note moins la moyenne de classe à ce devoir, dans le barème du devoir.",
            ),
        ),
        MethodSection(
            "Le simulateur",
            listOf(
                "Il utilise les notes de la matière pour la période affichée, avec les règles ci-dessus : c'est une estimation fondée sur ce que l'application voit, pas sur la moyenne officielle.",
                "« Quelle note pour atteindre X » : (X × (somme des coefficients + coefficient de l'évaluation) − total des points) ÷ coefficient de l'évaluation, arrondi au dixième supérieur.",
                "Si le résultat dépasse 20, l'objectif est hors d'atteinte avec une seule évaluation ; s'il est nul ou négatif, il est déjà acquis.",
            ),
        ),
        MethodSection(
            "En cas de doute",
            listOf(
                "La moyenne du bulletin de l'établissement fait foi. Une différence avec l'application n'est pas forcément un bug ; si une moyenne « officielle » te paraît fausse, signale-le depuis Plus, « Signaler un problème ».",
            ),
        ),
    )
}
