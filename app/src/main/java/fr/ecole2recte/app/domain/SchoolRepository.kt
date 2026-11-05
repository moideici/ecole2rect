package fr.ecole2recte.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Source des données scolaires affichées par les écrans.
 * Aujourd'hui : [fr.ecole2recte.app.data.demo.DemoSchoolRepository]. Le lot 7 branchera
 * ÉcoleDirecte et le lot 8 ajoutera le cache, sans toucher aux écrans.
 */
interface SchoolRepository {
    /** Âge des données : permet d'afficher « hors ligne, données du … ». */
    val freshness: StateFlow<Freshness>

    /** Oublie la mémoire à court terme : le prochain chargement interroge ÉcoleDirecte (tirer pour rafraîchir). */
    fun invalidate()

    suspend fun home(): HomeData
    suspend fun schedule(): ScheduleWeek
    suspend fun homework(): List<Homework>
    suspend fun grades(): GradeOverview
    suspend fun messages(): List<MessagePreview>
    suspend fun badges(): Badges
}
