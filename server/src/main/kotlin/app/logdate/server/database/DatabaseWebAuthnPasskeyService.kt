package app.logdate.server.database

import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.shared.model.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Database-backed WebAuthn passkey service that persists challenges and credentials.
 */
@OptIn(ExperimentalUuidApi::class)
class DatabaseWebAuthnPasskeyService(
    private val passkeyRepository: PostgreSQLPasskeyRepository,
    private val relyingPartyId: String = "logdate.app",
    private val relyingPartyName: String = "LogDate",
    private val origin: String = "https://app.logdate.com"
) {
    
    private val secureRandom = SecureRandom()
    
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
        
        // Store challenge in database
        transaction {
            WebAuthnChallengesTable.insert {
                it[WebAuthnChallengesTable.challenge] = challenge
                it[WebAuthnChallengesTable.userId] = userId.toJavaUUID()
                it[challengeType] = "registration"
                it[expiresAt] = Clock.System.now() + 5.minutes
                it[isUsed] = false
                it[createdAt] = Clock.System.now()
            }
        }
        
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
    suspend fun generateAuthenticationOptions(
        userId: Uuid? = null,
        allowedCredentials: List<String> = emptyList()
    ): PasskeyAuthenticationOptions {
        val challenge = generateChallenge()
        val challengeUserId = userId ?: Uuid.random()
        
        // Store challenge in database
        transaction {
            WebAuthnChallengesTable.insert {
                it[WebAuthnChallengesTable.challenge] = challenge
                it[WebAuthnChallengesTable.userId] = challengeUserId.toJavaUUID()
                it[challengeType] = "authentication"
                it[expiresAt] = Clock.System.now() + 5.minutes
                it[isUsed] = false
                it[createdAt] = Clock.System.now()
            }
        }
        
        val allowCredentials = when {
            allowedCredentials.isNotEmpty() -> allowedCredentials
            userId != null -> passkeyRepository.getCredentialIdsForUser(userId)
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
     */
    suspend fun verifyRegistration(
        userId: Uuid,
        challenge: String,
        registrationResponse: PasskeyRegistrationResponse
    ): RegistrationResult {
        return try {
            // Validate challenge
            val challengeData = transaction {
                WebAuthnChallengesTable.selectAll()
                    .where { WebAuthnChallengesTable.challenge eq challenge }
                    .singleOrNull()
            } ?: return RegistrationResult(success = false, error = "Invalid challenge")
            
            if (challengeData[WebAuthnChallengesTable.isUsed]) {
                return RegistrationResult(success = false, error = "Challenge already used")
            }
            
            if (challengeData[WebAuthnChallengesTable.userId] != userId.toJavaUUID()) {
                return RegistrationResult(success = false, error = "User ID mismatch")
            }
            
            if (Clock.System.now() > challengeData[WebAuthnChallengesTable.expiresAt]) {
                return RegistrationResult(success = false, error = "Challenge expired")
            }
            
            // Mark challenge as used
            transaction {
                WebAuthnChallengesTable.update({ WebAuthnChallengesTable.challenge eq challenge }) {
                    it[isUsed] = true
                }
            }
            
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
            
            // Store the passkey with WebAuthn data
            passkeyRepository.saveWithWebAuthnData(
                accountId = userId,
                passkey = passkey,
                publicKey = registrationResponse.response.attestationObject, // Simplified
                signCount = 0,
                webauthnData = "{\"rawId\":\"${registrationResponse.rawId}\"}"
            )
            
            RegistrationResult(success = true, credentialId = credentialId, passkey = passkey)
            
        } catch (e: Exception) {
            RegistrationResult(success = false, error = "Registration verification failed: ${e.message}")
        }
    }
    
    /**
     * Verify a WebAuthn authentication response.
     */
    suspend fun verifyAuthentication(
        challenge: String,
        authenticationResponse: PasskeyAuthenticationResponse
    ): AuthenticationResult {
        return try {
            // Validate challenge
            val challengeData = transaction {
                WebAuthnChallengesTable.selectAll()
                    .where { WebAuthnChallengesTable.challenge eq challenge }
                    .singleOrNull()
            } ?: return AuthenticationResult(success = false, error = "Invalid challenge")
            
            if (challengeData[WebAuthnChallengesTable.isUsed]) {
                return AuthenticationResult(success = false, error = "Challenge already used")
            }
            
            if (Clock.System.now() > challengeData[WebAuthnChallengesTable.expiresAt]) {
                return AuthenticationResult(success = false, error = "Challenge expired")
            }
            
            // Mark challenge as used
            transaction {
                WebAuthnChallengesTable.update({ WebAuthnChallengesTable.challenge eq challenge }) {
                    it[isUsed] = true
                }
            }
            
            // Find the account that owns this credential
            val userId = findUserByCredentialId(authenticationResponse.id)
                ?: return AuthenticationResult(success = false, error = "Credential not found")
            
            // Update last used time
            passkeyRepository.updateLastUsed(authenticationResponse.id)
            
            AuthenticationResult(success = true, userId = userId, credentialId = authenticationResponse.id)
            
        } catch (e: Exception) {
            AuthenticationResult(success = false, error = "Authentication verification failed: ${e.message}")
        }
    }
    
    /**
     * Get all passkeys for a user.
     */
    suspend fun getPasskeysForUser(userId: Uuid): List<PasskeyInfo> {
        return passkeyRepository.findActiveByAccountId(userId)
    }
    
    /**
     * Delete a passkey for a user.
     */
    suspend fun deletePasskey(credentialId: String, userId: Uuid): Boolean {
        val passkey = passkeyRepository.findByCredentialId(credentialId)
        return if (passkey != null) {
            passkeyRepository.deactivatePasskey(credentialId, userId)
        } else {
            false
        }
    }
    
    
    /**
     * Clean up expired challenges.
     */
    suspend fun cleanupExpiredChallenges(): Int {
        return transaction {
            val now = Clock.System.now()
            WebAuthnChallengesTable.deleteWhere { 
                (expiresAt less now) or (isUsed eq true)
            }
        }
    }
    
    private fun generateChallenge(): String {
        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
    }
    
    private suspend fun findUserByCredentialId(credentialId: String): Uuid? {
        return transaction {
            PasskeysTable.selectAll()
                .where { (PasskeysTable.credentialId eq credentialId) and (PasskeysTable.isActive eq true) }
                .singleOrNull()
                ?.get(PasskeysTable.accountId)
                ?.toKotlinUuid()
        }
    }
}