package fr.ecole2recte.app.ui.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.ui.common.rememberReduceMotion
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Une page de la présentation : un titre, une phrase d'accroche et quelques points. */
data class OnboardingPage(val title: String, val lead: String, val points: List<String>)

/** Le texte de la présentation de trois pages, rejouable depuis Plus > À propos. */
object Onboarding {
    const val SKIP = "Passer"
    const val NEXT = "Suivant"
    const val DONE = "C'est parti"
    const val CLOSE = "Fermer"

    val pages: List<OnboardingPage> = listOf(
        OnboardingPage(
            "L'essentiel d'un coup d'œil",
            "Ce que tu peux faire avec ecole2recte.",
            listOf(
                "Accueil : ton prochain cours, ta journée, les devoirs pour demain et ce qu'il faut savoir.",
                "Agenda : l'emploi du temps en jour ou en semaine (pince pour zoomer) et les devoirs à faire.",
                "Notes : moyennes, évolution et simulateur « pour atteindre X ». Messages : lus, non lus, recherche.",
                "Tire vers le bas pour rafraîchir ; la barre « Rechercher » fouille ce qui est déjà enregistré.",
            ),
        ),
        OnboardingPage(
            "Notifications, widgets, exports et réglages",
            "Tout est facultatif et se règle dans Plus.",
            listOf(
                "Notifications : cours annulé ou déplacé, nouvelle note, message, devoirs. Éteintes tant que tu ne les actives pas, jamais plus d'une vérification toutes les 4 heures.",
                "Widgets : pose-les sur l'écran d'accueil (appui long sur le fond d'écran), ils se mettent à jour à l'ouverture de l'app.",
                "Exports : l'emploi du temps en PDF ou en calendrier (.ics) depuis l'agenda.",
                "Réglages : cinq thèmes de couleurs, mode sombre ou noir, taille du texte, verrouillage par empreinte.",
            ),
        ),
        OnboardingPage(
            "Signalement et vie privée",
            "Ton identifiant ne quitte pas ce téléphone.",
            listOf(
                "Identifiant et mot de passe restent chiffrés ici et ne servent qu'à te connecter à ÉcoleDirecte. Aucune télémétrie, aucun serveur intermédiaire.",
                "L'application lit seulement : rien n'est écrit chez ÉcoleDirecte sans que tu l'actives toi-même.",
                "Un problème ? Plus > Signaler un problème prépare un rapport sans donnée personnelle ; tu le relis et tu choisis où il part.",
                "Quelqu'un regarde ton écran ? L'œil en haut masque les notes (et les messages en option).",
            ),
        ),
    )

    val pageCount: Int get() = pages.size

    /** La page suivante, ou `null` après la dernière (la présentation se termine). */
    fun next(page: Int): Int? = (page + 1).takeIf { it < pageCount }

    fun isLast(page: Int): Boolean = page >= pageCount - 1

    /** « Page 2 sur 3 : Notifications… », pour les lecteurs d'écran. */
    fun description(page: Int): String = "Page ${page + 1} sur $pageCount : ${pages[page].title}"
}

/** La présentation ne s'affiche qu'une fois, après l'avis légal, devant la saisie d'identifiants. */
fun needsOnboarding(noticeAccepted: Boolean, seen: Boolean, loginVisible: Boolean): Boolean =
    loginVisible && noticeAccepted && !seen

private val pageIcons: List<ImageVector> = listOf(Icons.Outlined.Home, Icons.Outlined.Notifications, Icons.Outlined.Shield)

/**
 * Présentation de trois pages à balayer, avec « Passer ». Les mouvements (défilement animé, points)
 * sont supprimés quand l'utilisateur a demandé de réduire les animations.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, closeLabel: String = Onboarding.DONE) {
    val reduceMotion = rememberReduceMotion()
    val pager = rememberPagerState(pageCount = { Onboarding.pageCount })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    Surface(Modifier.fillMaxSize(), color = AppTheme.colors.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 40.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    if (Onboarding.isLast(page)) Onboarding.CLOSE else Onboarding.SKIP,
                    modifier = Modifier.clickable(onClick = onFinish).padding(12.dp),
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val content = Onboarding.pages[index]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).semantics { contentDescription = Onboarding.description(index) },
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).background(AppTheme.colors.accentFill.copy(alpha = .15f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(pageIcons[index], contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(34.dp))
                    }
                    Text(content.title, color = AppTheme.colors.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                    Text(content.lead, color = AppTheme.colors.muted, fontSize = 16.sp)
                    content.points.forEach { point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(7.dp).background(AppTheme.colors.accent, CircleShape))
                            Text(point, color = AppTheme.colors.ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = Onboarding.description(page) },
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(Onboarding.pageCount) { index ->
                    val color by animateColorAsState(
                        if (index == page) AppTheme.colors.accent else AppTheme.colors.muted.copy(alpha = .35f),
                        if (reduceMotion) snap() else tween(200),
                        label = "dot",
                    )
                    Box(Modifier.padding(4.dp).size(if (index == page) 10.dp else 8.dp).background(color, CircleShape))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val next = Onboarding.next(page)
                    if (next == null) onFinish()
                    else scope.launch { if (reduceMotion) pager.scrollToPage(next) else pager.animateScrollToPage(next) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (Onboarding.isLast(page)) closeLabel else Onboarding.NEXT) }
        }
    }
}
