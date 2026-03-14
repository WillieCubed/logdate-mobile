package app.logdate.client.permissions

import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyRegistrationOptions

/**
 * No-op implementation of [RestoreCredentialManager] for platforms that don't support
 * Android's backup and restore credential system (iOS, Desktop, JVM).
 */
class NoOpRestoreCredentialManager : RestoreCredentialManager {
    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> =
        Result.failure(RestoreCredentialError.BackupUnavailable())

    override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String> =
        Result.failure(RestoreCredentialError.NoCredential())

    override suspend fun clearRestoreCredential(): Result<Unit> = Result.success(Unit)
}
