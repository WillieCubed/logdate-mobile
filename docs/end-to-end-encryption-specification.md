# LogDate End-to-End Encryption Specification

## Table of Contents

1. [Overview](#overview)
2. [Security Model](#security-model)
3. [AI-Native Encryption Challenges](#ai-native-encryption-challenges)
4. [Cross-Platform Architecture](#cross-platform-architecture)
5. [Implementation Strategy](#implementation-strategy)
6. [Privacy Contexts](#privacy-contexts)
7. [Key Management](#key-management)
8. [Data Flow Patterns](#data-flow-patterns)
9. [AI Processing Framework](#ai-processing-framework)
10. [Security Guarantees](#security-guarantees)
11. [Migration Strategy](#migration-strategy)
12. [Compliance Considerations](#compliance-considerations)

---

## Overview

LogDate implements a **zero-knowledge, client-side encryption architecture** that protects user data
while enabling AI-powered features through selective, user-controlled decryption. This specification
addresses the unique challenges of building an AI-native application with end-to-end encryption
across multiple platforms.

### Core Principles

- **Zero-Knowledge**: Server never has access to plaintext user data
- **User Control**: Granular permissions for AI processing
- **Selective Decryption**: Decrypt only what's needed, when needed
- **Cross-Platform Security**: Consistent security model across all platforms
- **Performance-First**: Minimal impact on user experience

---

## Security Model

### Threat Model

**Protected Against:**

- Server-side data breaches
- Network interception attacks
- Unauthorized access to local storage
- Malicious cloud AI services
- Cross-device data exposure

**Trust Assumptions:**

- User devices are secure during active use
- Platform cryptographic implementations are secure
- User authentication mechanisms (passkeys/biometrics) are trusted
- Users will responsibly manage AI processing permissions

### Security Boundaries

```
┌──────────────────────────────────────────────────────────────┐
│                    User's Trusted Environment                │
│  ┌───────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Device 1    │  │    Device 2     │  │    Device N     │ │
│  │ (Decrypted)   │  │  (Decrypted)    │  │  (Decrypted)    │ │
│  └───────────────┘  └─────────────────┘  └─────────────────┘ │
└──────────────────────────────────────────────────────────────┘
                                │
                     ┌──────────▼──────────┐
                     │   Encryption Layer  │
                     └──────────┬──────────┘
                                │
┌──────────────────────────────────────────────────────────────┐
│                    Untrusted Environment                     │
│  ┌───────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ LogDate Cloud │  │   AI Services   │  │  Local Storage  │ │
│  │  (Encrypted)  │  │  (Controlled)   │  │  (Encrypted)    │ │
│  └───────────────┘  └─────────────────┘  └─────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

---

## AI-Native Encryption Challenges

### The AI Processing Dilemma

Traditional E2EE conflicts with AI processing requirements:

- **AI needs plaintext** to analyze content
- **Encryption hides content** from analysis
- **Cloud AI services** require data transmission
- **Local AI models** have limited capabilities

### LogDate's Solution: Contextual Decryption Framework

```kotlin
// Selective decryption based on user preferences and content sensitivity
sealed class DecryptionContext {
    object FullAccess : DecryptionContext()
    object AIProcessing : DecryptionContext()
    object SyncOnly : DecryptionContext()
    object LocalOnly : DecryptionContext()
}

interface AIProcessingPolicy {
    suspend fun canProcessWithAI(
        content: EncryptedContent,
        aiService: AIService,
        context: DecryptionContext
    ): AIPermission
}
```

### AI Processing Modes

#### 1. **Privacy-First Mode**

- Local AI processing only
- No cloud AI services
- Limited AI capabilities
- Maximum privacy

#### 2. **Selective Sharing Mode**

- User chooses which entries can use cloud AI
- Content anonymization before cloud processing
- Granular permissions per AI feature
- Balanced privacy/functionality

#### 3. **Enhanced Features Mode**

- Full AI processing capabilities
- Opt-in cloud AI services
- Rich AI-powered insights
- User-controlled data sharing

---

## Cross-Platform Architecture

### Platform Abstraction Strategy

LogDate uses **expect/actual declarations** to provide consistent encryption APIs across platforms
while leveraging platform-specific security features.

### Core Abstractions

#### 1. **Cryptographic Provider Interface**

```kotlin
// commonMain
expect class PlatformCryptoProvider : CryptoProvider {
    suspend fun generateSymmetricKey(keySize: Int): ByteArray
    suspend fun encryptAES(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    suspend fun decryptAES(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    suspend fun generateSecureRandom(size: Int): ByteArray
    suspend fun computeHMAC(data: ByteArray, key: ByteArray): ByteArray
}
```

#### 2. **Secure Key Storage Interface**

```kotlin
// commonMain
expect class PlatformKeyManager : KeyManager {
    suspend fun storeKey(keyId: String, key: ByteArray, requireAuth: Boolean): Boolean
    suspend fun retrieveKey(keyId: String): ByteArray?
    suspend fun deleteKey(keyId: String): Boolean
    suspend fun keyExists(keyId: String): Boolean
    suspend fun generateMasterKey(): ByteArray
}
```

#### 3. **Biometric Authentication Interface**

```kotlin
// commonMain
expect class PlatformBiometricManager : BiometricManager {
    suspend fun isAvailable(): Boolean
    suspend fun authenticate(reason: String): AuthResult
    suspend fun createBiometricKey(keyId: String): Boolean
    suspend fun authenticateWithKey(keyId: String): AuthResult
}
```

### Platform-Specific Implementations

#### Android Implementation

```kotlin
// androidMain
actual class PlatformKeyManager : KeyManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")

    actual suspend fun storeKey(keyId: String, key: ByteArray, requireAuth: Boolean): Boolean {
        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyId,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            if (requireAuth) {
                setUserAuthenticationRequired(true)
                setUserAuthenticationValidityDurationSeconds(300)
                setInvalidatedByBiometricEnrollment(true)
            }
        }.build()

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(keyGenSpec)
        keyGenerator.generateKey()

        return true
    }

    actual suspend fun generateMasterKey(): ByteArray {
        // Use Android Keystore hardware-backed key generation
        return generateHardwareBackedKey()
    }
}

actual class PlatformCryptoProvider : CryptoProvider {
    actual suspend fun encryptAES(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(data)
    }
}
```

#### iOS Implementation

```kotlin
// iosMain
actual class PlatformKeyManager : KeyManager {
    actual suspend fun storeKey(keyId: String, key: ByteArray, requireAuth: Boolean): Boolean {
        val query = CFMutableDictionary.create()
        query[kSecClass] = kSecClassGenericPassword
        query[kSecAttrAccount] = keyId
        query[kSecValueData] = key.toNSData()
        query[kSecAttrAccessible] = if (requireAuth) {
            kSecAttrAccessibleBiometryAny
        } else {
            kSecAttrAccessibleAfterFirstUnlock
        }

        val status = SecItemAdd(query, null)
        return status == errSecSuccess
    }

    actual suspend fun generateMasterKey(): ByteArray {
        // Use iOS Secure Enclave for key generation
        return generateSecureEnclaveKey()
    }
}

actual class PlatformCryptoProvider : CryptoProvider {
    actual suspend fun encryptAES(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // Use iOS CommonCrypto for AES encryption
        return performAESEncryption(data, key, iv)
    }
}
```

#### Desktop (JVM) Implementation

```kotlin
// jvmMain
actual class PlatformKeyManager : KeyManager {
    private val credentialManager = when {
        Platform.isWindows() -> WindowsCredentialManager()
        Platform.isMacOS() -> KeychainManager()
        Platform.isLinux() -> LibSecretManager()
        else -> throw UnsupportedOperationException("Platform not supported")
    }

    actual suspend fun storeKey(keyId: String, key: ByteArray, requireAuth: Boolean): Boolean {
        return credentialManager.store(keyId, key, requireAuth)
    }

    actual suspend fun generateMasterKey(): ByteArray {
        // Use platform-specific secure key generation
        return credentialManager.generateSecureKey()
    }
}
```

### Platform Security Features Matrix

| Platform | Key Storage             | Biometric Auth   | Hardware Security |
|----------|-------------------------|------------------|-------------------|
| Android  | Keystore                | Fingerprint/Face | TEE/StrongBox     |
| iOS      | Keychain                | TouchID/FaceID   | Secure Enclave    |
| macOS    | Keychain                | TouchID          | Secure Enclave    |
| Windows  | Credential Manager      | Windows Hello    | TPM               |
| Linux    | libsecret/gnome-keyring | -                | TPM (optional)    |

---

## Implementation Strategy

### Module Structure

```
client/encryption/
├── src/
│   ├── commonMain/kotlin/app/logdate/client/encryption/
│   │   ├── EncryptionManager.kt
│   │   ├── KeyManager.kt
│   │   ├── CryptoProvider.kt
│   │   ├── AIDecryptionPolicy.kt
│   │   ├── ContentClassifier.kt
│   │   └── di/EncryptionModule.kt
│   ├── androidMain/kotlin/.../
│   │   ├── AndroidKeyManager.kt
│   │   ├── AndroidCryptoProvider.kt
│   │   └── AndroidBiometricManager.kt
│   ├── iosMain/kotlin/.../
│   │   ├── IosKeyManager.kt
│   │   ├── IosCryptoProvider.kt
│   │   └── IosBiometricManager.kt
│   ├── jvmMain/kotlin/.../
│   │   ├── DesktopKeyManager.kt
│   │   ├── DesktopCryptoProvider.kt
│   │   └── DesktopAuthManager.kt
│   └── commonTest/kotlin/.../
│       └── EncryptionTests.kt
```

### Core Encryption Manager

```kotlin
class EncryptionManager(
    private val keyManager: KeyManager,
    private val cryptoProvider: CryptoProvider,
    private val contentClassifier: ContentClassifier,
    private val aiPolicy: AIDecryptionPolicy
) {
    suspend fun encrypt(content: String, context: EncryptionContext): EncryptedContent {
        val contentKey = generateContentKey()
        val encryptedData = cryptoProvider.encryptAES(
            data = content.toByteArray(),
            key = contentKey,
            iv = cryptoProvider.generateSecureRandom(12)
        )

        val encryptedKey = encryptContentKey(contentKey)

        return EncryptedContent(
            data = encryptedData,
            keyId = contentKey.id,
            encryptedKey = encryptedKey,
            metadata = EncryptionMetadata(
                algorithm = "AES-256-GCM",
                keyDerivation = "PBKDF2",
                contentType = context.contentType,
                sensitivityLevel = contentClassifier.classify(content)
            )
        )
    }

    suspend fun decryptForAI(
        encryptedContent: EncryptedContent,
        aiService: AIService,
        purpose: AIProcessingPurpose
    ): DecryptionResult {
        val permission = aiPolicy.checkPermission(encryptedContent, aiService, purpose)

        return when (permission) {
            is AIPermission.Allowed -> {
                val plaintext = decrypt(encryptedContent)
                val sanitized = sanitizeForAI(plaintext, permission.sanitizationLevel)
                DecryptionResult.Success(sanitized)
            }
            is AIPermission.LocalOnly -> {
                val plaintext = decrypt(encryptedContent)
                DecryptionResult.LocalProcessingOnly(plaintext)
            }
            is AIPermission.Denied -> {
                DecryptionResult.PermissionDenied(permission.reason)
            }
        }
    }
}
```

---

## Privacy Contexts

### Content Sensitivity Classification

```kotlin
enum class ContentSensitivity {
    PUBLIC,      // Non-sensitive content (weather, general observations)
    PERSONAL,    // Personal but non-sensitive (daily activities)
    PRIVATE,     // Private information (relationships, work)
    SENSITIVE,   // Highly sensitive (health, financial, intimate)
    RESTRICTED   // User-marked as never for AI processing
}

class ContentClassifier {
    suspend fun classify(content: String): ContentSensitivity {
        // Use local ML models to classify content sensitivity
        // Consider: PII detection, emotional content, medical terms, etc.
        return classifyWithLocalModel(content)
    }
}
```

### AI Processing Permissions

```kotlin
data class AIProcessingPreferences(
    val allowCloudAI: Boolean = false,
    val allowedServices: Set<AIService> = emptySet(),
    val maxSensitivityLevel: ContentSensitivity = ContentSensitivity.PERSONAL,
    val requireReconfirmation: Boolean = true,
    val anonymizePersonalInfo: Boolean = true,
    val localProcessingOnly: Set<AIFeature> = setOf(
        AIFeature.PEOPLE_EXTRACTION,
        AIFeature.BASIC_SUMMARIZATION
    )
)

sealed class AIPermission {
    data class Allowed(val sanitizationLevel: SanitizationLevel) : AIPermission()
    data class LocalOnly(val reason: String) : AIPermission()
    data class Denied(val reason: String) : AIPermission()
}
```

---

## Key Management

### Key Hierarchy

```
Master Key (Derived from Passkey/Biometric)
└── Account Key (Per-user, rotatable)
    ├── Device Key (Per-device, for sync)
    ├── Journal Key (Per-journal)
    │   └── Entry Keys (Per-entry, temporal)
    └── Feature Keys (Per-AI-feature, revocable)
```

### Key Derivation

```kotlin
class KeyDerivationService(
    private val cryptoProvider: CryptoProvider
) {
    suspend fun deriveMasterKey(
        userCredential: UserCredential,
        deviceSalt: ByteArray
    ): MasterKey {
        val keyMaterial = when (userCredential) {
            is PasskeyCredential -> userCredential.publicKey
            is BiometricCredential -> userCredential.biometricTemplate
        }

        return cryptoProvider.deriveKey(
            keyMaterial = keyMaterial,
            salt = deviceSalt,
            iterations = 100_000,
            keyLength = 32
        )
    }

    suspend fun deriveContentKey(
        accountKey: AccountKey,
        contentId: String,
        timestamp: Instant
    ): ContentKey {
        val contextInfo = buildString {
            append("logdate.content.")
            append(contentId)
            append(".")
            append(timestamp.epochSeconds)
        }

        return cryptoProvider.hkdfExpand(
            prk = accountKey.bytes,
            info = contextInfo.toByteArray(),
            length = 32
        )
    }
}
```

### Key Rotation Strategy

```kotlin
class KeyRotationManager(
    private val keyManager: KeyManager,
    private val encryptionManager: EncryptionManager
) {
    suspend fun rotateAccountKey(trigger: RotationTrigger) {
        val newAccountKey = keyManager.generateAccountKey()
        val oldAccountKey = keyManager.getCurrentAccountKey()

        // Re-encrypt all content with new key
        val migrationPlan = createMigrationPlan(oldAccountKey, newAccountKey)
        executeMigration(migrationPlan)

        // Update key references
        keyManager.setCurrentAccountKey(newAccountKey)
        keyManager.archiveKey(oldAccountKey)
    }
}

enum class RotationTrigger {
    SCHEDULED,          // Regular rotation (e.g., yearly)
    SECURITY_INCIDENT,  // Security breach or compromise
    DEVICE_CHANGE,      // New device added/removed
    USER_REQUEST       // Manual rotation
}
```

---

## Data Flow Patterns

### Encryption-Aware Repository Pattern

```kotlin
class EncryptedJournalRepository(
    private val encryptionManager: EncryptionManager,
    private val localDao: JournalDao,
    private val remoteSource: RemoteJournalDataSource
) : JournalRepository {

    override suspend fun create(entry: JournalEntry): Result<Uuid> {
        return try {
            // Encrypt before local storage
            val encrypted = encryptionManager.encrypt(
                content = entry.content,
                context = EncryptionContext.LocalStorage
            )

            val entity = entry.toEncryptedEntity(encrypted)
            val id = localDao.insert(entity)

            // Sync encrypted data to cloud
            remoteSource.sync(encrypted)

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getForAIProcessing(
        entryId: Uuid,
        aiFeature: AIFeature
    ): Result<String> {
        val encryptedEntity = localDao.getById(entryId)
        val encryptedContent = encryptedEntity.toEncryptedContent()

        return when (val result = encryptionManager.decryptForAI(
            encryptedContent = encryptedContent,
            aiService = aiFeature.requiredService,
            purpose = aiFeature.processingPurpose
        )) {
            is DecryptionResult.Success -> Result.success(result.content)
            is DecryptionResult.LocalProcessingOnly -> Result.success(result.content)
            is DecryptionResult.PermissionDenied -> Result.failure(
                AIPermissionException(result.reason)
            )
        }
    }
}
```

### AI Processing Pipeline

```kotlin
class AIProcessingPipeline(
    private val encryptionManager: EncryptionManager,
    private val contentFilter: ContentFilter,
    private val aiClients: Map<AIService, AIClient>
) {
    suspend fun processEntries(
        entries: List<EncryptedJournalEntry>,
        feature: AIFeature
    ): AIProcessingResult {
        val processingPlan = createProcessingPlan(entries, feature)

        return when (processingPlan.strategy) {
            ProcessingStrategy.LOCAL_ONLY -> processLocally(processingPlan)
            ProcessingStrategy.CLOUD_WITH_SANITIZATION -> processWithCloud(processingPlan)
            ProcessingStrategy.MIXED -> processMixed(processingPlan)
            ProcessingStrategy.DENIED -> AIProcessingResult.PermissionDenied
        }
    }

    private suspend fun processWithCloud(plan: ProcessingPlan): AIProcessingResult {
        val decryptedContent = plan.entries.mapNotNull { entry ->
            when (val result = encryptionManager.decryptForAI(
                encryptedContent = entry.content,
                aiService = plan.aiService,
                purpose = plan.purpose
            )) {
                is DecryptionResult.Success -> result.content
                else -> null
            }
        }

        val sanitizedContent = contentFilter.sanitize(
            content = decryptedContent,
            level = plan.sanitizationLevel
        )

        val aiResult = aiClients[plan.aiService]?.process(
            content = sanitizedContent,
            feature = plan.feature
        )

        return AIProcessingResult.Success(aiResult)
    }
}
```

---

## AI Processing Framework

### Content Sanitization

```kotlin
enum class SanitizationLevel {
    NONE,        // No sanitization (local processing only)
    MINIMAL,     // Remove obvious PII (emails, phone numbers)
    MODERATE,    // Replace names with placeholders
    AGGRESSIVE,  // Remove all potential identifiers
    ANONYMIZED   // Statistical/semantic content only
}

class ContentSanitizer {
    suspend fun sanitize(content: String, level: SanitizationLevel): String {
        return when (level) {
            SanitizationLevel.NONE -> content
            SanitizationLevel.MINIMAL -> removePII(content)
            SanitizationLevel.MODERATE -> replaceNames(content)
            SanitizationLevel.AGGRESSIVE -> removeIdentifiers(content)
            SanitizationLevel.ANONYMIZED -> createSemanticSummary(content)
        }
    }

    private suspend fun replaceNames(content: String): String {
        // Use local NLP models to identify and replace names
        val names = extractNames(content)
        var sanitized = content

        names.forEachIndexed { index, name ->
            sanitized = sanitized.replace(name, "Person ${index + 1}")
        }

        return sanitized
    }
}
```

### AI Service Abstraction

```kotlin
interface AIService {
    val name: String
    val trustLevel: TrustLevel
    val dataRetentionPolicy: DataRetentionPolicy
    val supportedFeatures: Set<AIFeature>
}

sealed class AIFeature(
    val requiredService: AIService,
    val processingPurpose: AIProcessingPurpose,
    val minimumSanitization: SanitizationLevel
) {
    object EntrySummarization : AIFeature(
        requiredService = OpenAIService,
        processingPurpose = AIProcessingPurpose.SUMMARIZATION,
        minimumSanitization = SanitizationLevel.MODERATE
    )

    object PeopleExtraction : AIFeature(
        requiredService = LocalNLPService,
        processingPurpose = AIProcessingPurpose.ENTITY_EXTRACTION,
        minimumSanitization = SanitizationLevel.NONE
    )

    object RewindGeneration : AIFeature(
        requiredService = OpenAIService,
        processingPurpose = AIProcessingPurpose.CREATIVE_WRITING,
        minimumSanitization = SanitizationLevel.AGGRESSIVE
    )
}
```

### Local AI Processing

```kotlin
class LocalAIProcessor(
    private val localModels: Map<AIFeature, LocalModel>
) {
    suspend fun processLocally(
        content: String,
        feature: AIFeature
    ): LocalProcessingResult {
        val model = localModels[feature]
            ?: return LocalProcessingResult.ModelNotAvailable

        return try {
            val result = model.process(content)
            LocalProcessingResult.Success(result)
        } catch (e: Exception) {
            LocalProcessingResult.ProcessingError(e)
        }
    }
}

interface LocalModel {
    suspend fun process(content: String): String
    val capabilities: Set<AICapability>
    val memoryRequirement: Long
}
```

---

## Security Guarantees

### Cryptographic Guarantees

1. **Confidentiality**: AES-256-GCM encryption with unique keys per content item
2. **Integrity**: HMAC-SHA256 for data integrity verification
3. **Authenticity**: Digital signatures for key authenticity
4. **Forward Secrecy**: Key rotation ensures past data remains secure
5. **Perfect Forward Secrecy**: Compromise of current keys doesn't affect past data

### Implementation Guarantees

1. **Zero-Knowledge**: Server cannot access plaintext data
2. **Selective Decryption**: Users control what gets decrypted for AI processing
3. **Audit Trail**: All AI processing operations are logged locally
4. **Revocable Permissions**: AI access can be revoked at any time
5. **Local Fallback**: Core functionality works without cloud AI services

### Platform Security Guarantees

| Platform | Key Protection         | Memory Protection      | Anti-Debug          |
|----------|------------------------|------------------------|---------------------|
| Android  | Hardware TEE/StrongBox | Memory tagging         | Root detection      |
| iOS      | Secure Enclave         | Pointer authentication | Jailbreak detection |
| macOS    | Secure Enclave         | SIP protection         | -                   |
| Windows  | TPM/Credential Guard   | CET/CFG                | -                   |
| Linux    | TPM (optional)         | ASLR/Stack canaries    | -                   |

---

## Migration Strategy

### Phase 1: Foundation (Months 1-2)

- Implement core encryption interfaces
- Create platform-specific key managers
- Add basic encryption to database layer
- Implement user consent UI

### Phase 2: AI Integration (Months 3-4)

- Build AI processing pipeline with encryption
- Implement content sanitization
- Add local AI processing capabilities
- Create granular permission controls

### Phase 3: Advanced Features (Months 5-6)

- Implement key rotation
- Add cross-device synchronization
- Build audit and compliance features
- Performance optimization

### Phase 4: Migration & Rollout (Months 7-8)

- Migrate existing user data
- Gradual feature rollout
- Security auditing and testing
- User education and documentation

### Data Migration Strategy

```kotlin
class EncryptionMigrationManager(
    private val encryptionManager: EncryptionManager,
    private val database: LogDateDatabase
) {
    suspend fun migrateUserData(userId: String): MigrationResult {
        val migrationPlan = createMigrationPlan(userId)

        return database.withTransaction {
            try {
                // Migrate in batches to avoid memory issues
                migrationPlan.batches.forEach { batch ->
                    migrateBatch(batch)
                }

                // Update migration status
                updateMigrationStatus(userId, MigrationStatus.COMPLETED)
                MigrationResult.Success
            } catch (e: Exception) {
                // Rollback on failure
                rollbackMigration(userId)
                MigrationResult.Failure(e)
            }
        }
    }

    private suspend fun migrateBatch(batch: MigrationBatch) {
        batch.entries.forEach { entry ->
            val encrypted = encryptionManager.encrypt(
                content = entry.content,
                context = EncryptionContext.Migration
            )

            database.journalDao.updateWithEncryption(
                entryId = entry.id,
                encryptedContent = encrypted
            )
        }
    }
}
```

---

## Compliance Considerations

### Privacy Regulations

**GDPR Compliance:**

- Right to erasure: Secure key deletion ensures data cannot be decrypted
- Data portability: Export functionality with user-controlled decryption
- Purpose limitation: Granular AI processing controls
- Consent management: Explicit consent for AI processing

**CCPA Compliance:**

- Do not sell: No sharing of plaintext data with third parties
- Data deletion: Cryptographic deletion through key destruction
- Access rights: Users can access their decrypted data

### Security Standards

**SOC 2 Type II:**

- Security controls for key management
- Audit logging for all encryption operations
- Access controls and authentication
- Monitoring and incident response

**ISO 27001:**

- Information security management system
- Risk assessment and treatment
- Security awareness and training
- Continuous improvement

### Implementation Checklist

- [ ] Cryptographic key management procedures
- [ ] Data classification and handling procedures
- [ ] Incident response plan for security breaches
- [ ] Regular security audits and penetration testing
- [ ] Employee security training programs
- [ ] Vendor security assessments for AI services
- [ ] Data retention and deletion policies
- [ ] Cross-border data transfer assessments

---

## Conclusion

This end-to-end encryption specification provides a comprehensive framework for protecting user data
in LogDate while enabling AI-powered features. The architecture balances security, privacy, and
functionality through:

1. **Strong cryptographic foundations** with platform-specific security features
2. **User-controlled AI processing** with granular permissions
3. **Cross-platform consistency** through well-defined abstractions
4. **Compliance-ready design** meeting major privacy regulations
5. **Practical migration strategy** for existing users

The implementation prioritizes user privacy while maintaining the AI-native capabilities that make
LogDate valuable, creating a new standard for privacy-preserving AI applications.

## References

- [NIST Cryptographic Standards](https://csrc.nist.gov/publications/fips)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Platform Security Guides](https://developer.android.com/training/articles/keystore)
- [AI Ethics Guidelines](https://ai.gov/ai-ethics-guidelines/)