package app.logdate.server.passkeys

import app.logdate.server.config.profileAwareBoolEnv
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyChallenge
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyRegistrationResponse
import app.logdate.shared.model.PasskeyUser
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
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
 * Strict mode uses WebAuthn4J verification. Production profiles default to strict; development and
 * test profiles default to the lightweight fallback so fixture challenges don't require real
 * attestations. Set `WEBAUTHN_STRICT_VERIFICATION` explicitly to override the profile default.
 */
@OptIn(ExperimentalUuidApi::class)
class WebAuthnPasskeyService(
    private val passkeyRepository: PasskeyRepository = InMemoryPasskeyRepository(),
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
        val passkey: PasskeyInfo? = null,
        val error: String? = null,
    )

    /**
     * Output of a successful crypto-only registration verification. The caller (typically the
     * signup route handler) decides when to actually persist this; that lets the account row be
     * created first so the passkeys → accounts foreign key holds.
     */
    data class VerifiedRegistration(
        val credentialId: String,
        val publicKey: ByteArray,
        val signCount: Long,
        val passkey: PasskeyInfo,
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
            rpId = relyingPartyId,
            rpName = relyingPartyName,
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
            rpId = relyingPartyId,
            allowCredentials = allowCredentials,
            timeout = 300_000L,
        )
    }

    /**
     * Two-step convenience wrapper that verifies the registration and immediately stores it.
     * Suitable for callers that already have an `accounts` row for [userId]. New signup flows
     * should prefer [verifyRegistrationOnly] + [storeVerifiedPasskey] so they can create the
     * account row before inserting the passkey (which carries an FK to `accounts`).
     */
    fun verifyRegistration(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): RegistrationResult {
        val verified = verifyRegistrationOnly(userId, challenge, registrationResponse)
        return when (verified) {
            is VerificationOutcome.Failure -> RegistrationResult(success = false, error = verified.error)
            is VerificationOutcome.Success -> {
                val data = verified.data
                val storeResult = runCatching { storeVerifiedPasskey(userId, data) }
                when {
                    storeResult.isFailure -> {
                        val cause = storeResult.exceptionOrNull()
                        RegistrationResult(
                            success = false,
                            error = "Registration verification failed: ${cause?.message}",
                        )
                    }
                    storeResult.getOrDefault(false) ->
                        RegistrationResult(success = true, credentialId = data.credentialId, passkey = data.passkey)
                    else ->
                        RegistrationResult(success = false, error = "Failed to store passkey")
                }
            }
        }
    }

    /**
     * Verifies a registration response cryptographically without writing to the database. Returns
     * a [VerifiedRegistration] the caller can hand back to [storeVerifiedPasskey] once the
     * account row exists. Marking the challenge consumed is part of verification, so a failed
     * verify will not leave the challenge re-usable.
     */
    fun verifyRegistrationOnly(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): VerificationOutcome =
        if (strictVerificationEnabled) {
            verifyRegistrationStrictOnly(userId, challenge, registrationResponse)
        } else {
            verifyRegistrationSimplifiedOnly(userId, challenge, registrationResponse)
        }

    /** Persists a previously-verified passkey. Returns true on success. */
    fun storeVerifiedPasskey(
        userId: Uuid,
        verified: VerifiedRegistration,
    ): Boolean =
        runBlocking {
            passkeyRepository.storePasskey(
                userId = userId,
                credentialId = verified.credentialId,
                publicKey = verified.publicKey,
                signCount = verified.signCount,
                info = verified.passkey,
            )
        }

    /** Result of [verifyRegistrationOnly] — either verified data or a structured error. */
    sealed interface VerificationOutcome {
        data class Success(
            val data: VerifiedRegistration,
        ) : VerificationOutcome

        data class Failure(
            val error: String,
        ) : VerificationOutcome
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

    private fun verifyRegistrationSimplifiedOnly(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): VerificationOutcome {
        return try {
            val challengeData =
                validateChallenge(
                    challenge = challenge,
                    expectedType = "registration",
                    expectedUserId = userId,
                ) ?: return VerificationOutcome.Failure("Invalid challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val credentialId = normalizeCredentialId(registrationResponse.id) ?: registrationResponse.id
            VerificationOutcome.Success(
                VerifiedRegistration(
                    credentialId = credentialId,
                    publicKey = registrationResponse.response.attestationObject.toByteArray(),
                    signCount = 0L,
                    passkey =
                        PasskeyInfo(
                            id = Uuid.random(),
                            credentialId = credentialId,
                            nickname = relyingPartyName,
                            deviceType = "platform",
                            createdAt = Clock.System.now(),
                            lastUsedAt = null,
                            isActive = true,
                        ),
                ),
            )
        } catch (e: Exception) {
            VerificationOutcome.Failure("Registration verification failed: ${e.message}")
        }
    }

    private fun verifyRegistrationStrictOnly(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse,
    ): VerificationOutcome {
        return try {
            val challengeData =
                validateChallenge(
                    challenge = challenge,
                    expectedType = "registration",
                    expectedUserId = userId,
                ) ?: return VerificationOutcome.Failure("Invalid challenge")
            challenges[challenge] = challengeData.copy(isUsed = true)

            val challengeBytes =
                decodeBase64Url(challenge)
                    ?: return VerificationOutcome.Failure("Challenge is not valid base64url")
            val attestationObjectBytes =
                decodeBase64Url(registrationResponse.response.attestationObject)
                    ?: return VerificationOutcome.Failure("Attestation object is not valid base64url")
            val clientDataJsonBytes =
                decodeBase64Url(registrationResponse.response.clientDataJSON)
                    ?: return VerificationOutcome.Failure("Client data is not valid base64url")

            val serverProperty =
                ServerProperty
                    .builder()
                    .origin(Origin(origin))
                    .rpId(relyingPartyId)
                    .challenge(DefaultChallenge(challengeBytes))
                    .build()

            val registrationRequest = RegistrationRequest(attestationObjectBytes, clientDataJsonBytes)
            // null pubKeyCredParams keeps the previous "all COSE algorithms allowed" behaviour.
            val registrationParameters = RegistrationParameters(serverProperty, null, true, true)
            val registrationData = webAuthnManager.verify(registrationRequest, registrationParameters)
            val attestationObject =
                registrationData.attestationObject
                    ?: return VerificationOutcome.Failure("Attestation object is missing")
            val authenticatorData = attestationObject.authenticatorData

            val attestedCredentialData =
                authenticatorData.attestedCredentialData
                    ?: return VerificationOutcome.Failure("Attested credential data is missing")

            val canonicalCredentialId = encodeBase64Url(attestedCredentialData.credentialId)
            val normalizedCredentialId = normalizeCredentialId(registrationResponse.id)
            if (normalizedCredentialId != null && normalizedCredentialId != canonicalCredentialId) {
                return VerificationOutcome.Failure("Credential ID mismatch")
            }

            VerificationOutcome.Success(
                VerifiedRegistration(
                    credentialId = canonicalCredentialId,
                    publicKey = attestedCredentialDataConverter.convert(attestedCredentialData),
                    signCount = authenticatorData.signCount,
                    passkey =
                        PasskeyInfo(
                            id = Uuid.random(),
                            credentialId = canonicalCredentialId,
                            nickname = relyingPartyName,
                            deviceType = "platform",
                            createdAt = Clock.System.now(),
                            lastUsedAt = null,
                            isActive = true,
                        ),
                ),
            )
        } catch (e: DataConversionException) {
            VerificationOutcome.Failure("Registration data conversion failed")
        } catch (e: Exception) {
            VerificationOutcome.Failure("Registration verification failed: ${e.message}")
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
            val authenticator = credentialRecord(attestedCredentialData, storedData.signCount)

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
