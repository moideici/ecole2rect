package fr.ecole2recte.app.core.format

import java.text.Normalizer

/**
 * Petites transformations de texte partagées par le parsing et l'interface.
 *
 * ÉcoleDirecte renvoie du HTML encodé en base64 pour les devoirs et les messages :
 * l'application ne peut pas l'afficher tel quel, et n'a pas besoin d'un moteur de rendu
 * complet pour du texte, quelques listes et des sauts de ligne.
 */
object Text {

    private val BLOCK_TAGS = setOf(
        "p", "div", "br", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "blockquote",
    )

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë",
        "agrave" to "à", "acirc" to "â", "ccedil" to "ç", "ugrave" to "ù", "ucirc" to "û",
        "ocirc" to "ô", "icirc" to "î", "iuml" to "ï", "oelig" to "œ", "laquo" to "«",
        "raquo" to "»", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“",
        "rdquo" to "”", "ndash" to "–", "mdash" to "—", "euro" to "€", "deg" to "°",
    )

    /** HTML → texte lisible : les blocs deviennent des lignes, le reste disparaît. */
    fun fromHtml(html: String): String {
        if (html.isBlank()) return ""
        val output = StringBuilder(html.length)
        var index = 0
        while (index < html.length) {
            val character = html[index]
            if (character == '<') {
                val close = html.indexOf('>', index)
                if (close < 0) {
                    // Un « < » isolé n'est pas une balise : c'est du texte.
                    output.append(character)
                    index++
                    continue
                }
                val raw = html.substring(index + 1, close).trim()
                val closing = raw.startsWith("/")
                val tag = raw.trimStart('/').substringBefore(' ').substringBefore('/').lowercase()
                if (tag in BLOCK_TAGS) output.append('\n')
                // Une puce n'a de sens qu'à l'ouverture : « </li> » ne doit rien ajouter.
                if (tag == "li" && !closing) output.append("• ")
                index = close + 1
                continue
            }
            if (character == '&') {
                val end = html.indexOf(';', index)
                if (end in (index + 1)..(index + 12)) {
                    val entity = html.substring(index + 1, end)
                    val decoded = decodeEntity(entity)
                    if (decoded != null) {
                        output.append(decoded)
                        index = end + 1
                        continue
                    }
                }
            }
            output.append(character)
            index++
        }
        return collapseBlankLines(output.toString())
    }

    private fun decodeEntity(entity: String): String? {
        ENTITIES[entity]?.let { return it }
        if (entity.startsWith("#")) {
            val code = entity.drop(1).let { raw ->
                if (raw.startsWith("x", ignoreCase = true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
            }
            if (code != null && code in 1..0x10FFFF) return String(Character.toChars(code))
        }
        return null
    }

    private fun collapseBlankLines(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\u00A0', ' ')
        .split('\n')
        .map { it.trim() }
        // Le HTML d'ÉcoleDirecte empile les balises de bloc : sans ce filtre, chaque
        // paragraphe serait séparé par deux ou trois lignes vides à l'écran.
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .trim()

    /** Première ligne utile d'un contenu, pour les aperçus de cartes et de listes. */
    fun preview(value: String, maxLength: Int = 120): String {
        val flat = value.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= maxLength) flat else flat.take(maxLength - 1).trimEnd() + "…"
    }

    /** Minuscule sans accent ni ponctuation : sert de clé stable pour les matières. */
    fun normalizeKey(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), "")

    /** « MATHEMATIQUES » ou « mathematiques » → « Mathématiques » quand c'est possible. */
    fun titleCase(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.any { it.isLowerCase() }) return trimmed
        return trimmed.split(' ').joinToString(" ") { word ->
            when {
                word.length <= 1 -> word
                word.contains('-') -> word.split('-').joinToString("-") { it.capitalizeFirst() }
                else -> word.capitalizeFirst()
            }
        }
    }

    private fun String.capitalizeFirst(): String =
        if (isEmpty()) this else first().uppercase() + drop(1).lowercase()

    /**
     * Noms de matières lisibles.
     *
     * L'API renvoie souvent « MATHEMATIQUES », en capitales et sans accents. Les afficher
     * tels quels donnerait une interface qui crie et qui écorche le français ; ce
     * dictionnaire rend leur orthographe aux matières les plus courantes, et le reste passe
     * par une simple mise en casse de titre.
     */
    fun prettySubject(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return trimmed
        SUBJECT_NAMES[normalizeKey(trimmed)]?.let { return it }
        return titleCase(trimmed)
    }

    private val SUBJECT_NAMES = mapOf(
        "mathematiques" to "Mathématiques",
        "maths" to "Maths",
        "francais" to "Français",
        "histoiregeographie" to "Histoire-Géographie",
        "histoiregeo" to "Histoire-Géo",
        "histgeo" to "Histoire-Géo",
        "philosophie" to "Philosophie",
        "philo" to "Philosophie",
        "anglais" to "Anglais",
        "anglaislv1" to "Anglais LV1",
        "anglaislv2" to "Anglais LV2",
        "espagnol" to "Espagnol",
        "espagnollv2" to "Espagnol LV2",
        "allemand" to "Allemand",
        "italien" to "Italien",
        "latin" to "Latin",
        "grec" to "Grec",
        "physiquechimie" to "Physique-Chimie",
        "physique" to "Physique",
        "chimie" to "Chimie",
        "svt" to "SVT",
        "sciencesdelavieetdelaterre" to "Sciences de la vie et de la Terre",
        "sciencesnumeriquesettechnologie" to "Sciences numériques et technologie",
        "snt" to "SNT",
        "numeriqueetsciencesinformatiques" to "Numérique et sciences informatiques",
        "nsi" to "NSI",
        "scienceseconomiquesetsociales" to "Sciences économiques et sociales",
        "ses" to "SES",
        "eps" to "EPS",
        "educationphysiqueetsportive" to "Éducation physique et sportive",
        "emc" to "EMC",
        "enseignementmoraletcivique" to "Enseignement moral et civique",
        "hlp" to "Humanités, littérature et philosophie",
        "humaniteslitteraturephilosophie" to "Humanités, littérature et philosophie",
        "technologie" to "Technologie",
        "artsplastiques" to "Arts plastiques",
        "educationmusicale" to "Éducation musicale",
        "musique" to "Musique",
        "viedeclasse" to "Vie de classe",
        "accompagnementpersonnalise" to "Accompagnement personnalisé",
        "orientation" to "Orientation",
    )

    /** Deux lettres pour une pastille de matière ou un avatar. */
    fun initials(value: String): String {
        val words = value.split(' ', '-', '\'').filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }
}
