package app.logdate.client.repository.account

import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * Repository interface for passkey-based account management operations.
 *
 * This repository handles account creation, authentication, and management
 * using WebAuthn passkeys for LogDate Cloud.
 */
interface PasskeyAccountRepository {
    /**
     * Current authenticated user account information.
     */
    val currentAccount: StateFlow<LogDateAccount?>

    /**
     * Current authentication status.
     */
    val isAuthenticated: StateFlow<Boolean>

    /**
     * Create a new LogDate Cloud account using passkey authentication.
     *
     * @param request The account creation request containing user details
     * @return Result containing the created account or error
     */
    suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount>

    /**
     * Authenticate with an existing LogDate Cloud account using passkeys.
     *
     * @param username Optional username hint for authentication
     * @param adoptLocalData the user has been told this installation's existing entries will
     * become part of the account being signed into, and agreed. Signing in without it fails with
     * [LocalDataAdoptionRequiredException] when there is anything to adopt.
     * @return Result containing the authenticated account or error
     */
    suspend fun authenticateWithPasskey(
        username: String? = null,
        adoptLocalData: Boolean = false,
    ): Result<LogDateAccount>

    /**
     * Check if a username is available for new account registration.
     *
     * @param username The username to check
     * @return Result containing true if available, false if taken
     */
    suspend fun checkUsernameAvailability(username: String): Result<Boolean>

    /**
     * Sign out the current user and clear stored authentication data.
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Get the currently authenticated account information.
     *
     * @return The current account or null if not authenticated
     */
    suspend fun getCurrentAccount(): LogDateAccount?

    /**
     * Refresh account information from the server.
     *
     * @return Result containing updated account information
     */
    suspend fun getAccountInfo(): Result<LogDateAccount>

    /**
     * Refresh the current authentication session using stored refresh token.
     *
     * @return Result indicating success or failure of the refresh operation
     */
    suspend fun refreshAuthentication(): Result<Unit>

    /**
     * Delete a specific passkey credential from the user's account.
     *
     * @param credentialId The ID of the passkey credential to delete
     * @return Result indicating success or failure of the deletion operation
     */
    suspend fun deletePasskey(credentialId: String): Result<Unit>

    /**
     * Lists this account's passkeys with the detail the account payload does not carry.
     *
     * The account knows only credential IDs, which cannot tell someone which of their devices a
     * credential belongs to -- and that is the decision the settings screen asks them to make.
     */
    suspend fun listPasskeys(): Result<List<PasskeyInfo>>

    /**
     * Creates a passkey on this device and adds it to the signed-in account, named after the
     * device so the passkey list can tell the person which one it is.
     *
     * A platform failure -- including the person cancelling -- is passed on as the platform's
     * `PasskeyException`, so the caller can tell a cancellation from an error.
     */
    suspend fun addPasskey(): Result<PasskeyInfo> = Result.failure(UnsupportedOperationException("Adding passkeys is not supported"))

    /**
     * Lists the non-passkey ways this account can sign in, such as a linked Google account.
     * Passkeys come from [listPasskeys].
     */
    suspend fun listLinkedSignInProviders(): Result<List<LinkedSignInProvider>> =
        Result.failure(UnsupportedOperationException("Listing sign-in methods is not supported"))

    /**
     * Permanently deletes the signed-in account and everything synced to it on the server, then
     * signs this device out. The journal on this device is left alone. A refusal leaves the
     * device signed in.
     */
    suspend fun deleteAccount(): Result<Unit> = Result.failure(UnsupportedOperationException("Deleting accounts is not supported"))

    /**
     * Create a restore key backed up to the device's encrypted cloud backup.
     * Should be called after successful account creation.
     * Non-fatal — returns success even if the device does not support E2EE backup.
     */
    suspend fun createRestoreKey(): Result<Unit>

    /**
     * Attempt to silently sign in using a restore credential from the device's cloud backup.
     * Returns failure if no restore credential is available (fresh install, no backup, or
     * the server does not support restore credentials).
     */
    suspend fun signInWithRestoreKey(): Result<LogDateAccount>

    /**
     * Delete the restore credential. Should be called on sign-out.
     * Non-fatal — failures are silently ignored.
     */
    suspend fun deleteRestoreKey(): Result<Unit>

    /**
     * Create a LogDate Cloud account from a Google account. Obtains a Google ID token through the
     * platform credential flow and exchanges it with the server. Defaults to failure so platforms
     * and test doubles without Google support don't need to implement it.
     */
    suspend fun signUpWithGoogle(
        username: String? = null,
        displayName: String? = null,
    ): Result<LogDateAccount> = Result.failure(UnsupportedOperationException("Google sign-up is not supported"))

    /**
     * Authenticate with an existing LogDate Cloud account using Google.
     */
    suspend fun signInWithGoogle(): Result<LogDateAccount> =
        Result.failure(UnsupportedOperationException("Google sign-in is not supported"))
}

/**
 * Request data for creating a new LogDate Cloud account.
 */
data class AccountCreationRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val email: String? = null,
)

/**
 * This installation holds entries that no account has claimed. Signing in would make them part of
 * the account, which is usually what the user wants on a second device -- but not something to do
 * behind their back.
 */
class LocalDataAdoptionRequiredException : IllegalStateException("Signing in would add this device's existing entries to the account")

/**
 * A way to sign in to the account other than a passkey.
 *
 * @property email the address the provider vouched for, when it shared one
 * @property linkedAt when the provider was connected to the account
 * @property lastSignInAt the last time the account was signed in to through this provider
 */
data class LinkedSignInProvider(
    val kind: Kind,
    val email: String?,
    val linkedAt: Instant,
    val lastSignInAt: Instant?,
) {
    enum class Kind {
        GOOGLE,

        /** A provider this version of the app has no name for. */
        OTHER,
    }
}

/** An account call that needs a signed-in session was made without one. */
class NotSignedInException : IllegalStateException("No active session")
