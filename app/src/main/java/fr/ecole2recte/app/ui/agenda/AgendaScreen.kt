package fr.ecole2recte.app.ui.agenda

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import fr.ecole2recte.app.data.SchoolModule
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.ui.common.PageHeader
import fr.ecole2recte.app.ui.common.ScrollToTopOnReselect
import fr.ecole2recte.app.ui.homework.HomeworkList
import fr.ecole2recte.app.ui.schedule.ScheduleList
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

const val AGENDA_SCHEDULE = 0
const val AGENDA_HOMEWORK = 1

/**
 * Onglet Agenda : l'emploi du temps et le travail à faire sont les deux faces d'une même
 * question (« qu'est-ce que j'ai à faire ? »). On passe de l'un à l'autre en glissant.
 */
@Composable
fun AgendaScreen(page: Int, onPageChange: (Int) -> Unit) {
    val closed = LocalActiveProfile.current?.closedModules.orEmpty()
    val scheduleOpen = SchoolModule.Schedule !in closed
    val homeworkOpen = SchoolModule.Homework !in closed
    if (scheduleOpen != homeworkOpen) {
        // Un seul des deux modules est ouvert : pas de pages à glisser, on montre directement le bon.
        Column {
            PageHeader("Agenda")
            if (scheduleOpen) ScheduleList(rememberLazyListState()) else HomeworkList(rememberLazyListState())
        }
        return
    }
    val pagerState = rememberPagerState(initialPage = page) { 2 }
    val scope = rememberCoroutineScope()
    val scheduleState = rememberLazyListState()
    val homeworkState = rememberLazyListState()
    LaunchedEffect(page) {
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChange)
    }
    // Retoucher l'onglet remonte la liste visible.
    ScrollToTopOnReselect("agenda", if (pagerState.currentPage == AGENDA_SCHEDULE) scheduleState else homeworkState)
    Column {
        PageHeader("Agenda")
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = AppTheme.colors.background,
            contentColor = AppTheme.colors.accent,
        ) {
            listOf("Emploi du temps", "Travail à faire").forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    selectedContentColor = AppTheme.colors.accent,
                    unselectedContentColor = AppTheme.colors.muted,
                )
            }
        }
        HorizontalPager(pagerState) { index ->
            when (index) {
                AGENDA_SCHEDULE -> ScheduleList(scheduleState)
                else -> HomeworkList(homeworkState)
            }
        }
    }
}
