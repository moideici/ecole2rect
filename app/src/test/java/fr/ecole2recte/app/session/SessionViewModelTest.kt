package fr.ecole2recte.app.session

import fr.ecole2recte.app.data.AuthClient
import fr.ecole2recte.app.data.BirthDate
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.TrustedAnswer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeStore(var saved: SavedLogin? = null) : CredentialStore {
    override fun read() = saved
    override fun save(login: SavedLogin) {
        saved = login
    }

    override fun clear() {
        saved = null
    }
}

private class FakeClient : AuthClient {
    val loginResults = ArrayDeque<LoginResult>()
    val answerResults = ArrayDeque<LoginResult>()
    val loginAnswers = mutableListOf<TrustedAnswer?>()
    val answers = mutableListOf<String>()
    var approvedOnAnswer: TrustedAnswer? = null
    override var approvedAnswer: TrustedAnswer? = null

    override fun login(identifier: String, password: String, trustedAnswer: TrustedAnswer?): LoginResult {
        loginAnswers += trustedAnswer
        return loginResults.removeFirst()
    }

    override fun completeSecurityQuestion(choice: String, identifier: String, password: String): LoginResult {
        answers += choice
        approvedAnswer = approvedOnAnswer
        return answerResults.removeFirst()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val client = FakeClient()
    private val success = LoginResult.Success(emptyList())
    private val saved = SavedLogin("jdupont", "secret", TrustedAnswer("cn", "cv"))

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel(store: FakeStore) =
        SessionViewModel(store, clientFactory = { client }, ioDispatcher = dispatcher, retryDelaysMs = listOf(5_000L, 20_000L))

    private fun failure(kind: FailureKind) = LoginResult.Failure("échec", "diag", kind)

    private val monthQuestion = LoginResult.SecurityQuestionRequired(
        "Quel est ton mois de naissance ?",
        listOf("Janvier", "Février"),
    )

    @Test
    fun `sans identifiants enregistres on demande la connexion`() = runTest(dispatcher) {
        val vm = viewModel(FakeStore())
        assertEquals(AuthStatus.SignedOut, vm.state.value.status)
    }

    @Test
    fun `avec identifiants l'app s'ouvre tout de suite puis se reconnecte en arriere-plan`() = runTest(dispatcher) {
        client.loginResults += success
        val vm = viewModel(FakeStore(saved))
        assertEquals(AuthStatus.SignedIn, vm.state.value.status)
        assertEquals(ConnectionStatus.Connecting, vm.state.value.connection)
        advanceUntilIdle()
        assertEquals(ConnectionStatus.Connected, vm.state.value.connection)
        assertEquals(listOf(saved.trustedAnswer), client.loginAnswers)
    }

    @Test
    fun `la reponse de confiance survit a une reconnexion reussie`() = runTest(dispatcher) {
        client.loginResults += success
        val store = FakeStore(saved)
        viewModel(store)
        advanceUntilIdle()
        assertEquals(saved.trustedAnswer, store.saved?.trustedAnswer)
    }

    @Test
    fun `un mot de passe refuse ne declenche aucun nouvel essai`() = runTest(dispatcher) {
        client.loginResults += failure(FailureKind.InvalidCredentials)
        val vm = viewModel(FakeStore(saved))
        advanceUntilIdle()
        assertEquals(AuthStatus.SignedOut, vm.state.value.status)
        assertEquals("", vm.state.value.login.password)
        assertEquals("jdupont", vm.state.value.login.identifier)
        vm.login("jdupont", "secret")
        advanceUntilIdle()
        assertEquals("aucun appel réseau supplémentaire", 1, client.loginAnswers.size)
        assertEquals(FailureKind.InvalidCredentials, vm.state.value.login.error?.kind)
    }

    @Test
    fun `un compte bloque n'est jamais reessaye`() = runTest(dispatcher) {
        client.loginResults += failure(FailureKind.Blocked)
        val vm = viewModel(FakeStore(saved))
        advanceUntilIdle()
        assertEquals(1, client.loginAnswers.size)
        assertEquals(AuthStatus.SignedOut, vm.state.value.status)
    }

    @Test
    fun `une panne reseau est reessayee deux fois puis l'app reste hors ligne`() = runTest(dispatcher) {
        repeat(3) { client.loginResults += failure(FailureKind.Network) }
        val vm = viewModel(FakeStore(saved))
        advanceUntilIdle()
        assertEquals(3, client.loginAnswers.size)
        assertEquals(AuthStatus.SignedIn, vm.state.value.status)
        assertEquals(ConnectionStatus.Offline, vm.state.value.connection)
    }

    @Test
    fun `la reconnexion reussit des que le reseau revient`() = runTest(dispatcher) {
        client.loginResults += failure(FailureKind.Network)
        client.loginResults += success
        val vm = viewModel(FakeStore(saved))
        advanceUntilIdle()
        assertEquals(2, client.loginAnswers.size)
        assertEquals(ConnectionStatus.Connected, vm.state.value.connection)
    }

    @Test
    fun `une premiere connexion avec question memorise la date de naissance et la reponse de confiance`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val vm = viewModel(store)
            client.loginResults += monthQuestion
            client.answerResults += success
            client.approvedOnAnswer = TrustedAnswer("cn-neuf", "cv-neuf")
            vm.login("jdupont", "secret")
            advanceUntilIdle()
            assertNotNull(vm.state.value.login.challenge)
            vm.answerSecurity("Février", BirthDate("12", "2", "2008"))
            advanceUntilIdle()
            assertEquals(AuthStatus.SignedIn, vm.state.value.status)
            assertEquals(TrustedAnswer("cn-neuf", "cv-neuf"), store.saved?.trustedAnswer)
            assertEquals(BirthDate("12", "2", "2008"), store.saved?.birthDate)
        }

    @Test
    fun `si la question revient elle recoit la reponse seule grace a la date memorisee`() = runTest(dispatcher) {
        val store = FakeStore(saved.copy(birthDate = BirthDate("12", "2", "2008")))
        client.loginResults += monthQuestion
        client.answerResults += success
        val vm = viewModel(store)
        advanceUntilIdle()
        assertEquals(listOf("Février"), client.answers)
        assertEquals(AuthStatus.SignedIn, vm.state.value.status)
        assertEquals(ConnectionStatus.Connected, vm.state.value.connection)
        assertNull(vm.state.value.login.challenge)
    }

    @Test
    fun `une reponse de securite refusee n'est jamais retentee`() = runTest(dispatcher) {
        val vm = viewModel(FakeStore())
        client.loginResults += monthQuestion
        client.answerResults += failure(FailureKind.SecurityRefused)
        vm.login("jdupont", "secret")
        advanceUntilIdle()
        vm.answerSecurity("Janvier")
        advanceUntilIdle()
        assertNull(vm.state.value.login.challenge)
        assertEquals(FailureKind.SecurityRefused, vm.state.value.login.error?.kind)
        vm.answerSecurity("Février")
        advanceUntilIdle()
        assertEquals(listOf("Janvier"), client.answers)
    }
}
