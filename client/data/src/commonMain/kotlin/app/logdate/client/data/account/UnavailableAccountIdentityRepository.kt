package app.logdate.client.data.account

import app.logdate.client.repository.account.AccountDerivedPlcRecoveryKey
import app.logdate.client.repository.account.AccountExportedSigningKey
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.AccountImportedSigningKey
import app.logdate.client.repository.account.AccountRegisteredPlcRecoveryKey
import app.logdate.client.repository.account.AccountRotatedSigningKey

/**
 * Identity repository for targets where hosted identity APIs are not wired.
 */
class UnavailableAccountIdentityRepository : AccountIdentityRepository {
    override suspend fun getIdentityStatus(): Result<AccountIdentityStatus> = unavailable()

    override suspend fun getHostedPlcOperations(): Result<List<AccountHostedPlcOperation>> = unavailable()

    override suspend fun exportSigningKey(passphrase: String): Result<AccountExportedSigningKey> = unavailable()

    override suspend fun rotateSigningKey(passphrase: String): Result<AccountRotatedSigningKey> = unavailable()

    override suspend fun importSigningKey(
        passphrase: String,
        exportedKeyJson: String,
    ): Result<AccountImportedSigningKey> = unavailable()

    override suspend fun importSigningKeyWithRecovery(
        passphrase: String,
        exportedKeyJson: String,
        recoveryPhrase: String,
    ): Result<AccountImportedSigningKey> = unavailable()

    override suspend fun derivePlcRecoveryDidKey(recoveryPhrase: String): Result<AccountDerivedPlcRecoveryKey> = unavailable()

    override suspend fun registerPlcRecoveryKey(recoveryDidKey: String): Result<AccountRegisteredPlcRecoveryKey> = unavailable()

    private fun <T> unavailable(): Result<T> =
        Result.failure(UnsupportedOperationException("Hosted identity APIs are unavailable on this target."))
}
