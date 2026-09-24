package app.logdate.feature.core.settings.account.recovery

import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun viewModel(
        storedPhrase: List<String>? = phrase,
        authResult: AppAuthState = AppAuthState.AUTHENTICATED,
        readFails: Boolean = false,
    ): RecoveryPhraseViewModel {
        var reads = 0
        return RecoveryPhraseViewModel(
            gatekeeper = ScriptedGatekeeper(authResult),
            loadPhrase = {
                // The first read only checks whether a phrase exists; the reveal is the second.
                if (readFails && reads++ > 0) error("Secure storage unavailable")
                storedPhrase
            },
        )
    }

    private class ScriptedGatekeeper(
        private val result: AppAuthState,
    ) : BiometricGatekeeper {
        override val authState: StateFlow<AppAuthState> = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

        override fun authenticate(
            title: String,
            subtitle: String,
            cancelLabel: String,
            requireConfirmation: Boolean,
            requestEnrollmentIfNecessary: Boolean,
            description: String?,
            onResult: (AppAuthState) -> Unit,
        ) = onResult(result)

        override fun requestEnrollment() {}
    }
}
