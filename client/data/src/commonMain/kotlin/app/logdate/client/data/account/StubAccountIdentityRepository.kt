package app.logdate.client.data.account

import app.logdate.client.repository.account.AccountExportedSigningKey
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.AccountImportedSigningKey
import app.logdate.client.repository.account.AccountRegisteredPlcRecoveryKey
import app.logdate.client.repository.account.AccountRotatedSigningKey
import app.logdate.client.repository.account.ExportedIdentitySigningKeyPayload

/**
 * Stub identity repository for targets that do not wire the hosted identity APIs yet.
 */
class StubAccountIdentityRepository : AccountIdentityRepository {
    override suspend fun getIdentityStatus(): Result<AccountIdentityStatus> =
        Result.success(
            AccountIdentityStatus(
                did = "did:web:desktop.logdate.app",
                handle = "desktop.logdate.app",
                signingKeyPublicMultibase = "zStubPublicKey",
                signingKeyDidKey = "did:key:zStubPublicKey",
            ),
        )

    override suspend fun getHostedPlcOperations(): Result<List<AccountHostedPlcOperation>> = Result.success(emptyList())

    override suspend fun exportSigningKey(passphrase: String): Result<AccountExportedSigningKey> =
        Result.success(
            AccountExportedSigningKey(
                did = "did:web:desktop.logdate.app",
                handle = "desktop.logdate.app",
                exportedKey =
                    ExportedIdentitySigningKeyPayload(
                        algorithm = "P-256",
                        publicKeyMultibase = "zStubPublicKey",
                        publicKeyDidKey = "did:key:zStubPublicKey",
                        encryptedPrivateKey = "stub-encrypted-private-key",
                        salt = "stub-salt",
                        iv = "stub-iv",
                    ),
            ),
        )

    override suspend fun rotateSigningKey(passphrase: String): Result<AccountRotatedSigningKey> =
        Result.success(
            AccountRotatedSigningKey(
                did = "did:web:desktop.logdate.app",
                handle = "desktop.logdate.app",
                previousPublicKeyDidKey = "did:key:zStubPreviousKey",
                exportedKey =
                    ExportedIdentitySigningKeyPayload(
                        algorithm = "P-256",
                        publicKeyMultibase = "zStubRotatedKey",
                        publicKeyDidKey = "did:key:zStubRotatedKey",
                        encryptedPrivateKey = "stub-encrypted-private-key",
                        salt = "stub-salt",
                        iv = "stub-iv",
                    ),
            ),
        )

    override suspend fun importSigningKey(
        passphrase: String,
        exportedKeyJson: String,
    ): Result<AccountImportedSigningKey> =
        Result.success(
            AccountImportedSigningKey(
                did = "did:web:desktop.logdate.app",
                handle = "desktop.logdate.app",
                publicKeyDidKey = "did:key:zStubImportedKey",
            ),
        )

    override suspend fun registerPlcRecoveryKey(recoveryDidKey: String): Result<AccountRegisteredPlcRecoveryKey> =
        Result.success(
            AccountRegisteredPlcRecoveryKey(
                did = "did:web:desktop.logdate.app",
                handle = "desktop.logdate.app",
                recoveryDidKey = recoveryDidKey,
            ),
        )
}
