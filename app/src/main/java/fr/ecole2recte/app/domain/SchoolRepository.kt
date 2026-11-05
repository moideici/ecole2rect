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
    /** Les devoirs à venir, faits ou non (les faits sont marqués). */
    suspend fun homework(): List<Homework>

    /** Énoncé complet et pièces jointes d'un devoir. */
    suspend fun homeworkDetail(homework: Homework): HomeworkDetail

    /** Coche ou décoche un devoir. Pour l'instant sur l'appareil seulement. */
    suspend fun setHomeworkDone(id: String, done: Boolean)
    suspend fun grades(): GradeOverview
    suspend fun messages(): List<MessagePreview>
    suspend fun badges(): Badges

    /** Absences, retards, sanctions et encouragements. */
    suspend fun schoolLife(): SchoolLifeSummary

    /** Une page de la boîte de réception (0 = la plus récente). */
    suspend fun messagesPage(page: Int): MessagesPage

    /** Le message ouvert. Côté ÉcoleDirecte, l'ouvrir le marque lu. */
    suspend fun messageDetail(id: String): MessageDetail
}
