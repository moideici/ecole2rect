package fr.ecole2recte.app.session

import fr.ecole2recte.app.data.AuthClient
import fr.ecole2recte.app.data.BirthDate
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.Account
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.Student
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

private class FakeStore(vararg initial: SavedLogin) : CredentialStore {
    val logins = initial.toMutableList()
    var activeId: String? = initial.firstOrNull()?.identifier

    /** Compte actif tel que l'enregistrement le voit. */
    val saved: SavedLogin? get() = logins.firstOrNull { it.identifier == activeId }

    override fun readAll() = logins.toList()
    override fun activeIdentifier() = activeId

    override fun save(login: SavedLogin, makeActive: Boolean) {
        val index = logins.indexOfFirst { it.identifier.equals(login.identifier, ignoreCase = true) }
        if (index >= 0) logins[index] = login else logins += login
        if (makeActive || activeId == null) activeId = login.identifier
    }

    override fun setActive(identifier: String) {
        activeId = identifier
    }

    override fun remove(identifier: String) {
        logins.removeAll { it.identifier.equals(identifier, ignoreCase = true) }
        if (logins.none { it.identifier == activeId }) activeId = logins.firstOrNull()?.identifier
    }

    override fun clear() {
        logins.clear()
        activeId = null
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

    // --- Multi-compte ------------------------------------------------------------------------

    private val second = SavedLogin("lmartin", "autre")

    @Test
    fun `ajouter un compte le rend actif et garde le premier`() = runTest(dispatcher) {
        client.loginResults += success
        val store = FakeStore(saved)
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.startAddAccount()
        assertEquals(true, vm.state.value.adding)
        client.loginResults += LoginResult.Success(listOf(Account("1", "E", "Léa", "MARTIN")))
        vm.login("lmartin", "autre")
        advanceUntilIdle()
        assertEquals(false, vm.state.value.adding)
        assertEquals(listOf("jdupont", "lmartin"), vm.state.value.accounts.map { it.identifier })
        assertEquals("lmartin", vm.state.value.active?.identifier)
        assertEquals("Léa", vm.state.value.active?.firstName)
        assertEquals("Léa Martin", vm.state.value.accounts.last().displayName)
    }

    @Test
    fun `annuler l'ajout revient au compte actif sans appel reseau`() = runTest(dispatcher) {
        client.loginResults += success
        val vm = viewModel(FakeStore(saved))
        advanceUntilIdle()
        vm.startAddAccount()
        vm.cancelAddAccount()
        assertEquals(false, vm.state.value.adding)
        assertEquals("jdupont", vm.state.value.active?.identifier)
        assertEquals(1, client.loginAnswers.size)
    }

    @Test
    fun `changer de compte reconnecte l'autre compte`() = runTest(dispatcher) {
        client.loginResults += success
        client.loginResults += success
        val store = FakeStore(saved, second)
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.switchAccount("lmartin")
        assertEquals("lmartin", vm.state.value.active?.identifier)
        assertEquals(ConnectionStatus.Connecting, vm.state.value.connection)
        advanceUntilIdle()
        assertEquals(ConnectionStatus.Connected, vm.state.value.connection)
        assertEquals("lmartin", store.activeId)
        assertEquals(2, client.loginAnswers.size)
    }

    @Test
    fun `retirer le compte actif bascule sur l'autre`() = runTest(dispatcher) {
        client.loginResults += success
        client.loginResults += success
        val store = FakeStore(saved, second)
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.removeAccount("jdupont")
        advanceUntilIdle()
        assertEquals(listOf("lmartin"), store.logins.map { it.identifier })
        assertEquals("lmartin", vm.state.value.active?.identifier)
        assertEquals(AuthStatus.SignedIn, vm.state.value.status)
    }

    @Test
    fun `retirer le dernier compte deconnecte`() = runTest(dispatcher) {
        client.loginResults += success
        val store = FakeStore(saved)
        val vm = viewModel(store)
        advanceUntilIdle()
        vm.removeAccount("jdupont")
        assertEquals(AuthStatus.SignedOut, vm.state.value.status)
        assertEquals(true, store.logins.isEmpty())
        assertNull(vm.state.value.active)
    }

    @Test
    fun `un compte famille permet de choisir l'eleve affiche`() = runTest(dispatcher) {
        val family = Account(
            "1", "1", "Marie", "DUPONT",
            students = listOf(Student("10", "PAUL", "DUPONT", "6A"), Student("11", "Zoé", "DUPONT", "3B")),
        )
        client.loginResults += LoginResult.Success(listOf(family))
        val vm = viewModel(FakeStore(saved))
        advanceUntilIdle()
        assertEquals("10", vm.state.value.active?.studentId)
        assertEquals("Paul", vm.state.value.active?.firstName)
        assertEquals("Famille", vm.state.value.accounts.single().typeLabel)
        val before = vm.state.value.active?.key
        vm.selectStudent("11")
        assertEquals("Zoé", vm.state.value.active?.firstName)
        assertEquals(true, before != vm.state.value.active?.key)
        vm.selectStudent("inconnu")
        assertEquals("11", vm.state.value.active?.studentId)
    }

    @Test
    fun `les noms sont mis en forme`() {
        assertEquals("Jean-Paul", titleCase("JEAN-PAUL"))
        assertEquals("De La Fontaine", titleCase("de la fontaine"))
        assertEquals("LM", initialsOf("léa", "martin", "x"))
        assertEquals("X", initialsOf("", "", "xavier"))
    }
}
