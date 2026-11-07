package fr.ecole2recte.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.ecole2recte.app.data.active
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parcourt l'application avec le compte déjà connecté sur le téléphone, **en lecture seule** :
 * on ouvre les onglets et on regarde ; on ne coche aucun devoir, on n'ouvre aucun message (cela
 * le marquerait lu côté ÉcoleDirecte) et on n'envoie rien. Sans compte connecté, ces tests sont
 * ignorés. Le compte se connecte à la main sur le téléphone, jamais depuis les tests.
 */
@RunWith(AndroidJUnit4::class)
class RealAccountReadOnlyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForTab(label: String) {
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilGone(text: String, timeoutMillis: Long = 60_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    private fun open(tab: String) {
        compose.onNodeWithContentDescription(tab, useUnmergedTree = true).performClick()
    }

    private fun connected(): Boolean {
        val container = (compose.activity.application as Ecole2recteApplication).container
        return container.credentialStore.active() != null
    }

    @Test
    fun lesCinqOngletsSOuvrentSansPlanter() {
        assumeTrue("aucun compte connecté sur ce téléphone", connected())
        waitForTab("Accueil")
        for (tab in listOf("Agenda", "Notes", "Messages", "Plus", "Accueil")) {
            open(tab)
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Accueil", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun lAgendaMontreLesHeuresEtLesDeuxVues() {
        assumeTrue("aucun compte connecté sur ce téléphone", connected())
        waitForTab("Accueil")
        open("Agenda")
        waitForTab("Emploi du temps")
        compose.onNodeWithText("Jour").assertIsDisplayed()
        compose.onNodeWithText("Semaine").assertIsDisplayed()
        compose.onNodeWithText("Travail à faire").performClick()
        compose.waitForIdle()
    }

    @Test
    fun lesNotesMontrentUneMoyenneEtDesMatieres() {
        assumeTrue("aucun compte connecté sur ce téléphone", connected())
        waitForTab("Accueil")
        open("Notes")
        waitForTab("Par matière")
        // Le défaut du premier essai : trimestre vide, moyenne « – ». Aucun des deux ne doit apparaître.
        compose.waitForIdle()
        val noGrades = compose.onAllNodesWithText("Pas encore de notes").fetchSemanticsNodes()
        val dashAverage = compose.onAllNodesWithText("–").fetchSemanticsNodes()
        org.junit.Assert.assertTrue("la moyenne générale ne doit pas être vide", dashAverage.isEmpty())
        org.junit.Assert.assertTrue("les matières ne doivent pas être vides", noGrades.isEmpty())
    }

    @Test
    fun lesReglagesDeNotificationEtDeVieScolaireSontLa() {
        assumeTrue("aucun compte connecté sur ce téléphone", connected())
        waitForTab("Accueil")
        open("Plus")
        waitForTab("Vie scolaire")
        compose.onNodeWithText("Notifications et widgets").assertIsDisplayed()
    }

    /**
     * Le plantage vu sur le téléphone : demander la permission de notification levait « Can only use
     * lower 16 bits for requestCode » (vieux Fragment tiré par la bibliothèque d'empreinte). Ce test
     * fait la même demande que l'interrupteur de Plus, avec le même registre d'Activity.
     */
    @Test
    fun demanderLaPermissionDeNotificationNePlantePas() {
        waitForTab("Accueil")
        compose.runOnUiThread {
            val launcher = compose.activity.activityResultRegistry.register(
                "test-notif-permission",
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { }
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.waitForIdle()
        // Si Android affiche sa fenêtre de demande, on la ferme.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
    }
}
