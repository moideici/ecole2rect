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

    /** Le prochain cours, sans le reste de l'accueil (la synchro et les widgets n'ont pas besoin de la vue famille). */
    suspend fun nextClass(): NextClass? = home().nextClass
    suspend fun schedule(): ScheduleWeek
    /** Les devoirs à venir, faits ou non (les faits sont marqués). */
    suspend fun homework(): List<Homework>

    /** Énoncé complet et pièces jointes d'un devoir. */
    suspend fun homeworkDetail(homework: Homework): HomeworkDetail

    /**
     * Coche ou décoche un devoir sur l'appareil, puis, seulement si l'utilisateur l'a autorisé dans
     * Plus, chez ÉcoleDirecte (une seule écriture, jamais rejouée toute seule).
     */
    suspend fun setHomeworkDone(id: String, done: Boolean): HomeworkWrite
    suspend fun grades(): GradeOverview

    /** Les notes d'une période du bulletin ; `null` : la période en cours. Sans nouvelle requête (même bulletin). */
    suspend fun grades(periodCode: String?): GradeOverview = grades()
    suspend fun messages(): List<MessagePreview>
    suspend fun badges(): Badges

    /** Absences, retards, sanctions et encouragements. */
    suspend fun schoolLife(): SchoolLifeSummary

    /** Une page de la boîte de réception (0 = la plus récente). */
    suspend fun messagesPage(page: Int): MessagesPage

    /** Le message ouvert. Côté ÉcoleDirecte, l'ouvrir le marque lu. */
    suspend fun messageDetail(id: String): MessageDetail

    /** Les personnes à qui l'on peut écrire. Seulement si l'envoi de messages est autorisé dans Plus. */
    suspend fun recipients(): List<Recipient> = emptyList()

    /**
     * Envoie un message. **Écriture**, jamais rejouée : exige `confirmed` (l'utilisateur a relu les
     * destinataires, l'objet et le texte et touché « Envoyer ») et l'autorisation donnée dans Plus.
     */
    suspend fun sendMessage(draft: MessageDraft, confirmed: Boolean): Unit =
        throw UnsupportedOperationException("L'envoi n'est pas disponible ici.")

    /** Télécharge une pièce jointe (lecture seule). Appelé seulement après confirmation de l'utilisateur. */
    suspend fun downloadAttachment(attachment: Attachment): AttachmentFile =
        throw UnsupportedOperationException("Le téléchargement n'est pas disponible ici.")
}
