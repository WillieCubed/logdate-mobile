package app.logdate.client.domain.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.shared.model.EntitlementResponse
import io.github.aakira.napier.Napier

/**
 * Reads the caller's current cloud entitlement from the server.
 *
 * Used by Settings to render the "Your plan" row, by the quota UI to show "X of Y used", and by
 * the billing flow to know whether a tier change has actually taken effect after a checkout.
 *
 * The server returns a [EntitlementResponse] whose [EntitlementResponse.status] is
 * `SELF_HOST` when billing isn't configured; the UI should treat that as "no billing surface at
 * all" rather than "free tier".
 */
class GetCurrentEntitlementUseCase(
    private val sessionStorage: SessionStorage,
    private val apiClient: PasskeyApiClientContract,
) {
    sealed class Result {
        data class Success(
            val entitlement: EntitlementResponse,
        ) : Result()

        /** No signed-in session; nothing to fetch. Caller should treat as local-only mode. */
        data object NotSignedIn : Result()

        data class Error(
            val message: String,
        ) : Result()
    }

    suspend operator fun invoke(): Result {
        val session = sessionStorage.getSession() ?: return Result.NotSignedIn
        val response = apiClient.getEntitlement(session.accessToken)
        return response.fold(
            onSuccess = { Result.Success(it) },
            onFailure = {
                Napier.w("getEntitlement failed", it)
                Result.Error(it.message ?: "Failed to read entitlement")
            },
        )
    }
}
