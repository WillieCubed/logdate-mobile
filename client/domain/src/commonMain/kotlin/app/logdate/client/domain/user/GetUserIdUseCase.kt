package app.logdate.client.domain.user

import app.logdate.client.device.identity.CanonicalOwnerProvider

/**
 * Use case for retrieving the current user ID.
 *
 * A user ID is the local canonical owner ID. Cloud credentials and the device
 * identifier are intentionally separate identities and must never replace it.
 */
class GetUserIdUseCase(
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
) {
    /**
     * Gets the current user ID.
     * @return Success with user ID if available, Error otherwise
     */
    suspend operator fun invoke(): UserIdResult =
        try {
            UserIdResult.Success(canonicalOwnerProvider.getCanonicalOwnerId())
        } catch (e: Exception) {
            UserIdResult.Error(e.message ?: "Failed to retrieve user ID")
        }

    /**
     * Result types for user ID requests.
     */
    sealed class UserIdResult {
        /** Successful retrieval of user ID. */
        data class Success(
            val userId: String,
        ) : UserIdResult()

        /** Error occurred retrieving user ID. */
        data class Error(
            val message: String,
        ) : UserIdResult()
    }
}
