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
    fun `a registered key with no phrase on this device asks for the phrase`() =
        runTest {
            val state = ready(viewModel(FakeIdentity(registered = DERIVED), storedPhrase = null))

            assertEquals(KeyStatus.ON_WITHOUT_PHRASE, state.key)
            assertFalse(state.canReplaceServerKey)
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
    fun `a phrase this device cannot use is reported`() =
        runTest {
            val state = ready(viewModel(FakeIdentity(registered = null, deriveFailure = true)))

            assertEquals(IdentityRecoveryFailure.DEVICE_ERROR, state.error)
        }

    @Test
    fun `the row is offered only when the flag is on and the server supports recovery keys`() =
        runTest {
            val flags = FakeFeatureFlagStore()
            val supporting = FakeServer(supportsRecoveryKeys = true)
            val notSupporting = FakeServer(supportsRecoveryKeys = false)

            assertFalse(recoveryKeyOffered(flags, supporting).first(), "the flag is off by default")
            flags.setEnabled(FeatureFlag.IDENTITY_RECOVERY_KEY, true)
            assertTrue(recoveryKeyOffered(flags, supporting).first())
            assertFalse(recoveryKeyOffered(flags, notSupporting).first(), "a server without the capability never shows it")
        }

    private fun ready(viewModel: RecoveryKeyViewModel): RecoveryKeyUiState.Ready = assertIs(viewModel.state.value)

    private fun viewModel(
        identity: FakeIdentity,
        storedPhrase: List<String>? = phrase,
        confirmed: Boolean = true,
    ) = RecoveryKeyViewModel(
        identity = identity,
        connectedServer = FakeServer(supportsRecoveryKeys = true),
        loadPhrase = { storedPhrase },
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
        private val recoverFailure: IdentityRecoveryFailure? = null,
        private val registerGate: CompletableDeferred<Unit>? = null,
    ) : AccountIdentityRepository {
        val registrations = mutableListOf<List<String>>()
        val recoveries = mutableListOf<List<String>>()

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

        override suspend fun registerPlcRecoveryKey(recoveryDidKey: String): Result<AccountRegisteredPlcRecoveryKey> = unused()

        private fun <T> unused(): Result<T> = error("not used by the recovery key screen")
    }

    private companion object {
        const val DERIVED = "did:key:zDnaeDerived"
    }
}
