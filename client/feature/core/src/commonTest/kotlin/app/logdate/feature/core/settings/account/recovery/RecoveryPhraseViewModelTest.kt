package app.logdate.feature.core.settings.account.recovery

import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.client.text.UiText
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryPhraseViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val phrase = List(12) { "word$it" }
    private val prompt = RevealPrompt(title = "Show recovery phrase", subtitle = "Confirm it's you", description = null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the phrase stays hidden until asked for`() =
        runTest {
            assertEquals(RecoveryPhraseUiState.Hidden, viewModel().state.value)
        }

    @Test
    fun `a device without the phrase offers to enter it`() =
        runTest {
            assertEquals(RecoveryPhraseUiState.NotOnThisDevice, viewModel(storedPhrase = null).state.value)
        }

    @Test
    fun `checking again finds a phrase entered since the screen was last open`() =
        runTest {
            var stored: List<String>? = null
            val viewModel = RecoveryPhraseViewModel(gatekeeper = ScriptedGatekeeper(AppAuthState.AUTHENTICATED), loadPhrase = { stored })
            assertEquals(RecoveryPhraseUiState.NotOnThisDevice, viewModel.state.value)

            stored = phrase
            viewModel.check()

            assertEquals(RecoveryPhraseUiState.Hidden, viewModel.state.value)
        }

    @Test
    fun `the phrase is shown once the person confirms it is them`() =
        runTest {
            val viewModel = viewModel(authResult = AppAuthState.AUTHENTICATED)

            viewModel.reveal(prompt)

            assertEquals(RecoveryPhraseUiState.Revealed(phrase), viewModel.state.value)
        }

    @Test
    fun `the phrase is shown without a prompt when the app has no lock`() =
        runTest {
            val viewModel = viewModel(authResult = AppAuthState.NO_PROMPT_NEEDED)

            viewModel.reveal(prompt)

            assertEquals(RecoveryPhraseUiState.Revealed(phrase), viewModel.state.value)
        }

    @Test
    fun `an unconfirmed request keeps the phrase hidden and says why`() =
        runTest {
            val viewModel = viewModel(authResult = AppAuthState.REQUIRE_PROMPT)

            viewModel.reveal(prompt)

            assertEquals(RecoveryPhraseUiState.Failed(RevealFailure.NOT_CONFIRMED), viewModel.state.value)
        }

    @Test
    fun `a phrase that cannot be read is reported`() =
        runTest {
            val viewModel = viewModel(readFails = true)

            viewModel.reveal(prompt)

            assertEquals(RecoveryPhraseUiState.Failed(RevealFailure.UNREADABLE), viewModel.state.value)
        }

    @Test
    fun `a screen rebuilt while the phrase is shown keeps it shown`() =
        runTest {
            val viewModel = viewModel()
            viewModel.reveal(prompt)

            viewModel.check()

            assertEquals(RecoveryPhraseUiState.Revealed(phrase), viewModel.state.value)
        }

    @Test
    fun `hiding the phrase covers it again`() =
        runTest {
            val viewModel = viewModel()
            viewModel.reveal(prompt)

            viewModel.hide()

            assertEquals(RecoveryPhraseUiState.Hidden, viewModel.state.value)
        }

    @Test
    fun `saving the shown phrase confirms it and nothing is confirmed before it is shown`() =
        runTest {
            var confirmed = false
            var backupRequests = 0
            val viewModel =
                RecoveryPhraseViewModel(
                    gatekeeper = ScriptedGatekeeper(AppAuthState.AUTHENTICATED),
                    loadPhrase = { phrase },
                    confirmPhrase = { words ->
                        confirmed = words == phrase
                        confirmed
                    },
                    isPhraseVerified = { confirmed },
                    onVerified = { backupRequests++ },
                )

            viewModel.confirmSaved()
            assertEquals(false, confirmed)
            assertEquals(0, backupRequests)

            viewModel.reveal(prompt)
            viewModel.confirmSaved()
            assertEquals(RecoveryPhraseUiState.Verified, viewModel.state.value)
            assertEquals(true, confirmed)
            assertEquals(1, backupRequests)
        }

    @Test
    fun `a phrase confirmed earlier is shown without asking for words again`() =
        runTest {
            val viewModel =
                RecoveryPhraseViewModel(
                    gatekeeper = ScriptedGatekeeper(AppAuthState.AUTHENTICATED),
                    loadPhrase = { phrase },
                    isPhraseVerified = { true },
                )

            viewModel.reveal(prompt)

            assertEquals(RecoveryPhraseUiState.Revealed(phrase, confirmed = true), viewModel.state.value)
        }

    @Test
    fun `nothing about moving the account shows unless it is offered`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = false, status = AccountRecoveryStatus.NEEDS_SETUP)

            assertNull(viewModel(confirmed = true, recovery = automatic(recovery)).accountRecovery.value)
        }

    @Test
    fun `moving the account is set up whether or not a copy of the phrase was saved`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.NEEDS_SETUP)

            val viewModel = viewModel(confirmed = false, recovery = automatic(recovery))

            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
            assertTrue(recovery.setups.isNotEmpty() && true !in recovery.setups, "setup showed a passkey prompt")
        }

    @Test
    fun `saving a copy of the phrase asks for nothing more`() =
        runTest {
            var confirmed = false
            val recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.NEEDS_SETUP, needsPasskey = true)
            val viewModel =
                RecoveryPhraseViewModel(
                    gatekeeper = ScriptedGatekeeper(AppAuthState.AUTHENTICATED),
                    loadPhrase = { phrase },
                    confirmPhrase = { words -> (words == phrase).also { confirmed = it } },
                    isPhraseVerified = { confirmed },
                    recovery = automatic(recovery),
                )

            viewModel.reveal(prompt)
            viewModel.confirmSaved()

            assertEquals(RecoveryPhraseUiState.Verified, viewModel.state.value)
            assertTrue(true !in recovery.setups, "saving the phrase showed a passkey prompt")
            assertEquals(AccountRecoveryUiState.NeedsSetup, viewModel.accountRecovery.value)
        }

    @Test
    fun `closing the passkey prompt leaves setup to finish later`() =
        runTest {
            val recovery =
                FakeAccountRecovery(
                    offered = true,
                    status = AccountRecoveryStatus.NEEDS_SETUP,
                    needsPasskey = true,
                    promptFailure = IdentityRecoveryFailure.STEP_UP_CANCELLED,
                )
            val viewModel = viewModel(confirmed = true, recovery = automatic(recovery))

            viewModel.finishAccountRecoverySetup()

            assertEquals(AccountRecoveryUiState.NeedsSetup, viewModel.accountRecovery.value)
            assertEquals(true, recovery.setups.last())
        }

    @Test
    fun `setup that fails says why and can be finished later`() =
        runTest {
            val recovery =
                FakeAccountRecovery(
                    offered = true,
                    status = AccountRecoveryStatus.NEEDS_SETUP,
                    needsPasskey = true,
                    promptFailure = IdentityRecoveryFailure.PASSKEY_REQUIRED,
                )
            val viewModel = viewModel(confirmed = true, recovery = automatic(recovery))

            viewModel.finishAccountRecoverySetup()
            assertEquals(AccountRecoveryUiState.SetupFailed(IdentityRecoveryFailure.PASSKEY_REQUIRED), viewModel.accountRecovery.value)

            recovery.promptFailure = null
            viewModel.finishAccountRecoverySetup()
            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    @Test
    fun `a status that cannot be read can be checked again`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.READY, statusFails = true)
            val viewModel = viewModel(confirmed = true, recovery = automatic(recovery, retries = 0))
            assertEquals(AccountRecoveryUiState.CheckFailed, viewModel.accountRecovery.value)

            recovery.statusFails = false
            viewModel.checkAccountRecovery()

            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    @Test
    fun `a server that starts offering account recovery after the screen opens shows it`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = false, status = AccountRecoveryStatus.READY)
            val viewModel = viewModel(confirmed = true, recovery = automatic(recovery))

            recovery.offeredFlow.value = true

            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    private fun TestScope.automatic(
        recovery: FakeAccountRecovery,
        retries: Int = 1,
    ): AutomaticAccountRecovery =
        AutomaticAccountRecovery(
            recovery = recovery,
            loadPhrase = { phrase },
            signedInAccount = MutableStateFlow("account-123"),
            passkeyConfirmations = MutableStateFlow(null),
            // Eager like the view model's own scope here, so each assertion sees where setup stands.
            scope = CoroutineScope(backgroundScope.coroutineContext + testDispatcher),
            retryDelays = List(retries) { 1.seconds },
        ).also { it.start() }

    private fun viewModel(
        storedPhrase: List<String>? = phrase,
        authResult: AppAuthState = AppAuthState.AUTHENTICATED,
        readFails: Boolean = false,
        confirmed: Boolean = false,
        recovery: AutomaticAccountRecovery? = null,
    ): RecoveryPhraseViewModel {
        var reads = 0
        return RecoveryPhraseViewModel(
            gatekeeper = ScriptedGatekeeper(authResult),
            loadPhrase = {
                // The first read only checks whether a phrase exists; the reveal is the second.
                if (readFails && reads++ > 0) error("Secure storage unavailable")
                storedPhrase
            },
            isPhraseVerified = { confirmed },
            recovery = recovery,
        )
    }

    /** Account recovery whose status and setup are scripted; each setup records whether it could prompt. */
    private class FakeAccountRecovery(
        offered: Boolean,
        var status: AccountRecoveryStatus,
        var statusFails: Boolean = false,
        private val needsPasskey: Boolean = false,
        var promptFailure: IdentityRecoveryFailure? = null,
    ) : AccountRecovery {
        val offeredFlow = MutableStateFlow(offered)
        val setups = mutableListOf<Boolean>()
        override val isOffered: Flow<Boolean> = offeredFlow

        override suspend fun status(phrase: List<String>): Result<AccountRecoveryStatus> =
            if (statusFails) Result.failure(IllegalStateException("offline")) else Result.success(status)

        override suspend fun finishSetup(
            phrase: List<String>,
            askForPasskey: Boolean,
        ): Result<Unit> {
            setups += askForPasskey
            if (statusFails) return Result.failure(IllegalStateException("offline"))
            if (status == AccountRecoveryStatus.READY) return Result.success(Unit)
            if (needsPasskey && !askForPasskey) return failure(IdentityRecoveryFailure.CONFIRMATION_NEEDED)
            if (askForPasskey) promptFailure?.let { return failure(it) }
            status = AccountRecoveryStatus.READY
            return Result.success(Unit)
        }

        private fun failure(failure: IdentityRecoveryFailure): Result<Unit> = Result.failure(IdentityRecoveryException(failure, "refused"))
    }

    private class ScriptedGatekeeper(
        private val result: AppAuthState,
    ) : BiometricGatekeeper {
        override val authState: StateFlow<AppAuthState> = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

        override fun authenticate(
            title: UiText,
            subtitle: UiText,
            cancelLabel: UiText,
            requireConfirmation: Boolean,
            requestEnrollmentIfNecessary: Boolean,
            description: UiText?,
            onResult: (AppAuthState) -> Unit,
        ) = onResult(result)

        override fun requestEnrollment() {}
    }
}
