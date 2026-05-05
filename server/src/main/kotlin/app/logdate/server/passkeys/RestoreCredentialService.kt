package app.logdate.server.passkeys

import app.logdate.server.config.profileAwareBoolEnv
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyChallenge
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyRegistrationResponse
import app.logdate.shared.model.PasskeyUser
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val CHALLENGE_TYPE_REGISTRATION = "restore-registration"
private const val CHALLENGE_TYPE_AUTHENTICATION = "restore-authentication"

/**
 * Standalone WebAuthn service for the restore credential flow.
 *
 * Manages its own challenge map, WebAuthn4J instances, and repository — completely isolated
 * from [WebAuthnPasskeyService]. Restore credentials are not user-visible passkeys and have
 * a different lifecycle: one per user, rotated on re-registration, consumed on authentication.
 */
@OptIn(ExperimentalUuidApi::class)
class RestoreCredentialService(
    private val restoreCredentialRepository: RestoreCredentialRepository,
    val relyingPartyId: String = "logdate.app",
    private val relyingPartyName: String = "LogDate",
    private val origin: String = "https://app.logdate.com",
    private val strictVerificationEnabled: Boolean =
        profileAwareBoolEnv(
            name = "WEBAUTHN_STRICT_VERIFICATION",
            productionDefault = true,
            devDefault = false,
        ),
) {
    private val secureRandom = SecureRandom()
    private val challenges = ConcurrentHashMap<String, PasskeyChallenge>()
    private val objectConverter = ObjectConverter()
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    data class RegistrationResult(
        val success: Boolean,
        val credentialId: String? = null,
        val error: String? = null,
    )

    data class AuthenticationResult(
        val success: Boolean,
        val userId: Uuid? = null,
        val credentialId: String? = null,
        val error: String? = null,
    )

    fun generateRegistrationOptions(
        userId: Uuid,
        username: String,
        displayName: String,
    ): PasskeyRegistrationOptions {
        val challenge = generateChallenge()
        challenges[challenge] =
            PasskeyChallenge(
                challenge = challenge,
                userId = userId,
                type = CHALLENGE_TYPE_REGISTRATION,
                expiresAt = (Clock.System.now() + 5.minutes).toString(),
                isUsed = false,
            )

        val excludeCredentials = runBlocking { restoreCredentialRepository.getCredentialIdsForUser(userId) }

        return PasskeyRegistrationOptions(
            challenge = challenge,
            rpId = relyingPartyId,
            rpName = relyingPartyName,
            user =
                PasskeyUser(
                    id = Base64.getEncoder().encodeToString(userId.toString().toByteArray()),
                    name = username,
                    displayName = displayName,
                ),
            excludeCredentials = excludeCredentials,
            timeout = 300_000L,
        )
    }

    fun verifyRegistration(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): RegistrationResult =
        if (strictVerificationEnabled) {
            verifyRegistrationStrict(userId, challenge, registrationResponse)
        } else {
            verifyRegistrationSimplified(userId, challenge, registrationResponse)
        }

    fun generateAuthOptions(): PasskeyAuthenticationOptions {
        val challenge = generateChallenge()
        challenges[challenge] =
            PasskeyChallenge(
                challenge = challenge,
                userId = Uuid.random(),
                type = CHALLENGE_TYPE_AUTHENTICATION,
                expiresAt = (Clock.System.now() + 5.minutes).toString(),
                isUsed = false,
            )

        return PasskeyAuthenticationOptions(
            challenge = challenge,
            rpId = relyingPartyId,
            allowCredentials = emptyList(),
            timeout = 300_000L,
        )
    }

    fun verifyAuthentication(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse,
    ): AuthenticationResult =
        if (strictVerificationEnabled) {
            verifyAuthenticationStrict(challenge, authenticationResponse)
        } else {
            verifyAuthenticationSimplified(challenge, authenticationResponse)
        }

    private fun verifyRegistrationSimplified(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): RegistrationResult =
        try {
            val challengeData =
                validateChallenge(challenge, CHALLENGE_TYPE_REGISTRATION, userId)
                    ?: return RegistrationResult(success = false, error = "Invalid or expired challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val credentialId = normalizeCredentialId(registrationResponse.id) ?: registrationResponse.id

            runBlocking {
                restoreCredentialRepository.deactivateAllForUser(userId)
                restoreCredentialRepository.store(
                    userId = userId,
                    credentialId = credentialId,
                    publicKey = registrationResponse.response.attestationObject.toByteArray(),
                    signCount = 0L,
                )
            }

            RegistrationResult(success = true, credentialId = credentialId)
        } catch (e: Exception) {
            Napier.e("Restore registration verification failed", e)
            RegistrationResult(success = false, error = "Registration verification failed: ${e.message}")
        }

    private fun verifyRegistrationStrict(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): RegistrationResult =
        try {
            val challengeData =
                validateChallenge(challenge, CHALLENGE_TYPE_REGISTRATION, userId)
                    ?: return RegistrationResult(success = false, error = "Invalid or expired challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val challengeBytes =
                decodeBase64Url(challenge)
                    ?: return RegistrationResult(success = false, error = "Challenge is not valid base64url")
            val attestationObjectBytes =
                decodeBase64Url(registrationResponse.response.attestationObject)
                    ?: return RegistrationResult(success = false, error = "Attestation object is not valid base64url")
            val clientDataJsonBytes =
                decodeBase64Url(registrationResponse.response.clientDataJSON)
                    ?: return RegistrationResult(success = false, error = "Client data is not valid base64url")

            val serverProperty =
                ServerProperty
                    .builder()
                    .origin(Origin(origin))
                    .rpId(relyingPartyId)
                    .challenge(DefaultChallenge(challengeBytes))
                    .build()

            val registrationData =
                webAuthnManager.verify(
                    RegistrationRequest(attestationObjectBytes, clientDataJsonBytes),
                    RegistrationParameters(serverProperty, null, true, true),
                )
            val attestedCredentialData =
                registrationData.attestationObject
                    ?.authenticatorData
                    ?.attestedCredentialData
                    ?: return RegistrationResult(success = false, error = "Attested credential data is missing")

            val canonicalCredentialId = encodeBase64Url(attestedCredentialData.credentialId)
            val publicKeyBytes = attestedCredentialDataConverter.convert(attestedCredentialData)

            runBlocking {
                restoreCredentialRepository.deactivateAllForUser(userId)
                restoreCredentialRepository.store(
                    userId = userId,
                    credentialId = canonicalCredentialId,
                    publicKey = publicKeyBytes,
                    signCount = registrationData.attestationObject?.authenticatorData?.signCount ?: 0L,
                )
            }

            RegistrationResult(success = true, credentialId = canonicalCredentialId)
        } catch (e: DataConversionException) {
            RegistrationResult(success = false, error = "Registration data conversion failed")
        } catch (e: Exception) {
            Napier.e("Restore registration strict verification failed", e)
            RegistrationResult(success = false, error = "Registration verification failed: ${e.message}")
        }

    private fun verifyAuthenticationSimplified(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse,
    ): AuthenticationResult =
        try {
            val challengeData =
                validateChallenge(challenge, CHALLENGE_TYPE_AUTHENTICATION, null)
                    ?: return AuthenticationResult(success = false, error = "Invalid or expired challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val stored =
                runBlocking {
                    restoreCredentialRepository.findByCredentialId(authenticationResponse.id)
                        ?: normalizeCredentialId(authenticationResponse.id)?.let {
                            restoreCredentialRepository.findByCredentialId(it)
                        }
                } ?: return AuthenticationResult(success = false, error = "Restore credential not found")

            runBlocking { restoreCredentialRepository.deactivate(stored.credentialId) }

            AuthenticationResult(success = true, userId = stored.userId, credentialId = stored.credentialId)
        } catch (e: Exception) {
            Napier.e("Restore authentication simplified verification failed", e)
            AuthenticationResult(success = false, error = "Authentication verification failed: ${e.message}")
        }

    private fun verifyAuthenticationStrict(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse,
    ): AuthenticationResult =
        try {
            val challengeData =
                validateChallenge(challenge, CHALLENGE_TYPE_AUTHENTICATION, null)
                    ?: return AuthenticationResult(success = false, error = "Invalid or expired challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val credentialIdBytes =
                decodeBase64Url(authenticationResponse.id)
                    ?: return AuthenticationResult(success = false, error = "Credential ID is not valid base64url")
            val canonicalCredentialId = encodeBase64Url(credentialIdBytes)

            val stored =
                runBlocking {
                    restoreCredentialRepository.findByCredentialId(canonicalCredentialId)
                        ?: restoreCredentialRepository.findByCredentialId(authenticationResponse.id)
                } ?: return AuthenticationResult(success = false, error = "Restore credential not found")

            val attestedCredentialData = attestedCredentialDataConverter.convert(stored.publicKey)
            val authenticator: CredentialRecord =
                CredentialRecordImpl(
                    NoneAttestationStatement(),
                    null,
                    null,
                    null,
                    stored.signCount,
                    attestedCredentialData,
                    null,
                    null,
                    null,
                    null,
                )

            val authenticatorDataBytes =
                decodeBase64Url(authenticationResponse.response.authenticatorData)
                    ?: return AuthenticationResult(success = false, error = "Authenticator data is not valid base64url")
            val clientDataJsonBytes =
                decodeBase64Url(authenticationResponse.response.clientDataJSON)
                    ?: return AuthenticationResult(success = false, error = "Client data is not valid base64url")
            val signatureBytes =
                decodeBase64Url(authenticationResponse.response.signature)
                    ?: return AuthenticationResult(success = false, error = "Signature is not valid base64url")
            val userHandleBytes = authenticationResponse.response.userHandle?.let { decodeBase64Url(it) }

            val authenticationRequest =
                if (userHandleBytes != null) {
                    AuthenticationRequest(credentialIdBytes, userHandleBytes, authenticatorDataBytes, clientDataJsonBytes, signatureBytes)
                } else {
                    AuthenticationRequest(credentialIdBytes, authenticatorDataBytes, clientDataJsonBytes, signatureBytes)
                }

            val challengeBytes =
                decodeBase64Url(challenge)
                    ?: return AuthenticationResult(success = false, error = "Challenge is not valid base64url")
            val serverProperty =
                ServerProperty
                    .builder()
                    .origin(Origin(origin))
                    .rpId(relyingPartyId)
                    .challenge(DefaultChallenge(challengeBytes))
                    .build()

            val authenticationData =
                webAuthnManager.verify(
                    authenticationRequest,
                    AuthenticationParameters(serverProperty, authenticator, listOf(credentialIdBytes), true, true),
                )

            val newSignCount = authenticationData.authenticatorData?.signCount ?: stored.signCount
            runBlocking {
                restoreCredentialRepository.updateSignCount(canonicalCredentialId, newSignCount)
                restoreCredentialRepository.deactivate(canonicalCredentialId)
            }

            AuthenticationResult(success = true, userId = stored.userId, credentialId = canonicalCredentialId)
        } catch (e: DataConversionException) {
            AuthenticationResult(success = false, error = "Authentication data conversion failed")
        } catch (e: Exception) {
            Napier.e("Restore authentication strict verification failed", e)
            AuthenticationResult(success = false, error = "Authentication verification failed: ${e.message}")
        }

    private fun validateChallenge(
        challenge: String,
        expectedType: String,
        expectedUserId: Uuid?,
    ): PasskeyChallenge? {
        val data = challenges[challenge] ?: return null
        if (data.isUsed) return null
        if (data.type != expectedType) return null
        if (expectedUserId != null && data.userId != expectedUserId) return null
        val expiresAt = runCatching { Instant.parse(data.expiresAt) }.getOrNull() ?: return null
        if (Clock.System.now() > expiresAt) return null
        return data
    }

    private fun generateChallenge(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun decodeBase64Url(value: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()

    private fun encodeBase64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun normalizeCredentialId(value: String): String? = decodeBase64Url(value)?.let { encodeBase64Url(it) }
}
