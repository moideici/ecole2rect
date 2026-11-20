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
                "Le calcul est le même que sur le site ecole2recte : pour les mêmes notes, l'application et le site affichent les mêmes moyennes.",
                "Une moyenne est dite « officielle » quand ÉcoleDirecte la fournit et qu'elle compte déjà toutes les notes connues : l'application la recopie telle quelle.",
                "Une moyenne « recalculée » remplace une moyenne officielle calculée avant les dernières notes saisies : l'application affiche la sienne, à jour, et rappelle la valeur publiée.",
                "Une moyenne « calculée » est celle que l'application calcule quand ÉcoleDirecte n'en fournit pas : c'est une estimation, qui peut différer du bulletin.",
            ),
        ),
        MethodSection(
            "D'où vient la moyenne d'une matière",
            listOf(
                "- Sans moyenne publiée, l'application calcule la moyenne pondérée des notes de la matière dans la période (calculée).",
                "- Avec une moyenne publiée, l'application la compare à son calcul. Si l'écart ne dépasse pas 0,055 (un arrondi), la moyenne publiée est gardée (officielle).",
                "- Sinon, l'application retire les notes une à une, de la plus récente à la plus ancienne : si la moyenne publiée réapparaît, elle date d'avant ces notes et l'application affiche la sienne (recalculée). Une note sans date arrête la recherche.",
                "- Si l'écart reste inexpliqué (coefficients cachés, règle de l'établissement), la moyenne publiée est gardée : le bulletin a raison.",
                "- Une matière qui a des notes mais aucune moyenne publiée est ajoutée au relevé, et comptée dans la moyenne générale.",
            ),
        ),
        MethodSection(
            "D'où vient la moyenne générale",
            listOf(
                "- 1. Le calcul de l'application : la moyenne des moyennes de matière (officielles, recalculées ou calculées), pondérée par le coefficient de la matière. Les sous-matières ne comptent pas : elles le sont déjà dans leur matière.",
                "- 2. Sans aucune moyenne de matière, la moyenne pondérée de toutes les notes de la période.",
                "- 3. La moyenne générale publiée (le champ moyenneGenerale de la période, ou celui d'ensembleMatieres) est affichée telle quelle (officielle), sauf si une matière au moins a été recalculée : elle date alors d'avant ces notes, et l'application affiche son calcul (recalculée).",
                "- 4. Sans moyenne publiée, l'application affiche son calcul (calculée) ; sans rien à calculer, « – ».",
            ),
        ),
        MethodSection(
            "Comment l'application calcule",
            listOf(
                "- Chaque note est ramenée sur 20 : valeur × 20 ÷ barème (un barème absent vaut 20). Une note dont le barème est nul ou négatif ne compte pas.",
                "- Chaque note est multipliée par son coefficient ; un coefficient absent, nul ou négatif vaut 1. On additionne, puis on divise par la somme des coefficients. Il en va de même pour les coefficients de matière.",
                "- Ne comptent pas : les absences et valeurs non numériques (Abs, Disp, NE, NN, NR…) et les notes que l'établissement marque « non significatives ».",
                "- Une note sans période connue n'entre dans le calcul d'aucune période.",
                "- Aucun zéro n'est inventé et aucun arrondi n'est fait en cours de calcul ; seul l'affichage est arrondi au centième.",
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
                "- La courbe par période relie les moyennes générales de chaque période, obtenues avec les règles ci-dessus ; une période sans moyenne n'a pas de point.",
                "- La courbe d'une matière est calculée par l'application : un point par date de note dans la période, la moyenne pondérée cumulée des notes jusqu'à cette date.",
                "- La moyenne de la classe est recopiée d'ÉcoleDirecte. L'écart d'une note à la classe est la note moins la moyenne de classe à ce devoir, dans le barème du devoir.",
            ),
        ),
        MethodSection(
            "Le simulateur",
            listOf(
                "Il utilise les notes de la matière pour la période affichée, avec les règles ci-dessus : c'est une estimation fondée sur ce que l'application voit, pas sur la moyenne officielle.",
                "« Quelle note pour atteindre X » : (X × (somme des coefficients + coefficient de l'évaluation) − total des points) ÷ coefficient de l'évaluation, affiché au centième.",
                "Si le résultat dépasse 20, l'objectif est hors d'atteinte avec une seule évaluation ; s'il est négatif, il est déjà acquis.",
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
