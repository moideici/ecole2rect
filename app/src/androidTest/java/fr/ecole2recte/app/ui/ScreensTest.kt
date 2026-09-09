package fr.ecole2recte.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests d'interface des parcours principaux.
 *
 * Ils ne remplacent pas les tests du cœur : ils vérifient l'autre moitié, celle qu'aucun test
 * JVM ne voit — que les écrans se composent réellement, que les états vides s'affichent, et
 * qu'un geste déclenche bien l'action attendue.
 *
 * Ils demandent un appareil ou un émulateur : `gradle connectedDebugAndroidTest`.
 * L'intégration continue, elle, en vérifie au moins la compilation.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val now: LocalDateTime = monday.atTime(9, 51)

    private val snapshot = SchoolSnapshot(
        account = Account("4242", "E", "Martin", "Leroy", "Terminale A", "Lycée Victor Hugo"),
        lessons = listOf(
            Lesson("1", "Mathématiques", "M. Petit", "C011", monday.atTime(10, 15), monday.atTime(12, 0)),
        ),
        homework = listOf(
            HomeworkItem("901", "Philosophie", monday.plusDays(1), content = "Dissertation"),
        ),
        grades = listOf(
            Grade("5001", "Mathématiques", "DS n°1", 17.5, 20.0, 2.0, null, monday, "A001"),
        ),
    )

    @Test
    fun accueil_montre_le_prenom_et_le_prochain_cours() {
        compose.setContent {
            Ecole2recteTheme {
                HomeScreen(
                    snapshot = snapshot,
                    settings = AppSettings(),
                    sync = SyncState(),
                    now = now,
                    accounts = listOf(snapshot.account!!),
                    onNavigate = {},
                    onOpenDetail = {},
                    onRefresh = {},
                    onToggleHomework = {},
                    onSelectAccount = {},
                )
            }
        }

        compose.onNodeWithText("Bonjour Martin").assertIsDisplayed()
        compose.onNodeWithText("Mathématiques").assertIsDisplayed()
    }

    @Test
    fun devoirs_ouvre_la_fiche_au_clic() {
        var opened: HomeworkItem? = null
        compose.setContent {
            Ecole2recteTheme {
                HomeworkScreen(
                    snapshot = snapshot,
                    settings = AppSettings(),
                    sync = SyncState(),
                    now = now,
                    onOpen = { opened = it },
                    onToggle = {},
                    onRefresh = {},
                )
            }
        }

        compose.onNodeWithText("Philosophie").assertIsDisplayed()
        compose.onNodeWithText("Philosophie").performClick()

        assertEquals("901", opened?.id)
    }

    @Test
    fun emploi_du_temps_vide_propose_de_synchroniser() {
        compose.setContent {
            Ecole2recteTheme {
                TimetableScreen(
                    state = rememberTimetableState(now.toLocalDate()),
                    snapshot = SchoolSnapshot(),
                    settings = AppSettings(),
                    sync = SyncState(),
                    now = now,
                    loadingWeeks = emptySet(),
                    onLoadWeek = {},
                    onOpenLesson = {},
                    onOpenHomework = {},
                    onRefreshWeek = {},
                )
            }
        }

        compose.onNodeWithText("Aucun cours ce jour-là").assertIsDisplayed()
        compose.onNodeWithText("Synchroniser").assertIsDisplayed()
    }

    @Test
    fun vue_parent_annonce_l_enfant_et_permet_d_en_changer() {
        val lea = Account("4243", "E", "Léa", "Leroy", "3e B", familyId = "777", familyName = "Claire Leroy")
        val martin = Account("4242", "E", "Martin", "Leroy", "Terminale A", familyId = "777", familyName = "Claire Leroy")
        var chosen: Account? = null

        compose.setContent {
            Ecole2recteTheme {
                HomeScreen(
                    snapshot = snapshot.copy(account = lea),
                    settings = AppSettings(),
                    sync = SyncState(),
                    now = now,
                    accounts = listOf(martin, lea),
                    onNavigate = {},
                    onOpenDetail = {},
                    onRefresh = {},
                    onToggleHomework = {},
                    onSelectAccount = { chosen = it },
                )
            }
        }

        // Un parent n'est pas salué par son prénom : c'est la journée de son enfant qu'il
        // ouvre, classe comprise. Le prénom apparaît deux fois — en titre et sur la
        // pastille — d'où le repère sur la classe, qui n'est écrite qu'une fois.
        compose.onNodeWithText("3e B", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Martin").performClick()

        assertEquals("4242", chosen?.id)
    }

    @Test
    fun vie_scolaire_vide_le_dit_clairement() {
        compose.setContent {
            Ecole2recteTheme {
                SchoolLifeScreen(snapshot = SchoolSnapshot(), onClose = {})
            }
        }

        compose.onNodeWithText("Rien à signaler").assertIsDisplayed()
    }
}
