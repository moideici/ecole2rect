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

    /** Au-delà, les lignes deviennent illisibles et le gabarit n'a plus de vues libres. */
    const val MAX_LINES = 8

    fun layout(kind: WidgetKind, size: WidgetSize): WidgetLayout =
        WidgetLayout(shape = shapeFor(kind, size), maxLines = maxLines(size))

    /**
     * Le gabarit qui convient à la place disponible.
     *
     * La semaine est le seul contenu qui exige de la largeur : six colonnes de chiffres dans
     * deux cellules ne se lisent pas. En dessous, elle redevient une liste de jours.
     */
    fun shapeFor(kind: WidgetKind, size: WidgetSize): WidgetShape {
        val columns = size.columns
        val rows = size.rows
        return when {
            columns >= 3 && rows >= 2 && kind.natural == WidgetShape.WEEK -> WidgetShape.WEEK
            rows >= 2 && columns >= 2 -> WidgetShape.LIST
            columns >= 2 -> WidgetShape.ROW
            // Une seule colonne, si haute soit-elle : une ligne « Mathématiques · C011 ·
            // 10:15 » n'y tiendrait pas. La tuile, elle, sait s'étirer.
            else -> WidgetShape.TILE
        }
    }

    /**
     * Combien de lignes tiennent dans la hauteur donnée.
     *
     * L'en-tête prend une soixantaine de dp, chaque ligne une trentaine. Mieux vaut une ligne
     * de moins qu'une ligne coupée en deux par le bord du widget.
     */
    fun maxLines(size: WidgetSize): Int =
        ((size.heightDp - 62) / 30).coerceIn(1, MAX_LINES)
}
