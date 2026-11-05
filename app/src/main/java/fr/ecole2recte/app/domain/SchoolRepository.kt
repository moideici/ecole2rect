package fr.ecole2recte.app.domain

/**
 * Source des données scolaires affichées par les écrans.
 * Aujourd'hui : [fr.ecole2recte.app.data.demo.DemoSchoolRepository]. Le lot 7 branchera
 * ÉcoleDirecte et le lot 8 ajoutera le cache, sans toucher aux écrans.
 */
interface SchoolRepository {
    suspend fun home(): HomeData
    suspend fun schedule(): ScheduleWeek
    suspend fun homework(): List<Homework>
    suspend fun grades(): GradeOverview
    suspend fun messages(): List<MessagePreview>
    suspend fun badges(): Badges
}
