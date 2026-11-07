package fr.ecole2recte.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.session.LoginUiState
import fr.ecole2recte.app.ui.login.LoginScreen
import fr.ecole2recte.app.ui.schedule.DayTimelineView
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** Écrans dessinés seuls, avec des données d'exemple : indépendants de tout compte. */
@RunWith(AndroidJUnit4::class)
class ComponentsOnDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun lesson(name: String, start: String, end: String, room: String = "B204", canceled: Boolean = false) =
        ScheduleEntry(Subject(name, "M. Petit", room, 0), "", canceled, "2026-11-05", start, end)

    private fun showLogin(state: LoginUiState = LoginUiState()) {
        compose.setContent {
            Ecole2recteTheme {
                LoginScreen(state = state, onLogin = { _, _ -> }, onAnswerSecurity = { _, _ -> }, onCancelSecurity = {})
            }
        }
    }

    @Test
    fun laConnexionSAffiche() {
        showLogin()
        compose.onNodeWithText("Connexion").assertIsDisplayed()
        compose.onNodeWithText("Tes identifiants sont chiffrés sur cet appareil.").assertIsDisplayed()
    }

    @Test
    fun laConnexionNEstPossibleQueChampsRemplis() {
        showLogin()
        compose.onNodeWithText("Se connecter  →").assertIsNotEnabled()
        // Chaînes d'essai dans le formulaire isolé du test : rien n'est envoyé nulle part.
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("eleve.test")
        fields[1].performTextInput("motdepasse-d-essai")
        compose.onNodeWithText("Se connecter  →").assertIsEnabled()
    }

    @Test
    fun uneErreurDeConnexionEstExpliquee() {
        showLogin(LoginUiState(error = fr.ecole2recte.app.session.LoginError("Identifiant ou mot de passe invalide.", fr.ecole2recte.app.data.FailureKind.InvalidCredentials)))
        compose.onNodeWithText("Identifiant ou mot de passe invalide.").assertIsDisplayed()
    }

    @Test
    fun lesDoublesHeuresEtLesPausesSontVisiblesSurLaFrise() {
        compose.setContent {
            Ecole2recteTheme {
                DayTimelineView(
                    entries = listOf(
                        lesson("Maths", "08:00", "08:55"),
                        lesson("Maths", "08:55", "09:50"),
                        lesson("Anglais", "10:30", "11:25"),
                        lesson("Histoire", "12:30", "13:25", canceled = true),
                    ),
                    now = LocalTime.of(9, 0),
                )
            }
        }
        compose.onNodeWithText("Double heure", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Pause · 40 min").assertIsDisplayed()
        compose.onNodeWithText("Pause déjeuner", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Maintenant", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Annulé", substring = true).assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("Anglais").fetchSemanticsNodes().size)
    }
}
