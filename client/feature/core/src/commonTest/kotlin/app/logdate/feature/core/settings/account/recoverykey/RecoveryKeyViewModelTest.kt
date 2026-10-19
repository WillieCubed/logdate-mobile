package app.logdate.feature.core.settings.account.recoverykey

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.repository.account.AccountDerivedPlcRecoveryKey
import app.logdate.client.repository.account.AccountExportedSigningKey
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.AccountImportedSigningKey
import app.logdate.client.repository.account.AccountRegisteredPlcRecoveryKey
import app.logdate.client.repository.account.AccountRotatedSigningKey
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.feature.core.settings.account.ConnectedServer
import app.logdate.feature.core.settings.account.ConnectedServerInfo
import app.logdate.feature.core.settings.account.ServerHealth
import app.logdate.feature.core.settings.ui.labs.FakeFeatureFlagStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryKeyViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val phrase = List(12) { "word$it" }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `without a registered key and a confirmed phrase the key can be turned on`() =
        runTest {
            val state = ready(viewModel(FakeIdentity(registered = null)))

            assertEquals(KeyStatus.OFF, state.key)
            assertEquals(PhraseStatus.CONFIRMED, state.phrase)
            assertEquals("alice.logdate.app", state.handle)
            assertEquals("LogDate Cloud", state.serverName)
            assertTrue(state.canTurnOn)
            assertFalse(state.canReplaceServerKey)
        }

    @Test
    fun `an unconfirmed or missing phrase cannot turn the key on`() =
        runTest {
            val unconfirmed = ready(viewModel(FakeIdentity(registered = null), confirmed = false))
            val missing = ready(viewModel(FakeIdentity(registered = null), storedPhrase = null))

            assertEquals(PhraseStatus.NOT_CONFIRMED, unconfirmed.phrase)
            assertFalse(unconfirmed.canTurnOn)
            assertEquals(PhraseStatus.MISSING, missing.phrase)
            assertFalse(missing.canTurnOn)
        }

    @Test
    fun `turning on registers the key from this device's phrase and shows it on`() =
        runTest {
            val identity = FakeIdentity(registered = null)
            val viewModel = viewModel(identity)

            viewModel.turnOn()

            assertEquals(listOf(phrase), identity.registrations)
            val state = ready(viewModel)
            assertEquals(KeyStatus.ON, state.key)
            assertNull(state.action)
            assertTrue(state.canReplaceServerKey)
        }

    @Test
    fun `turning on shows the request under way`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val identity = FakeIdentity(registered = null, registerGate = gate)
            val viewModel = viewModel(identity)

            viewModel.turnOn()
            assertEquals(RecoveryKeyAction.TURNING_ON, ready(viewModel).action)
            gate.complete(Unit)

            assertNull(ready(viewModel).action)
        }

    @Test
    fun `a refused registration says why and can be dismissed`() =
        runTest {
            val identity = FakeIdentity(registered = null, registerFailure = IdentityRecoveryFailure.ALREADY_SET)
            val viewModel = viewModel(identity)

            viewModel.turnOn()
            assertEquals(IdentityRecoveryFailure.ALREADY_SET, ready(viewModel).error)
            assertEquals(KeyStatus.OFF, ready(viewModel).key)

            viewModel.dismissError()
            assertNull(ready(viewModel).error)
        }

    @Test
    fun `a registered key from this phrase can replace the server key`() =
        runTest {
            val identity = FakeIdentity(registered = DERIVED)
            val viewModel = viewModel(identity)

            viewModel.replaceServerKey()

            assertEquals(listOf(phrase), identity.recoveries)
            val state = ready(viewModel)
            assertTrue(state.replacedServerKey)
            assertNull(state.error)
        }

    @Test
    fun `a failed replacement says why and changes nothing on screen`() =
        runTest {
            val identity = FakeIdentity(registered = DERIVED, recoverFailure = IdentityRecoveryFailure.IDENTITY_CHANGED)
            val viewModel = viewModel(identity)

            viewModel.replaceServerKey()

            val state = ready(viewModel)
            assertEquals(IdentityRecoveryFailure.IDENTITY_CHANGED, state.error)
            assertFalse(state.replacedServerKey)
            assertEquals(KeyStatus.ON, state.key)
        }

    @Test
    fun `a key from another phrase is shown and cannot be used here`() =
        runTest {
            val identity = FakeIdentity(registered = "did:key:zSomeoneElse")
            val viewModel = viewModel(identity)

            val state = ready(viewModel)
            viewModel.replaceServerKey()

            assertEquals(KeyStatus.ON_FROM_OTHER_PHRASE, state.key)
            assertFalse(state.canReplaceServerKey)
            assertTrue(identity.recoveries.isEmpty())
        }

    @Test
    fun `a registered key with no phrase on this device cannot be used here`() =
        runTest {
            val state = ready(viewModel(FakeIdentity(registered = DERIVED), storedPhrase = null))

            assertEquals(KeyStatus.ON_WITHOUT_PHRASE, state.key)
            assertFalse(state.canReplaceServerKey)
            assertFalse(state.canReplaceRecoveryKey)
        }

    @Test
    fun `a key from another phrase can be replaced with this device's confirmed phrase`() =
        runTest {
            val identity = FakeIdentity(registered = "did:key:zSomeoneElse")
            val viewModel = viewModel(identity)

            assertTrue(ready(viewModel).canReplaceRecoveryKey)
            viewModel.replaceRecoveryKey()

            assertEquals(listOf(phrase), identity.replacements)
            val state = ready(viewModel)
            assertEquals(KeyStatus.ON, state.key)
            assertTrue(state.replacedRecoveryKey)
        }

    @Test
    fun `a key from another phrase is not replaced with an unconfirmed phrase`() =
        runTest {
            val identity = FakeIdentity(registered = "did:key:zSomeoneElse")
            val viewModel = viewModel(identity, confirmed = false)

            viewModel.replaceRecoveryKey()

            assertFalse(ready(viewModel).canReplaceRecoveryKey)
            assertTrue(identity.replacements.isEmpty())
        }

    @Test
    fun `an earlier error is gone after a later request succeeds`() =
        runTest {
            val identity = FakeIdentity(registered = DERIVED, recoverFailure = IdentityRecoveryFailure.STEP_UP_CANCELLED)
            val viewModel = viewModel(identity)
            viewModel.replaceServerKey()
            assertEquals(IdentityRecoveryFailure.STEP_UP_CANCELLED, ready(viewModel).error)

            identity.recoverFailure = null
            viewModel.replaceServerKey()

            assertNull(ready(viewModel).error)
            assertTrue(ready(viewModel).replacedServerKey)
        }

    @Test
    fun `an earlier error is gone after the page is read again`() =
        runTest {
            val identity = FakeIdentity(registered = null, registerFailure = IdentityRecoveryFailure.REFUSED)
            val viewModel = viewModel(identity)
            viewModel.turnOn()

            viewModel.refresh()

            assertNull(ready(viewModel).error)
        }

    @Test
    fun `a phrase that cannot be read offers only try again`() =
        runTest {
            val viewModel = viewModel(FakeIdentity(registered = DERIVED), phraseFailure = IllegalStateException("keystore locked"))

            val state = assertIs<RecoveryKeyUiState.PhraseUnavailable>(viewModel.state.value)

            assertEquals(PhraseProblem.UNREADABLE, state.problem)
            assertEquals("alice.logdate.app", state.handle)
        }

    @Test
    fun `a phrase read failure during a request stops it without sending anything`() =
        runTest {
            var failRead = false
            val identity = FakeIdentity(registered = DERIVED)
            val viewModel =
                RecoveryKeyViewModel(
                    identity = identity,
                    connectedServer = FakeServer(supportsRecoveryKeys = true),
                    offered = MutableStateFlow(true),
                    loadPhrase = { if (failRead) error("keystore locked") else phrase },
                    isPhraseConfirmed = { true },
                )
            failRead = true

            viewModel.replaceServerKey()

            assertEquals(PhraseProblem.UNREADABLE, assertIs<RecoveryKeyUiState.PhraseUnavailable>(viewModel.state.value).problem)
            assertTrue(identity.recoveries.isEmpty())
        }

    @Test
    fun `a failed confirmation check is reported instead of taken as unconfirmed`() =
        runTest {
            val viewModel =
                RecoveryKeyViewModel(
                    identity = FakeIdentity(registered = null),
                    connectedServer = FakeServer(supportsRecoveryKeys = true),
                    offered = MutableStateFlow(true),
                    loadPhrase = { phrase },
                    isPhraseConfirmed = { error("storage unavailable") },
                )

            val state = assertIs<RecoveryKeyUiState.PhraseUnavailable>(viewModel.state.value)

            assertEquals(PhraseProblem.CONFIRMATION_UNREADABLE, state.problem)
        }

    @Test
    fun `the page shows nothing it can change when the feature is not offered`() =
        runTest {
            val identity = FakeIdentity(registered = DERIVED)
            val viewModel = viewModel(identity, offered = false)

            viewModel.replaceServerKey()

            assertEquals(RecoveryKeyUiState.NotOffered, viewModel.state.value)
            assertTrue(identity.recoveries.isEmpty())
        }

    @Test
    fun `an identity that cannot be read offers to try again`() =
        runTest {
            val identity = FakeIdentity(registered = null, statusFailure = IllegalStateException("offline"))
            val viewModel = viewModel(identity)

            assertEquals(RecoveryKeyUiState.LoadFailed("LogDate Cloud"), viewModel.state.value)

            identity.statusFailure = null
            viewModel.refresh()

            assertIs<RecoveryKeyUiState.Ready>(viewModel.state.value)
        }

    @Test
    fun `a phrase this device cannot derive a key from offers only try again`() =
        runTest {
            val off = viewModel(FakeIdentity(registered = null, deriveFailure = true)).state.value
            val registered = viewModel(FakeIdentity(registered = DERIVED, deriveFailure = true)).state.value

            assertEquals(PhraseProblem.CANNOT_DERIVE, assertIs<RecoveryKeyUiState.PhraseUnavailable>(off).problem)
            assertEquals(PhraseProblem.CANNOT_DERIVE, assertIs<RecoveryKeyUiState.PhraseUnavailable>(registered).problem)
        }

    @Test
    fun `the row is offered only with the flag on and server support and passkeys on this device`() =
        runTest {
            val flags = FakeFeatureFlagStore()
            val supporting = FakeServer(supportsRecoveryKeys = true)
            val notSupporting = FakeServer(supportsRecoveryKeys = false)
            val passkeys = MutableStateFlow(true)

            assertFalse(recoveryKeyOffered(flags, supporting, passkeys).first(), "the flag is off by default")
            flags.setEnabled(FeatureFlag.IDENTITY_RECOVERY_KEY, true)
            assertTrue(recoveryKeyOffered(flags, supporting, passkeys).first())
            assertFalse(recoveryKeyOffered(flags, notSupporting, passkeys).first(), "a server without the capability never shows it")
            assertFalse(recoveryKeyOffered(flags, supporting, MutableStateFlow(false)).first(), "a device without passkeys never shows it")
        }

    private fun ready(viewModel: RecoveryKeyViewModel): RecoveryKeyUiState.Ready = assertIs(viewModel.state.value)

    private fun viewModel(
        identity: FakeIdentity,
        storedPhrase: List<String>? = phrase,
        confirmed: Boolean = true,
        offered: Boolean = true,
        phraseFailure: Exception? = null,
    ) = RecoveryKeyViewModel(
        identity = identity,
        connectedServer = FakeServer(supportsRecoveryKeys = true),
        offered = MutableStateFlow(offered),
        loadPhrase = {
            phraseFailure?.let { throw it }
            storedPhrase
        },
        isPhraseConfirmed = { confirmed },
    )

    private class FakeServer(
        supportsRecoveryKeys: Boolean,
    ) : ConnectedServer {
        override val info =
            MutableStateFlow(
                ConnectedServerInfo(
                    origin = "https://cloud.logdate.app",
                    displayName = "LogDate Cloud",
                    isLogDateCloud = true,
                    publishesIdentityChanges = false,
                    supportsRecoveryKeys = supportsRecoveryKeys,
                ),
            )

        override suspend fun refresh(): ServerHealth = ServerHealth.Reachable(version = null)
    }

    private class FakeIdentity(
        var registered: String?,
        var statusFailure: Throwable? = null,
        private val deriveFailure: Boolean = false,
        private val registerFailure: IdentityRecoveryFailure? = null,
        var recoverFailure: IdentityRecoveryFailure? = null,
        private val registerGate: CompletableDeferred<Unit>? = null,
    ) : AccountIdentityRepository {
        val registrations = mutableListOf<List<String>>()
        val recoveries = mutableListOf<List<String>>()
        val replacements = mutableListOf<List<String>>()

        override suspend fun getIdentityStatus(): Result<AccountIdentityStatus> {
            statusFailure?.let { return Result.failure(it) }
            return Result.success(
                AccountIdentityStatus(
                    did = "did:plc:alice123",
                    handle = "alice.logdate.app",
                    signingKeyPublicMultibase = "zServer",
                    signingKeyDidKey = "did:key:zServer",
                    plcRecoveryDidKey = registered,
                ),
            )
        }

        override suspend fun derivePlcRecoveryDidKey(recoveryPhrase: String): Result<AccountDerivedPlcRecoveryKey> =
            if (deriveFailure) {
                Result.failure(IllegalArgumentException("Invalid recovery phrase"))
            } else {
                Result.success(AccountDerivedPlcRecoveryKey(DERIVED))
            }

        override suspend fun registerRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey> {
            registerGate?.await()
            registrations += recoveryPhrase
            registerFailure?.let { return Result.failure(IdentityRecoveryException(it, "refused")) }
            registered = DERIVED
            return Result.success(AccountRegisteredPlcRecoveryKey("did:plc:alice123", "alice.logdate.app", DERIVED))
        }

        override suspend fun recoverIdentity(recoveryPhrase: List<String>): Result<AccountImportedSigningKey> {
            recoveries += recoveryPhrase
            recoverFailure?.let { return Result.failure(IdentityRecoveryException(it, "refused")) }
            return Result.success(AccountImportedSigningKey("did:plc:alice123", "alice.logdate.app", "did:key:zFresh"))
        }

        override suspend fun getHostedPlcOperations(): Result<List<AccountHostedPlcOperation>> = unused()

        override suspend fun exportSigningKey(passphrase: String): Result<AccountExportedSigningKey> = unused()

        override suspend fun rotateSigningKey(passphrase: String): Result<AccountRotatedSigningKey> = unused()

        override suspend fun importSigningKey(
            passphrase: String,
            exportedKeyJson: String,
        ): Result<AccountImportedSigningKey> = unused()

        override suspend fun importSigningKeyWithRecovery(
            passphrase: String,
            exportedKeyJson: String,
            recoveryPhrase: String,
        ): Result<AccountImportedSigningKey> = unused()

        override suspend fun replaceRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey> {
            replacements += recoveryPhrase
            registered = DERIVED
            return Result.success(AccountRegisteredPlcRecoveryKey("did:plc:alice123", "alice.logdate.app", DERIVED))
        }

        private fun <T> unused(): Result<T> = error("not used by the recovery key screen")
    }

    private companion object {
        const val DERIVED = "did:key:zDnaeDerived"
    }
}
