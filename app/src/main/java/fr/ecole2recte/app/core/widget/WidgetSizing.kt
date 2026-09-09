package fr.ecole2recte.app.core.widget

/**
 * La place réellement accordée par le lanceur, en dp.
 *
 * Un widget Android n'a pas de taille fixe : l'utilisateur l'étire, le lanceur décide de sa
 * grille, et deux téléphones ne donnent pas les mêmes dimensions pour « 2×2 ». Un widget
 * dessiné pour une seule taille est donc vide sur un grand écran et tronqué sur un petit.
 */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {

    /**
     * Cellules approximatives. La formule est celle de Google : une cellule vaut 70 dp, moins
     * les marges que le lanceur reprend sur les bords.
     */
    val columns: Int get() = cells(widthDp)
    val rows: Int get() = cells(heightDp)

    private fun cells(sizeDp: Int): Int = ((sizeDp + 30) / 70).coerceAtLeast(1)

    companion object {
        /** Taille par défaut quand le lanceur ne dit rien : la plus petite qui existe. */
        val SMALL = WidgetSize(40, 40)
    }
}

/**
 * Ce qu'un widget affiche à une taille donnée.
 *
 * L'idée tient en une phrase : **plus il y a de place, plus il y a de contenu**. La même
 * tuile « moyenne » montre un chiffre en 1×1, la moyenne et la tendance en 2×1, et le détail
 * par matière dès qu'elle a deux lignes de haut. Ce n'est pas un habillage : c'est le contenu
 * lui-même qui change, sinon un widget agrandi n'est qu'un widget avec du vide autour.
 */
data class WidgetLayout(val shape: WidgetShape, val maxLines: Int)

object WidgetSizing {

    /**
     * Au-delà, on empile des lignes au lieu de les rendre lisibles.
     *
     * Six et non huit depuis que les lignes portent deux niveaux de texte : trois devoirs
     * complets dans un 2×2 valent mieux que six titres tronqués.
     */
    const val MAX_LINES = 6

    fun layout(kind: WidgetKind, size: WidgetSize, proportional: Boolean = true): WidgetLayout =
        WidgetLayout(shape = shapeFor(kind, size, proportional), maxLines = maxLines(size))

    /**
     * Le gabarit qui convient à la place disponible.
     *
     * La semaine est le seul contenu qui exige de la largeur : six colonnes dans deux
     * cellules ne se lisent pas. En dessous, elle redevient une liste de jours.
     *
     * [proportional] dit si l'on peut donner une hauteur exacte à une vue distante. Ce n'est
     * possible qu'à partir d'Android 12 ; en deçà, les grilles redeviennent des listes. Une
     * liste régulière est un moindre mal devant une grille dont tous les blocs feraient la
     * même hauteur — ce qui mentirait sur la durée des cours.
     */
    fun shapeFor(kind: WidgetKind, size: WidgetSize, proportional: Boolean = true): WidgetShape {
        val columns = size.columns
        val rows = size.rows
        return when {
            columns >= 3 && rows >= 2 && kind.natural == WidgetShape.WEEK && proportional -> WidgetShape.WEEK
            columns >= 2 && rows >= 2 && kind.timetable && proportional -> WidgetShape.GRID
            rows >= 2 && columns >= 2 -> WidgetShape.LIST
            // Trois chiffres alignés demandent de la largeur, pas de la hauteur : c'est
            // exactement la place d'un 2×1, et rien de plus.
            columns >= 2 && kind.natural == WidgetShape.HOURS -> WidgetShape.HOURS
            columns >= 2 -> WidgetShape.ROW
            // Une seule colonne, si haute soit-elle : une ligne « Mathématiques · C011 ·
            // 10:15 » n'y tiendrait pas. La tuile, elle, sait s'étirer.
            else -> WidgetShape.TILE
        }
    }

    /** Ce que prennent la marge du gabarit et son en-tête, avant la première ligne. */
    private const val HEADER_DP = 58

    /**
     * Hauteur d'une ligne.
     *
     * Une ligne porte désormais deux niveaux — un titre de treize points et une précision de
     * neuf — et il faut le compter : garder l'ancienne mesure de 23 dp promettait six lignes
     * là où trois tiennent, et le widget les aurait rognées par le bas.
     */
    private const val LINE_DP = 32

    /**
     * Combien de lignes tiennent dans la hauteur donnée.
     *
     * Les deux constantes sont mesurées sur le gabarit, pas devinées : une ligne trop haute
     * dans le calcul, et le widget s'arrête à quatre cours en laissant un tiers de sa
     * surface blanche — ce qui est exactement ce qu'on lui reproche.
     *
     * Mieux vaut tout de même une ligne de moins qu'une ligne coupée en deux par le bord :
     * la division entière arrondit vers le bas, et c'est voulu.
     */
    fun maxLines(size: WidgetSize): Int =
        ((size.heightDp - HEADER_DP) / LINE_DP).coerceIn(1, MAX_LINES)

    /** La hauteur qui reste à une grille sous l'en-tête, en dp. */
    fun contentHeightDp(size: WidgetSize): Int = (size.heightDp - HEADER_DP).coerceAtLeast(0)

    /** Et celle d'une colonne de semaine, qui perd en plus le nom du jour. */
    fun columnHeightDp(size: WidgetSize): Int =
        (size.heightDp - HEADER_DP - DAY_NAME_DP).coerceAtLeast(0)

    /** Le nom du jour, en tête de chaque colonne de la semaine. */
    private const val DAY_NAME_DP = 16
}
