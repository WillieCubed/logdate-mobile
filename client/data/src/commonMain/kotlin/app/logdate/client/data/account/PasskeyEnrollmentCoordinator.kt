package app.logdate.client.data.account

import app.logdate.client.networking.AccountSignInIdentity
import app.logdate.client.networking.AddPasskeyRequest
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.shared.model.PasskeyInfo
import io.github.aakira.napier.Napier
import kotlin.time.Instant

/**
 * Adds passkeys to the signed-in account and reads the other ways it can sign in.
 *
 * Adding a passkey is a three-step ceremony: the server issues registration options, the platform
 * creates the credential, and the server stores it. The session is checked before the platform is
 * asked for anything, so nobody is shown a passkey prompt that cannot finish.
 */
internal class PasskeyEnrollmentCoordinator(
    private val apiClient: PasskeyApiClientContract,
    private val passkeyManager: PasskeyManager,
    private val credentialCodec: PasskeyCredentialCodec,
    private val sessionRefreshCoordinator: AccountSessionRefreshCoordinator,
    private val deviceName: () -> String?,
) {
    suspend fun addPasskey(): Result<PasskeyInfo> {
        val options =
            sessionRefreshCoordinator
                .authorized { accessToken -> apiClient.beginAddPasskey(accessToken) }
                .getOrElse { return Result.failure(it) }

        val credentialJson =
            passkeyManager
                .registerPasskey(options)
                .getOrElse { return Result.failure(it) }

        val credential =
            runCatching { credentialCodec.parseCredentialResponse(credentialJson) }
                .getOrElse { error ->
                    Napier.w("The platform returned a passkey credential that could not be read", error)
                    return Result.failure(error)
                }

        val request =
            AddPasskeyRequest(
                challenge = options.challenge,
                credential = credential,
                nickname = deviceName(),
            )
        return sessionRefreshCoordinator.authorized { accessToken -> apiClient.completeAddPasskey(accessToken, request) }
    }

    suspend fun listLinkedSignInProviders(): Result<List<LinkedSignInProvider>> =
        sessionRefreshCoordinator
            .authorized { accessToken -> apiClient.listSignInIdentities(accessToken) }
            .map { identities -> identities.mapNotNull { it.toLinkedSignInProvider() } }
}

private fun AccountSignInIdentity.toLinkedSignInProvider(): LinkedSignInProvider? {
    val kind =
        when (provider.lowercase()) {
            PASSKEY_PROVIDER -> return null
            GOOGLE_PROVIDER -> LinkedSignInProvider.Kind.GOOGLE
            else -> LinkedSignInProvider.Kind.OTHER
        }
    val linkedAt =
        runCatching { Instant.parse(createdAt) }.getOrElse { error ->
            Napier.w("Sign-in method $provider has an unreadable link date: $createdAt", error)
            return null
        }
    return LinkedSignInProvider(
        kind = kind,
        email = email,
        linkedAt = linkedAt,
        lastSignInAt = lastSignInAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}

private const val PASSKEY_PROVIDER = "passkey"
private const val GOOGLE_PROVIDER = "google"
