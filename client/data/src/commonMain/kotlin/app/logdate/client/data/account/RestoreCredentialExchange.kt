package app.logdate.client.data.account

import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.model.BeginAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.PasskeyAuthenticationOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

/** The credential ceremony alone, without journal, account-manager, or session side effects. */
internal class RestoreCredentialExchange(
    private val begin: suspend () -> Result<BeginAuthenticationData>,
    private val getCredential: suspend (PasskeyAuthenticationOptions) -> Result<String>,
    private val complete: suspend (CompleteAuthenticationRequest) -> Result<CompleteAuthenticationData>,
    private val isCurrent: suspend () -> Boolean = { true },
) {
    constructor(api: PasskeyApiClientContract, credentials: RestoreCredentialManager, isCurrent: suspend () -> Boolean = { true }) : this(
        api::beginRestoreSignIn,
        credentials::getRestoreCredential,
        api::completeRestoreSignIn,
        isCurrent,
    )

    suspend operator fun invoke(): Result<CompleteAuthenticationData> =
        try {
            check(isCurrent()) { "Restore configuration changed" }
            val options = begin().getOrThrow()
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Restore configuration changed" }
            val credential =
                getCredential(
                    PasskeyAuthenticationOptions(options.challenge, options.rpId, options.allowCredentials.map { it.id }, options.timeout),
                ).getOrThrow()
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Restore configuration changed" }
            val assertion = PasskeyCredentialCodec(Json { ignoreUnknownKeys = true }).parseAssertionResponse(credential)
            val authenticated = complete(CompleteAuthenticationRequest(assertion, options.challenge)).getOrThrow()
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Restore configuration changed" }
            Result.success(authenticated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Result.failure(error)
        }
}
