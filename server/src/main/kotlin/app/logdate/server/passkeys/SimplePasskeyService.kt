package app.logdate.server.passkeys

import app.logdate.shared.model.*
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Simplified passkey service for development and testing.
 * 
 * This implementation provides basic passkey functionality without complex WebAuthn verification.
 * Suitable for development environments where full cryptographic validation is not required.
 */
@OptIn(ExperimentalUuidApi::class)
class SimplePasskeyService(
    private val relyingPartyId: String = "logdate.app",
    private val relyingPartyName: String = "LogDate",
    private val origin: String = "https://app.logdate.com"
) {
    
    // In-memory storage for demo purposes
    private val challenges = mutableMapOf<String, PasskeyChallenge>()
    private val passkeys = mutableMapOf<String, PasskeyInfo>()
    
    fun generateRegistrationOptions(userId: Uuid, username: String, displayName: String): PasskeyRegistrationOptions {
        val challenge = generateChallenge()
        
        // Store challenge for verification
        challenges[challenge] = PasskeyChallenge(
            challenge = challenge,
            userId = userId,
            type = "registration",
            expiresAt = Clock.System.now().plus(kotlin.time.Duration.parse("PT5M")).toString()
        )
        
        return PasskeyRegistrationOptions(
            challenge = challenge,
            user = PasskeyUser(
                id = userId.toString(),
                name = username,
                displayName = displayName
            ),
            excludeCredentials = getExistingCredentialsForUser(userId),
            timeout = 300_000L
        )
    }
    
    fun generateAuthenticationOptions(
        userId: Uuid? = null,
        allowedCredentials: List<String> = emptyList()
    ): PasskeyAuthenticationOptions {
        val challenge = generateChallenge()
        
        // Store challenge for verification
        challenges[challenge] = PasskeyChallenge(
            challenge = challenge,
            userId = userId ?: Uuid.random(),
            type = "authentication",
            expiresAt = Clock.System.now().plus(kotlin.time.Duration.parse("PT5M")).toString()
        )
        
        val allowCredentials = if (allowedCredentials.isNotEmpty()) {
            allowedCredentials
        } else if (userId != null) {
            getExistingCredentialsForUser(userId)
        } else {
            emptyList()
        }
        
        return PasskeyAuthenticationOptions(
            challenge = challenge,
            allowCredentials = allowCredentials,
            timeout = 300_000L
        )
    }
    
    data class VerificationResult(
        val success: Boolean,
        val credentialId: String? = null,
        val userId: Uuid? = null,
        val error: String? = null
    )
    
    fun verifyRegistration(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse
    ): VerificationResult {
        return try {
            // Validate challenge
            val challengeData = challenges[challenge]
                ?: return VerificationResult(success = false, error = "Invalid challenge")
            
            if (challengeData.isUsed) {
                return VerificationResult(success = false, error = "Challenge already used")
            }
            
            if (challengeData.userId != userId) {
                return VerificationResult(success = false, error = "User ID mismatch")
            }
            
            // Mark challenge as used
            challenges[challenge] = challengeData.copy(isUsed = true)
            
            // Create stored passkey
            val passkey = PasskeyInfo(
                id = Uuid.random(),
                credentialId = registrationResponse.id,
                nickname = "Generated Passkey",
                deviceType = "platform",
                createdAt = Clock.System.now(),
                lastUsedAt = null,
                isActive = true
            )
            
            // Store the passkey
            passkeys[registrationResponse.id] = passkey
            
            VerificationResult(success = true, credentialId = registrationResponse.id)
        } catch (e: Exception) {
            VerificationResult(success = false, error = e.message)
        }
    }
    
    fun verifyAuthentication(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse
    ): VerificationResult {
        return try {
            // Validate challenge
            val challengeData = challenges[challenge]
                ?: return VerificationResult(success = false, error = "Invalid challenge")
            
            if (challengeData.isUsed) {
                return VerificationResult(success = false, error = "Challenge already used")
            }
            
            // Get stored passkey
            val passkey = passkeys[authenticationResponse.id]
                ?: return VerificationResult(success = false, error = "Credential not found")
            
            // Mark challenge as used
            challenges[challenge] = challengeData.copy(isUsed = true)
            
            // Update last used time
            val updatedPasskey = passkey.copy(lastUsedAt = Clock.System.now())
            passkeys[authenticationResponse.id] = updatedPasskey
            
            VerificationResult(success = true, userId = challengeData.userId)
        } catch (e: Exception) {
            VerificationResult(success = false, error = e.message)
        }
    }
    
    fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> {
        // In a real implementation, this would query by userId
        // For now, return all active passkeys
        return passkeys.values.filter { it.isActive }
    }
    
    fun deletePasskey(credentialId: String, userId: Uuid): Boolean {
        val passkey = passkeys[credentialId]
        return if (passkey != null) {
            passkeys[credentialId] = passkey.copy(isActive = false)
            true
        } else {
            false
        }
    }
    
    fun getUserCredentials(userId: Uuid): List<String> {
        // In a real implementation, this would query by userId
        return passkeys.values
            .filter { it.isActive }
            .map { it.credentialId }
    }
    
    fun generateChallenge(): String {
        val challengeBytes = ByteArray(32)
        Random.nextBytes(challengeBytes)
        return challengeBytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun getExistingCredentialsForUser(userId: Uuid): List<String> {
        // In a real implementation, this would query by userId
        return passkeys.values
            .filter { it.isActive }
            .map { it.credentialId }
    }
}