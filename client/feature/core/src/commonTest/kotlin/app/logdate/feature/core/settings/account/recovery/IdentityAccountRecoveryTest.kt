package app.logdate.feature.core.settings.account.recovery

import app.logdate.client.repository.account.AccountDerivedPlcRecoveryKey
import app.logdate.client.repository.account.AccountExportedSigningKey
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.AccountImportedSigningKey
import app.logdate.client.repository.account.AccountRegisteredPlcRecoveryKey
import app.logdate.client.repository.account.AccountRotatedSigningKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityAccountRecoveryTest {
    private val phrase = List(12) { "word$it" }

    @Test
    fun `an account without a registered phrase needs setup and registers this one`() =
        runTest {
            val identity = FakeIdentity(registered = null)
            val recovery = IdentityAccountRecovery(identity, flowOf(true))

            assertEquals(AccountRecoveryStatus.NEEDS_SETUP, recovery.status(phrase).getOrThrow())
            recovery.finishSetup(phrase).getOrThrow()

            assertEquals(listOf(phrase), identity.registrations)
            assertTrue(identity.replacements.isEmpty())
            assertEquals(AccountRecoveryStatus.READY, recovery.status(phrase).getOrThrow())
        }

    @Test
    fun `a phrase from another device needs setup and replaces it with this one`() =
        runTest {
            val identity = FakeIdentity(registered = "did:key:zOther")
            val recovery = IdentityAccountRecovery(identity, flowOf(true))

            assertEquals(AccountRecoveryStatus.NEEDS_SETUP, recovery.status(phrase).getOrThrow())
            recovery.finishSetup(phrase).getOrThrow()

            assertEquals(listOf(phrase), identity.replacements)
            assertTrue(identity.registrations.isEmpty())
        }

    @Test
    fun `finishing setup again changes nothing once this phrase is registered`() =
        runTest {
            val identity = FakeIdentity(registered = DERIVED)
            val recovery = IdentityAccountRecovery(identity, flowOf(true))

            assertEquals(AccountRecoveryStatus.READY, recovery.status(phrase).getOrThrow())
            recovery.finishSetup(phrase).getOrThrow()

            assertTrue(identity.registrations.isEmpty())
            assertTrue(identity.replacements.isEmpty())
        }

    private class FakeIdentity(
        var registered: String?,
    ) : AccountIdentityRepository {
        val registrations = mutableListOf<List<String>>()
        val replacements = mutableListOf<List<String>>()

        override suspend fun getIdentityStatus(): Result<AccountIdentityStatus> =
            Result.success(
                AccountIdentityStatus(
                    did = "did:plc:alice123",
                    handle = "alice.logdate.app",
                    signingKeyPublicMultibase = "zServer",
                    signingKeyDidKey = "did:key:zServer",
                    plcRecoveryDidKey = registered,
                ),
            )

        override suspend fun derivePlcRecoveryDidKey(recoveryPhrase: String): Result<AccountDerivedPlcRecoveryKey> =
            Result.success(AccountDerivedPlcRecoveryKey(DERIVED))

        override suspend fun registerRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey> {
            registrations += recoveryPhrase
            registered = DERIVED
            return Result.success(AccountRegisteredPlcRecoveryKey("did:plc:alice123", "alice.logdate.app", DERIVED))
        }

        override suspend fun replaceRecoveryKey(recoveryPhrase: List<String>): Result<AccountRegisteredPlcRecoveryKey> {
            replacements += recoveryPhrase
            registered = DERIVED
            return Result.success(AccountRegisteredPlcRecoveryKey("did:plc:alice123", "alice.logdate.app", DERIVED))
        }

        override suspend fun recoverIdentity(recoveryPhrase: List<String>): Result<AccountImportedSigningKey> = unused()

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

        private fun <T> unused(): Result<T> = error("not used by account recovery")
    }

    private companion object {
        const val DERIVED = "did:key:zDnaeDerived"
    }
}
