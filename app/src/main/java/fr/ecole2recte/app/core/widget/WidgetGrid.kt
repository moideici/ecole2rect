package fr.ecole2recte.app.core.widget

/**
 * Le partage de la hauteur entre les blocs d'une grille.
 *
 * C'est le calcul qui répond au reproche fait aux anciens widgets : un 2×2 rempli de quatre
 * lignes collées en haut n'est qu'un 2×1 avec du vide dessous. Ici la hauteur est le temps —
 * un cours de deux heures occupe deux fois la place d'un cours d'une heure — et la grille
 * remplit toute la carte, trous compris.
 *
 * Deux garde-fous font tout le sérieux de la chose. Un bloc porteur de texte ne descend jamais
 * sous [MIN_BLOCK_DP], sinon un intercours de vingt minutes afficherait une matière coupée en
 * deux ; et le total ne dépasse jamais la place accordée, sinon le dernier cours de la journée
 * serait rogné par le bord du widget.
 *
 * Le calcul vit ici, sans rien d'Android, parce que c'est exactement le genre de règle qu'on
 * ne peut pas vérifier à l'œil sur un écran d'accueil.
 */
object WidgetGrid {

    /** En deçà, un bloc n'a plus la hauteur d'une ligne de texte. */
    const val MIN_BLOCK_DP = 14

    /** Au-delà de cette hauteur, un bloc a la place d'un second niveau de texte. */
    private const val SECOND_LEVEL_DP = 30

    /** Et d'un troisième : l'horaire, la salle, puis le professeur. */
    private const val THIRD_LEVEL_DP = 46

    /**
     * Ramène une journée au nombre d'emplacements du gabarit.
     *
     * `RemoteViews` ne sait pas répéter une vue : le gabarit a un nombre fixe de blocs. Quand
     * la journée en compte davantage, on **absorbe les espaces nus** dans le bloc voisin, du
     * plus court au plus long. Les minutes ne sont pas perdues mais reversées, si bien que la
     * colonne garde exactement la même durée totale — ce qui est vital pour la semaine, où
     * six colonnes doivent rester alignées sur la même échelle.
     *
     * Si cela ne suffit pas, on coupe la fin : montrer le début d'une journée en entier vaut
     * mieux que la montrer entière en bandes illisibles.
     */
    fun fit(blocks: List<WidgetBlock>, slots: Int): List<WidgetBlock> {
        if (slots <= 0) return emptyList()
        var current = blocks
        while (current.size > slots) {
            val index = current.withIndex()
                .filter { (position, block) -> block.blank && (position > 0 || current.size > 1) }
                .minByOrNull { it.value.minutes }
                ?.index ?: break
            current = absorb(current, index)
        }
        return if (current.size <= slots) current else current.take(slots)
    }

    /** Reverse les minutes d'un espace nu au bloc précédent, ou au suivant s'il ouvre la liste. */
    private fun absorb(blocks: List<WidgetBlock>, index: Int): List<WidgetBlock> {
        val result = blocks.toMutableList()
        val removed = result.removeAt(index)
        val target = if (index > 0) index - 1 else 0
        if (result.isEmpty()) return listOf(removed)
        result[target] = result[target].copy(minutes = result[target].minutes + removed.minutes)
        return result
    }

    /**
     * Les hauteurs, en dp, dans l'ordre des blocs.
     *
     * Un bloc rendu à zéro n'est pas dessiné : c'est le sort des espaces nus quand la place
     * manque, et c'est voulu — mieux vaut perdre un intercours de cinq minutes que la
     * lisibilité du cours qui le suit.
     */
    fun heights(
        blocks: List<WidgetBlock>,
        availableDp: Int,
        minimumDp: Int = MIN_BLOCK_DP,
    ): List<Int> {
        if (blocks.isEmpty() || availableDp <= 0) return blocks.map { 0 }
        val total = blocks.sumOf { it.minutes }.coerceAtLeast(1)

        // Première répartition, au prorata du temps.
        val raw = blocks.map { availableDp.toDouble() * it.minutes / total }

        // Les blocs porteurs de texte réclament leur minimum ; le reste se partage ce qui
        // demeure, ce qui revient à prendre la place aux espaces nus en priorité.
        val floors = blocks.mapIndexed { index, block ->
            if (block.blank) 0.0 else minOf(minimumDp.toDouble(), availableDp.toDouble())
        }
        val claimed = floors.sum()
        if (claimed >= availableDp) {
            // Trop de blocs pour la hauteur : on garde les premiers, entiers, plutôt que de
            // rendre une bouillie de bandes de six points.
            return trim(blocks, availableDp, minimumDp)
        }

        val free = availableDp - claimed
        val extra = raw.mapIndexed { index, value -> (value - floors[index]).coerceAtLeast(0.0) }
        val extraTotal = extra.sum()
        val scaled = blocks.indices.map { index ->
            floors[index] + if (extraTotal <= 0.0) 0.0 else free * extra[index] / extraTotal
        }
        return round(scaled, availableDp)
    }

    /**
     * Quand la journée ne tient pas, on la coupe franchement.
     *
     * Les premiers blocs gardent leur hauteur minimale et les suivants disparaissent : un
     * widget qui montre le début de la journée en entier est plus utile qu'un widget qui
     * montre la journée complète en bandes illisibles.
     *
     * Les espaces nus, eux, gardent leur part du temps. Ils ne portent pas de texte mais ils
     * portent l'alignement : dans une semaine, ce sont eux qui font commencer les six colonnes
     * à la même heure. Les mettre à zéro collait chaque journée en haut de sa colonne et
     * faisait mentir l'échelle commune — c'est-à-dire la seule raison d'être de ce gabarit.
     */
    private fun trim(blocks: List<WidgetBlock>, availableDp: Int, minimumDp: Int): List<Int> {
        val total = blocks.sumOf { it.minutes }.coerceAtLeast(1)
        val spacers = blocks.map { block ->
            if (block.blank) availableDp.toDouble() * block.minutes / total else 0.0
        }
        val heights = MutableList(blocks.size) { 0.0 }
        var left = availableDp - spacers.sum()
        blocks.forEachIndexed { index, block ->
            if (block.blank) {
                heights[index] = spacers[index]
                return@forEachIndexed
            }
            if (left < minimumDp) return@forEachIndexed
            heights[index] = minimumDp.toDouble()
            left -= minimumDp
        }
        return round(heights, availableDp)
    }

    /**
     * Arrondit sans perdre ni inventer de points : les décalages d'arrondi s'accumulent sur
     * le bloc suivant plutôt que de s'ajouter au total, qui doit rester égal à la place reçue.
     */
    private fun round(values: List<Double>, availableDp: Int): List<Int> {
        val heights = mutableListOf<Int>()
        var carried = 0.0
        var used = 0
        values.forEach { value ->
            val exact = value + carried
            val height = exact.toInt().coerceAtLeast(0)
            carried = exact - height
            heights += height
            used += height
        }
        // Le reliquat va au plus grand bloc : c'est là qu'un point de plus ne se voit pas.
        val remainder = availableDp - used
        if (remainder > 0) {
            val target = heights.indices.maxByOrNull { heights[it] } ?: return heights
            heights[target] = heights[target] + remainder
        }
        return heights
    }

    /**
     * Combien de blocs seront réellement dessinés dans cette hauteur.
     *
     * Sert à ne retirer de la place que les marges qui existeront : un bloc rendu à zéro est
     * masqué, et sa marge avec lui. Le calcul est le même que [heights], fait une fois de
     * plus — une poignée d'additions, contre un cours perdu au bord du widget.
     */
    fun drawnCount(blocks: List<WidgetBlock>, availableDp: Int, minimumDp: Int = MIN_BLOCK_DP): Int =
        heights(blocks, availableDp, minimumDp).count { it > 0 }

    /**
     * Combien de niveaux de texte tiennent dans un bloc de cette hauteur.
     *
     * C'est ce qui fait qu'un cours de cinquante minutes n'affiche que sa matière tandis
     * qu'un cours de deux heures montre aussi son horaire, sa salle et son professeur : le
     * bloc ne se remplit pas de ce qu'on a, mais de ce qui y tient.
     */
    fun levels(heightDp: Int): Int = when {
        heightDp >= THIRD_LEVEL_DP -> 3
        heightDp >= SECOND_LEVEL_DP -> 2
        heightDp >= MIN_BLOCK_DP -> 1
        else -> 0
    }
}
