package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyChallenge
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyRegistrationResponse
import app.logdate.shared.model.PasskeyUser
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * WebAuthn passkey service with optional strict cryptographic verification.
 *
 * Strict mode (`WEBAUTHN_STRICT_VERIFICATION=true`) uses WebAuthn4J verification.
 * Non-strict mode keeps a lightweight fallback for local/dev tests.
 */
@OptIn(ExperimentalUuidApi::class)
class WebAuthnPasskeyService(
    private val passkeyRepository: PasskeyRepository = InMemoryPasskeyRepository(),
    val relyingPartyId: String = "logdate.app",
    private val relyingPartyName: String = "LogDate",
    private val origin: String = "https://app.logdate.com",
    private val strictVerificationEnabled: Boolean = readBooleanEnv("WEBAUTHN_STRICT_VERIFICATION", false),
) {
    private val secureRandom = SecureRandom()
    private val challenges = ConcurrentHashMap<String, PasskeyChallenge>()

    private val objectConverter = ObjectConverter()
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    data class RegistrationResult(
        val success: Boolean,
        val credentialId: String? = null,
        val passkey: PasskeyInfo? = null,
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
        excludeCredentials: List<String> = emptyList(),
    ): PasskeyRegistrationOptions {
        val challenge = generateChallenge()
        val expiresAt = Clock.System.now() + 5.minutes

        challenges[challenge] =
            PasskeyChallenge(
                challenge = challenge,
                userId = userId,
                type = "registration",
                expiresAt = expiresAt.toString(),
                isUsed = false,
            )

        return PasskeyRegistrationOptions(
            challenge = challenge,
            user =
                PasskeyUser(
                    id = Base64.getEncoder().encodeToString(userId.toString().toByteArray()),
                    name = username,
                    displayName = displayName,
                ),
            excludeCredentials = excludeCredentials.ifEmpty { getUserCredentials(userId) },
            timeout = 300_000L,
        )
    }

    fun generateAuthenticationOptions(
        userId: Uuid? = null,
        allowedCredentials: List<String> = emptyList(),
    ): PasskeyAuthenticationOptions {
        val challenge = generateChallenge()
        val challengeUserId = userId ?: Uuid.random()
        val expiresAt = Clock.System.now() + 5.minutes

        challenges[challenge] =
            PasskeyChallenge(
                challenge = challenge,
                userId = challengeUserId,
                type = "authentication",
                expiresAt = expiresAt.toString(),
                isUsed = false,
            )

        val allowCredentials =
            when {
                allowedCredentials.isNotEmpty() -> allowedCredentials
                userId != null -> getUserCredentials(userId)
                else -> emptyList()
            }

        return PasskeyAuthenticationOptions(
            challenge = challenge,
            allowCredentials = allowCredentials,
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

    fun verifyAuthentication(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse,
    ): AuthenticationResult =
        if (strictVerificationEnabled) {
            verifyAuthenticationStrict(challenge, authenticationResponse)
        } else {
            verifyAuthenticationSimplified(challenge, authenticationResponse)
        }

    fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> = runBlocking { passkeyRepository.getPasskeysForUser(userId) }

    fun deletePasskey(
        credentialId: String,
        userId: Uuid,
    ): Boolean = runBlocking { passkeyRepository.deactivatePasskey(credentialId, userId) }

    fun credentialBelongsToUser(
        credentialId: String,
        userId: Uuid,
    ): Boolean = runBlocking { passkeyRepository.credentialBelongsToUser(credentialId, userId) }

    fun getUserCredentials(userId: Uuid): List<String> = runBlocking { passkeyRepository.getCredentialIdsForUser(userId) }

    private fun verifyRegistrationSimplified(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): RegistrationResult {
        return try {
            val challengeData =
                validateChallenge(
                    challenge = challenge,
                    expectedType = "registration",
                    expectedUserId = userId,
                ) ?: return RegistrationResult(success = false, error = "Invalid challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val credentialId = normalizeCredentialId(registrationResponse.id) ?: registrationResponse.id
            val passkey =
                PasskeyInfo(
                    id = Uuid.random(),
                    credentialId = credentialId,
                    nickname = relyingPartyName,
                    deviceType = "platform",
                    createdAt = Clock.System.now(),
                    lastUsedAt = null,
                    isActive = true,
                )

            val stored =
                runBlocking {
                    passkeyRepository.storePasskey(
                        userId = userId,
                        credentialId = credentialId,
                        publicKey = registrationResponse.response.attestationObject.toByteArray(),
                        signCount = 0L,
                        info = passkey,
                    )
                }
            if (!stored) {
                return RegistrationResult(success = false, error = "Failed to store passkey")
            }

            RegistrationResult(success = true, credentialId = credentialId, passkey = passkey)
        } catch (e: Exception) {
            RegistrationResult(success = false, error = "Registration verification failed: ${e.message}")
        }
    }

    private fun verifyRegistrationStrict(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): RegistrationResult {
        return try {
            val challengeData =
                validateChallenge(
                    challenge = challenge,
                    expectedType = "registration",
                    expectedUserId = userId,
                ) ?: return RegistrationResult(success = false, error = "Invalid challenge")
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

            val registrationRequest = RegistrationRequest(attestationObjectBytes, clientDataJsonBytes)
            val registrationParameters = RegistrationParameters(serverProperty, true, true)
            val registrationData = webAuthnManager.verify(registrationRequest, registrationParameters)
            val attestationObject =
                registrationData.attestationObject
                    ?: return RegistrationResult(success = false, error = "Attestation object is missing")
            val authenticatorData = attestationObject.authenticatorData

            val attestedCredentialData =
                authenticatorData.attestedCredentialData
                    ?: return RegistrationResult(success = false, error = "Attested credential data is missing")

            val canonicalCredentialId = encodeBase64Url(attestedCredentialData.credentialId)
            val normalizedCredentialId = normalizeCredentialId(registrationResponse.id)
            if (normalizedCredentialId != null && normalizedCredentialId != canonicalCredentialId) {
                return RegistrationResult(success = false, error = "Credential ID mismatch")
            }

            val passkey =
                PasskeyInfo(
                    id = Uuid.random(),
                    credentialId = canonicalCredentialId,
                    nickname = relyingPartyName,
                    deviceType = "platform",
                    createdAt = Clock.System.now(),
                    lastUsedAt = null,
                    isActive = true,
                )

            val stored =
                runBlocking {
                    passkeyRepository.storePasskey(
                        userId = userId,
                        credentialId = canonicalCredentialId,
                        publicKey = attestedCredentialDataConverter.convert(attestedCredentialData),
                        signCount = authenticatorData.signCount,
                        info = passkey,
                    )
                }
            if (!stored) {
                return RegistrationResult(success = false, error = "Failed to store passkey")
            }

            RegistrationResult(success = true, credentialId = canonicalCredentialId, passkey = passkey)
        } catch (e: DataConversionException) {
            RegistrationResult(success = false, error = "Registration data conversion failed")
        } catch (e: Exception) {
            RegistrationResult(success = false, error = "Registration verification failed: ${e.message}")
        }
    }

    private fun verifyAuthenticationSimplified(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse,
    ): AuthenticationResult {
        return try {
            val challengeData =
                validateChallenge(
                    challenge = challenge,
                    expectedType = "authentication",
                    expectedUserId = null,
                ) ?: return AuthenticationResult(success = false, error = "Invalid challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val (userId, storedData) =
                findStoredPasskey(authenticationResponse.id)
                    ?: return AuthenticationResult(success = false, error = "Credential not found")

            runBlocking {
                passkeyRepository.updateSignCount(storedData.credentialId, storedData.signCount + 1L)
            }

            AuthenticationResult(success = true, userId = userId, credentialId = storedData.credentialId)
        } catch (e: Exception) {
            AuthenticationResult(success = false, error = "Authentication verification failed: ${e.message}")
        }
    }

    private fun verifyAuthenticationStrict(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse,
    ): AuthenticationResult {
        return try {
            val challengeData =
                validateChallenge(
                    challenge = challenge,
                    expectedType = "authentication",
                    expectedUserId = null,
                ) ?: return AuthenticationResult(success = false, error = "Invalid challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val credentialIdBytes =
                decodeBase64Url(authenticationResponse.id)
                    ?: return AuthenticationResult(success = false, error = "Credential ID is not valid base64url")
            val canonicalCredentialId = encodeBase64Url(credentialIdBytes)
            val (userId, storedData) =
                findStoredPasskey(canonicalCredentialId)
                    ?: return AuthenticationResult(success = false, error = "Credential not found")

            val attestedCredentialData = attestedCredentialDataConverter.convert(storedData.publicKey)
            val authenticator =
                AuthenticatorImpl(
                    attestedCredentialData,
                    NoneAttestationStatement(),
                    storedData.signCount,
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
                    AuthenticationRequest(
                        credentialIdBytes,
                        userHandleBytes,
                        authenticatorDataBytes,
                        clientDataJsonBytes,
                        signatureBytes,
                    )
                } else {
                    AuthenticationRequest(
                        credentialIdBytes,
                        authenticatorDataBytes,
                        clientDataJsonBytes,
                        signatureBytes,
                    )
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

            val authenticationParameters =
                AuthenticationParameters(
                    serverProperty,
                    authenticator,
                    listOf(credentialIdBytes),
                    true,
                    true,
                )

            val authenticationData = webAuthnManager.verify(authenticationRequest, authenticationParameters)
            val newSignCount = authenticationData.authenticatorData?.signCount ?: storedData.signCount
            runBlocking {
                passkeyRepository.updateSignCount(canonicalCredentialId, newSignCount)
            }

            AuthenticationResult(success = true, userId = userId, credentialId = canonicalCredentialId)
        } catch (e: DataConversionException) {
            AuthenticationResult(success = false, error = "Authentication data conversion failed")
        } catch (e: Exception) {
            AuthenticationResult(success = false, error = "Authentication verification failed: ${e.message}")
        }
    }

    private fun validateChallenge(
        challenge: String,
        expectedType: String,
        expectedUserId: Uuid?,
    ): PasskeyChallenge? {
        val challengeData = challenges[challenge] ?: return null
        if (challengeData.isUsed) {
            return null
        }
        if (challengeData.type != expectedType) {
            return null
        }
        if (expectedUserId != null && challengeData.userId != expectedUserId) {
            return null
        }
        val expiresAt = runCatching { Instant.parse(challengeData.expiresAt) }.getOrNull()
        if (expiresAt == null || Clock.System.now() > expiresAt) {
            return null
        }
        return challengeData
    }

    private fun findStoredPasskey(credentialId: String): Pair<Uuid, StoredPasskeyData>? {
        val normalized = normalizeCredentialId(credentialId)
        return runBlocking {
            passkeyRepository.getPasskeyByCredentialId(credentialId)
                ?: (normalized?.takeIf { it != credentialId }?.let { passkeyRepository.getPasskeyByCredentialId(it) })
        }
    }

    private fun generateChallenge(): String {
        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
    }

    private fun decodeBase64Url(value: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()

    private fun encodeBase64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun normalizeCredentialId(value: String): String? = decodeBase64Url(value)?.let { encodeBase64Url(it) }
}

private fun readBooleanEnv(
    name: String,
    defaultValue: Boolean,
    readEnv: (String) -> String? = System::getenv,
): Boolean {
    val raw = readEnv(name) ?: return defaultValue
    return raw.equals("true", ignoreCase = true) ||
        raw.equals("yes", ignoreCase = true) ||
        raw == "1"
}
