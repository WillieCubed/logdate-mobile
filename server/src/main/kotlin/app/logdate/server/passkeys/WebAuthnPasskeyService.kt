package app.logdate.server.passkeys

import app.logdate.shared.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.security.SecureRandom
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Simplified WebAuthn passkey service for development and initial implementation.
 * 
 * This implementation provides basic passkey functionality with simplified verification
 * suitable for development environments. For production, this would be enhanced with
 * full WebAuthn4j cryptographic verification.
 */
@OptIn(ExperimentalUuidApi::class)
class WebAuthnPasskeyService(
    private val relyingPartyId: String = "logdate.app",
    private val relyingPartyName: String = "LogDate",
    private val origin: String = "https://app.logdate.com"
) {
    
    private val secureRandom = SecureRandom()
    
    // In-memory storage for demo purposes - in production this would be a database
    private val challenges = mutableMapOf<String, PasskeyChallenge>()
    private val passkeys = mutableMapOf<Uuid, MutableList<StoredPasskey>>()
    
    data class StoredPasskey(
        val credentialId: String,
        val publicKey: String,
        val signCount: Long,
        val info: PasskeyInfo
    )
    
    data class RegistrationResult(
        val success: Boolean,
        val credentialId: String? = null,
        val passkey: PasskeyInfo? = null,
        val error: String? = null
    )
    
    data class AuthenticationResult(
        val success: Boolean,
        val userId: Uuid? = null,
        val credentialId: String? = null,
        val error: String? = null
    )
    
    /**
     * Generate WebAuthn registration options for account creation.
     */
    fun generateRegistrationOptions(
        userId: Uuid,
        username: String,
        displayName: String,
        excludeCredentials: List<String> = emptyList()
    ): PasskeyRegistrationOptions {
        val challenge = generateChallenge()
        
        // Store challenge for later verification
        challenges[challenge] = PasskeyChallenge(
            challenge = challenge,
            userId = userId,
            type = "registration",
            expiresAt = (Clock.System.now().plus(kotlin.time.Duration.parse("PT5M"))).toString(),
            isUsed = false
        )
        
        return PasskeyRegistrationOptions(
            challenge = challenge,
            user = PasskeyUser(
                id = Base64.getEncoder().encodeToString(userId.toString().toByteArray()),
                name = username,
                displayName = displayName
            ),
            excludeCredentials = excludeCredentials,
            timeout = 300_000L
        )
    }
    
    /**
     * Generate WebAuthn authentication options for login.
     */
    fun generateAuthenticationOptions(
        userId: Uuid? = null,
        allowedCredentials: List<String> = emptyList()
    ): PasskeyAuthenticationOptions {
        val challenge = generateChallenge()
        
        // Store challenge for later verification
        challenges[challenge] = PasskeyChallenge(
            challenge = challenge,
            userId = userId ?: Uuid.random(),
            type = "authentication",
            expiresAt = (Clock.System.now().plus(kotlin.time.Duration.parse("PT5M"))).toString(),
            isUsed = false
        )
        
        val allowCredentials = when {
            allowedCredentials.isNotEmpty() -> allowedCredentials
            userId != null -> getUserCredentials(userId)
            else -> emptyList()
        }
        
        return PasskeyAuthenticationOptions(
            challenge = challenge,
            allowCredentials = allowCredentials,
            timeout = 300_000L
        )
    }
    
    /**
     * Verify a WebAuthn registration response.
     * Simplified implementation for development.
     */
    fun verifyRegistration(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse
    ): RegistrationResult {
        return try {
            // Validate challenge
            val challengeData = challenges[challenge]
                ?: return RegistrationResult(success = false, error = "Invalid challenge")
            
            if (challengeData.isUsed) {
                return RegistrationResult(success = false, error = "Challenge already used")
            }
            
            if (challengeData.userId != userId) {
                return RegistrationResult(success = false, error = "User ID mismatch")
            }
            
            // Mark challenge as used
            challenges[challenge] = challengeData.copy(isUsed = true)
            
            // Create passkey info
            val credentialId = registrationResponse.id
            val passkey = PasskeyInfo(
                id = Uuid.random(),
                credentialId = credentialId,
                nickname = "Passkey",
                deviceType = "platform",
                createdAt = Clock.System.now(),
                lastUsedAt = null,
                isActive = true
            )
            
            // Store the passkey
            val storedPasskey = StoredPasskey(
                credentialId = credentialId,
                publicKey = registrationResponse.response.attestationObject, // Simplified
                signCount = 0,
                info = passkey
            )
            
            passkeys.computeIfAbsent(userId) { mutableListOf() }.add(storedPasskey)
            
            RegistrationResult(success = true, credentialId = credentialId, passkey = passkey)
            
        } catch (e: Exception) {
            RegistrationResult(success = false, error = "Registration verification failed: ${e.message}")
        }
    }
    
    /**
     * Verify a WebAuthn authentication response.
     * Simplified implementation for development.
     */
    fun verifyAuthentication(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse
    ): AuthenticationResult {
        return try {
            // Validate challenge
            val challengeData = challenges[challenge]
                ?: return AuthenticationResult(success = false, error = "Invalid challenge")
            
            if (challengeData.isUsed) {
                return AuthenticationResult(success = false, error = "Challenge already used")
            }
            
            // Mark challenge as used
            challenges[challenge] = challengeData.copy(isUsed = true)
            
            // Find stored passkey by credential ID
            val (userId, storedPasskey) = findPasskeyByCredentialId(authenticationResponse.id)
                ?: return AuthenticationResult(success = false, error = "Credential not found")
            
            // Update last used time
            val updatedPasskey = storedPasskey.copy(
                info = storedPasskey.info.copy(lastUsedAt = Clock.System.now())
            )
            
            // Update stored passkey
            passkeys[userId]?.let { userPasskeys ->
                val index = userPasskeys.indexOfFirst { it.credentialId == authenticationResponse.id }
                if (index >= 0) {
                    userPasskeys[index] = updatedPasskey
                }
            }
            
            AuthenticationResult(success = true, userId = userId, credentialId = authenticationResponse.id)
            
        } catch (e: Exception) {
            AuthenticationResult(success = false, error = "Authentication verification failed: ${e.message}")
        }
    }
    
    /**
     * Get all passkeys for a user.
     */
    fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> {
        return passkeys[userId]?.map { it.info }?.filter { it.isActive } ?: emptyList()
    }
    
    /**
     * Delete a passkey for a user.
     */
    fun deletePasskey(credentialId: String, userId: Uuid): Boolean {
        val userPasskeys = passkeys[userId] ?: return false
        
        val index = userPasskeys.indexOfFirst { it.credentialId == credentialId }
        return if (index >= 0) {
            val passkey = userPasskeys[index]
            userPasskeys[index] = passkey.copy(info = passkey.info.copy(isActive = false))
            true
        } else {
            false
        }
    }
    
    /**
     * Get credential IDs for a user.
     */
    fun getUserCredentials(userId: Uuid): List<String> {
        return passkeys[userId]?.filter { it.info.isActive }?.map { it.credentialId } ?: emptyList()
    }
    
    private fun generateChallenge(): String {
        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
    }
    
    private fun findPasskeyByCredentialId(credentialId: String): Pair<Uuid, StoredPasskey>? {
        for ((userId, userPasskeys) in passkeys) {
            for (passkey in userPasskeys) {
                if (passkey.credentialId == credentialId && passkey.info.isActive) {
                    return userId to passkey
                }
            }
        }
        return null
    }
}