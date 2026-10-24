package app.logdate.feature.core.settings.account.recovery

import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.client.text.UiText
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `correct reentry verifies recovery and wrong words do not`() =
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

            viewModel.reveal(prompt)
            viewModel.verify("incorrect words")
            assertEquals(RecoveryPhraseUiState.VerificationFailed, viewModel.state.value)
            assertEquals(false, confirmed)
            assertEquals(0, backupRequests)

            viewModel.reveal(prompt)
            viewModel.verify(phrase.joinToString(" "))
            assertEquals(RecoveryPhraseUiState.Verified, viewModel.state.value)
            assertEquals(true, confirmed)
            assertEquals(1, backupRequests)
        }

    @Test
    fun `nothing about moving the account shows unless it is offered`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = false, status = AccountRecoveryStatus.NEEDS_SETUP)

            assertNull(viewModel(confirmed = true, recovery = recovery).accountRecovery.value)
        }

    @Test
    fun `an unconfirmed phrase says nothing about moving the account`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.NEEDS_SETUP)

            assertNull(viewModel(confirmed = false, recovery = recovery).accountRecovery.value)
        }

    @Test
    fun `a confirmed phrase says whether it can move the account, without asking for a passkey`() =
        runTest {
            val ready = viewModel(confirmed = true, recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.READY))
            val unfinished = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.NEEDS_SETUP)
            val needsSetup = viewModel(confirmed = true, recovery = unfinished)

            assertEquals(AccountRecoveryUiState.Ready, ready.accountRecovery.value)
            assertEquals(AccountRecoveryUiState.NeedsSetup, needsSetup.accountRecovery.value)
            assertEquals(0, unfinished.setups)
        }

    @Test
    fun `confirming the phrase finishes setting up account recovery`() =
        runTest {
            var confirmed = false
            val recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.NEEDS_SETUP)
            val viewModel =
                RecoveryPhraseViewModel(
                    gatekeeper = ScriptedGatekeeper(AppAuthState.AUTHENTICATED),
                    loadPhrase = { phrase },
                    confirmPhrase = { words -> (words == phrase).also { confirmed = it } },
                    isPhraseVerified = { confirmed },
                    recovery = recovery,
                )

            viewModel.reveal(prompt)
            viewModel.verify(phrase.joinToString(" "))

            assertEquals(1, recovery.setups)
            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    @Test
    fun `closing the passkey prompt leaves setup to finish later`() =
        runTest {
            val recovery =
                FakeAccountRecovery(
                    offered = true,
                    status = AccountRecoveryStatus.NEEDS_SETUP,
                    setupFailure = IdentityRecoveryFailure.STEP_UP_CANCELLED,
                )
            val viewModel = viewModel(confirmed = true, recovery = recovery)

            viewModel.finishAccountRecoverySetup()

            assertEquals(AccountRecoveryUiState.NeedsSetup, viewModel.accountRecovery.value)
        }

    @Test
    fun `setup that fails says why and can be finished later`() =
        runTest {
            val recovery =
                FakeAccountRecovery(
                    offered = true,
                    status = AccountRecoveryStatus.NEEDS_SETUP,
                    setupFailure = IdentityRecoveryFailure.UNREACHABLE,
                )
            val viewModel = viewModel(confirmed = true, recovery = recovery)

            viewModel.finishAccountRecoverySetup()
            assertEquals(AccountRecoveryUiState.SetupFailed(IdentityRecoveryFailure.UNREACHABLE), viewModel.accountRecovery.value)

            recovery.setupFailure = null
            viewModel.finishAccountRecoverySetup()
            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    @Test
    fun `a status that cannot be read can be checked again`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = true, status = AccountRecoveryStatus.READY, statusFails = true)
            val viewModel = viewModel(confirmed = true, recovery = recovery)
            assertEquals(AccountRecoveryUiState.CheckFailed, viewModel.accountRecovery.value)

            recovery.statusFails = false
            viewModel.checkAccountRecovery()

            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    @Test
    fun `a server that starts offering account recovery after the screen opens shows it`() =
        runTest {
            val recovery = FakeAccountRecovery(offered = false, status = AccountRecoveryStatus.READY)
            val viewModel = viewModel(confirmed = true, recovery = recovery)

            recovery.offeredFlow.value = true

            assertEquals(AccountRecoveryUiState.Ready, viewModel.accountRecovery.value)
        }

    private fun viewModel(
        storedPhrase: List<String>? = phrase,
        authResult: AppAuthState = AppAuthState.AUTHENTICATED,
        readFails: Boolean = false,
        confirmed: Boolean = false,
        recovery: AccountRecovery? = null,
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

    private class FakeAccountRecovery(
        offered: Boolean,
        var status: AccountRecoveryStatus,
        var statusFails: Boolean = false,
        var setupFailure: IdentityRecoveryFailure? = null,
    ) : AccountRecovery {
        val offeredFlow = MutableStateFlow(offered)
        var setups = 0
        override val isOffered: Flow<Boolean> = offeredFlow

        override suspend fun status(phrase: List<String>): Result<AccountRecoveryStatus> =
            if (statusFails) Result.failure(IllegalStateException("offline")) else Result.success(status)

        override suspend fun finishSetup(phrase: List<String>): Result<Unit> {
            setups++
            setupFailure?.let { return Result.failure(IdentityRecoveryException(it, "refused")) }
            status = AccountRecoveryStatus.READY
            return Result.success(Unit)
        }
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
