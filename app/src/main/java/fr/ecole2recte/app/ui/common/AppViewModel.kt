package fr.ecole2recte.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ecole2recte.app.AppContainer
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.session.ActiveProfile

/** Profil dont les données sont affichées ; change quand on change de compte ou d'élève. */
val LocalActiveProfile = staticCompositionLocalOf<ActiveProfile?> { null }

/** Dépôt de données du profil actif. */
@Composable
fun rememberSchoolRepository(): SchoolRepository {
    val container = (LocalContext.current.applicationContext as Ecole2recteApplication).container
    val profile = LocalActiveProfile.current
    return remember(profile?.key) { container.schoolRepositoryFor(profile) }
}

/** Crée (ou retrouve) un ViewModel en lui donnant le conteneur de dépendances. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val application = LocalContext.current.applicationContext as Ecole2recteApplication
    return viewModel(factory = viewModelFactory { initializer { create(application.container) } })
}

class HomeViewModel(repository: SchoolRepository) : LoadingViewModel<HomeData>(repository::home)

class ScheduleViewModel(repository: SchoolRepository) : LoadingViewModel<ScheduleWeek>(repository::schedule)

class HomeworkViewModel(repository: SchoolRepository) : LoadingViewModel<List<Homework>>(repository::homework)

class GradesViewModel(repository: SchoolRepository) : LoadingViewModel<GradeOverview>(repository::grades)

class MessagesViewModel(repository: SchoolRepository) : LoadingViewModel<List<MessagePreview>>(repository::messages)
