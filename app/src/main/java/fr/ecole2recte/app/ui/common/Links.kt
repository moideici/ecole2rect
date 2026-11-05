package fr.ecole2recte.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import fr.ecole2recte.app.ui.theme.AppTheme

/** Un lien trouvé dans un texte : où il est et où il mène. */
data class LinkSpan(val start: Int, val end: Int, val url: String)

private val URL = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")
private val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
private val PHONE = Regex("""(?<![\d+])(?:\+33\s?|0)[1-9](?:[\s.-]?\d{2}){4}(?!\d)""")

/**
 * Repère les adresses web, les courriels et les numéros de téléphone français d'un texte.
 * La ponctuation qui termine une phrase (« voir https://… . ») n'est pas dans le lien.
 */
fun findLinks(text: String): List<LinkSpan> {
    val found = mutableListOf<LinkSpan>()
    for (match in URL.findAll(text)) {
        var end = match.range.last + 1
        while (end > match.range.first && text[end - 1] in ".,;:!?)]}»") end--
        val raw = text.substring(match.range.first, end)
        found += LinkSpan(match.range.first, end, if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw)
    }
    for (match in EMAIL.findAll(text)) {
        if (found.none { it.start < match.range.last + 1 && match.range.first < it.end }) {
            found += LinkSpan(match.range.first, match.range.last + 1, "mailto:${match.value}")
        }
    }
    for (match in PHONE.findAll(text)) {
        if (found.none { it.start < match.range.last + 1 && match.range.first < it.end }) {
            found += LinkSpan(match.range.first, match.range.last + 1, "tel:" + match.value.filter { it.isDigit() || it == '+' })
        }
    }
    return found.sortedBy { it.start }
}

/** Le texte avec ses liens cliquables : un toucher ouvre le navigateur, la messagerie ou le téléphone. */
@Composable
fun linkified(text: String): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = AppTheme.colors.accent, textDecoration = TextDecoration.Underline))
    return remember(text, style) {
        buildAnnotatedString {
            var cursor = 0
            for (link in findLinks(text)) {
                if (link.start < cursor) continue
                append(text.substring(cursor, link.start))
                withLink(LinkAnnotation.Url(link.url, style)) { append(text.substring(link.start, link.end)) }
                cursor = link.end
            }
            append(text.substring(cursor))
        }
    }
}
