package app.logdate.client.permissions

import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyRegistrationOptions

/**
 * Manages Android Restore Credentials — device backup credentials that allow silent
 * re-authentication on a new device after data restoration.
 *
 * This is distinct from [PasskeyManager], which handles user-facing passkey authentication.
 * Restore credentials are platform backup infrastructure; they are created automatically
 * after account creation and redeemed silently on restore — no user interaction required.
 *
 * Non-Android platforms provide a no-op implementation.
 */
interface RestoreCredentialManager {
    /**
     * Create a restore key backed up to the user's encrypted cloud backup.
     *
     * @param options WebAuthn registration options from the server
     * @return Registration response JSON to send back to the server, or failure if the
     *   device does not support E2EE backup (treat as non-fatal)
     */
    suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String>

    /**
     * Retrieve the restore credential from the device's cloud backup.
     *
     * @param options WebAuthn authentication options from the server
     * @return Authentication response JSON, or failure with [RestoreCredentialError.NoCredential]
     *   if no restore credential exists on this device (fresh install or no backup)
     */
    suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String>

    /**
     * Clear the restore credential. Should be called on sign-out.
     * Failures are non-fatal — best effort only.
     */
    suspend fun clearRestoreCredential(): Result<Unit>
}

sealed class RestoreCredentialError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** No restore credential found on this device (fresh install or no backup). */
    class NoCredential(
        cause: Throwable? = null,
    ) : RestoreCredentialError("No restore credential available", cause)

    /** Device does not support E2EE backup. Restore key creation should be silently skipped. */
    class BackupUnavailable(
        cause: Throwable? = null,
    ) : RestoreCredentialError("E2EE backup not available on this device", cause)

    /** User cancelled the credential operation. */
    class Cancelled(
        cause: Throwable? = null,
    ) : RestoreCredentialError("Restore credential operation cancelled", cause)

    /** Generic failure. */
    class Unknown(
        message: String,
        cause: Throwable? = null,
    ) : RestoreCredentialError(message, cause)
}
