# LogDate User Identity System

This document outlines the design and implementation of the LogDate user identity system, which provides a consistent user identification mechanism across devices and account states.

## Core Principles

1. **Universal Identity**: Every LogDate installation generates a unique user ID during initial onboarding, regardless of cloud account status.
2. **Identity Persistence**: This ID persists throughout the application lifecycle and serves as the user's primary identifier.
3. **Cloud Integration**: When a user creates a LogDate Cloud account, their local ID is associated with their cloud account.
4. **Data Ownership**: The user ID acts as a signature for all user content, enabling ownership verification and forming the foundation for end-to-end encryption.
5. **Local-First**: Content is always accessible on the device regardless of cloud status.

## Technical Implementation

### User ID Generation

1. **Format**: User IDs are generated as UUIDs (v4) to ensure uniqueness
2. **Timing**: ID generation occurs during the onboarding process completion
3. **Persistence**: The ID is immediately stored in secure storage after generation

### Storage Security

1. **Platform-Specific Secure Storage**:
   - Android: Android Keystore System with encryption
   - iOS: iOS Keychain Services with appropriate protection classes
   - Desktop: Platform-specific encrypted storage (e.g., macOS Keychain, Windows Credential Manager)

2. **Additional Security Layers**:
   - Encryption with a device-specific key before storage
   - Access restricted to authenticated application sessions
   - No exposure through logs, analytics, or debugging information

### Multi-Device Handling

1. **Primary ID Concept**:
   - The first device's ID becomes the primary user ID when creating a cloud account
   - This primary ID is stored in the cloud account record
   - When signing into an existing account on a new device, the cloud-stored primary ID is retrieved

2. **Device ID Mapping**:
   - Each device maintains a mapping between its local ID and the primary user ID
   - This mapping is used for content reconciliation during sync operations

3. **ID Migration Process**:
   - When a user signs into an existing cloud account on a device with local content
   - The device begins using the cloud account's primary ID for all new content immediately
   - A background migration process updates existing content to use the primary ID
   - All content remains accessible during migration

### Recovery Mechanisms

1. **Cloud-Based Recovery**:
   - For cloud users, the primary ID is retrievable through authentication
   - After account authentication, the primary ID is restored to the device

2. **Manual Recovery Options**:
   - Secure export of identity information in an encrypted file
   - QR code generation for direct device-to-device transfer
   - Optional recovery phrase (mnemonic) for manual recovery

3. **Identity Verification**:
   - Challenge-response mechanism to verify identity ownership
   - Prevents unauthorized access to user identity during recovery

## Content Chain of Custody

A critical aspect of the identity system is maintaining provable ownership of content through a chain of custody mechanism.

### How Chain of Custody Works

1. **Creation Signing**:
   - When content is created, it receives a unique content ID (UUID)
   - The content is signed with the creator's private key
   - The signature covers both content and metadata (timestamp, author ID, content ID)
   - This signature is stored with the content

2. **Edit History**:
   - Each edit creates a new signed record in the edit history
   - The original creator signature remains part of the content history
   - Each editor signs their modifications
   - The full history creates an immutable audit trail

3. **Verification**:
   - Anyone with access to the content and public keys can verify:
     - Who created the original content
     - Who made each subsequent edit
     - That the content hasn't been tampered with

4. **Implementation Considerations**:
   - Storage overhead for signatures and history
   - Performance impact of cryptographic operations
   - Key management requirements

## ID Migration Process

When a user signs into a cloud account on a device with existing local content, a migration process occurs to update local content with the cloud account's primary user ID.

### Migration Design

1. **Immediate Changes**:
   - The app immediately starts using the cloud primary ID for all new content
   - All local content remains accessible via content-specific IDs
   - UI shows both migrated and unmigrated content

2. **Background Migration Worker**:
   - A background worker process handles ID migration
   - The process is resumable across app restarts
   - Migration occurs in batches to minimize resource impact
   - Progress is tracked and can be reported to the user

3. **Migration State Management**:
   - Migration state is persistently stored
   - Includes tracking of:
     - Old and new user IDs
     - Migration progress (items processed/total)
     - Last processed item for resumption
     - Overall migration status

4. **Migration Resilience**:
   - Handles app updates during migration
   - Recovers from interruptions
   - Implements exponential backoff for retries
   - Logs specific failures for troubleshooting

### Migration Code Example

```kotlin
class UserIdMigrationManager(
    private val database: AppDatabase,
    private val preferences: DataStore<Preferences>
) {
    // Migration state keys
    private val MIGRATION_IN_PROGRESS = booleanPreferencesKey("migration_in_progress")
    private val MIGRATION_OLD_ID = stringPreferencesKey("migration_old_id")
    private val MIGRATION_NEW_ID = stringPreferencesKey("migration_new_id")
    private val MIGRATION_LAST_PROCESSED_ID = stringPreferencesKey("migration_last_processed_id")
    private val MIGRATION_ITEMS_PROCESSED = intPreferencesKey("migration_items_processed")
    private val MIGRATION_TOTAL_ITEMS = intPreferencesKey("migration_total_items")

    suspend fun startMigration(oldUserId: String, newUserId: String) {
        // Count total items to migrate
        val totalItems = database.countAllContentByUserId(oldUserId)
        
        // Store migration state
        preferences.edit { prefs ->
            prefs[MIGRATION_IN_PROGRESS] = true
            prefs[MIGRATION_OLD_ID] = oldUserId
            prefs[MIGRATION_NEW_ID] = newUserId
            prefs[MIGRATION_LAST_PROCESSED_ID] = ""
            prefs[MIGRATION_ITEMS_PROCESSED] = 0
            prefs[MIGRATION_TOTAL_ITEMS] = totalItems
        }
        
        // Queue the migration work
        queueMigrationWork()
    }
    
    suspend fun resumeMigrationIfNeeded() {
        // Check if migration was in progress
        val migrationInProgress = preferences.data.first()[MIGRATION_IN_PROGRESS] ?: false
        
        if (migrationInProgress) {
            // Resume migration
            queueMigrationWork()
        }
    }
    
    private fun queueMigrationWork() {
        // Create work request with backoff strategy
        val workRequest = OneTimeWorkRequestBuilder<UserIdMigrationWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
            
        // Enqueue unique work to prevent duplicates
        WorkManager.getInstance()
            .enqueueUniqueWork("user_id_migration", ExistingWorkPolicy.REPLACE, workRequest)
    }
    
    suspend fun updateMigrationProgress(lastProcessedId: String, itemsProcessed: Int) {
        preferences.edit { prefs ->
            prefs[MIGRATION_LAST_PROCESSED_ID] = lastProcessedId
            prefs[MIGRATION_ITEMS_PROCESSED] = itemsProcessed
        }
    }
    
    suspend fun completeMigration() {
        preferences.edit { prefs ->
            prefs[MIGRATION_IN_PROGRESS] = false
        }
    }
    
    suspend fun getMigrationProgress(): Flow<MigrationProgress> {
        return preferences.data.map { prefs ->
            MigrationProgress(
                inProgress = prefs[MIGRATION_IN_PROGRESS] ?: false,
                itemsProcessed = prefs[MIGRATION_ITEMS_PROCESSED] ?: 0,
                totalItems = prefs[MIGRATION_TOTAL_ITEMS] ?: 0
            )
        }
    }
}

// Worker implementation
class UserIdMigrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var migrationManager: UserIdMigrationManager
    
    @Inject
    lateinit var database: AppDatabase

    override suspend fun doWork(): Result {
        // Get migration state
        val prefs = migrationManager.getMigrationState().first()
        
        if (!prefs.inProgress) {
            return Result.success()
        }
        
        val oldUserId = prefs.oldId
        val newUserId = prefs.newId
        val lastProcessedId = prefs.lastProcessedId
        val itemsProcessed = prefs.itemsProcessed
        
        try {
            // Get batch of items to migrate
            val itemBatch = database.getContentBatchForMigration(
                userId = oldUserId,
                afterContentId = lastProcessedId,
                limit = BATCH_SIZE
            )
            
            if (itemBatch.isEmpty()) {
                // Migration complete
                migrationManager.completeMigration()
                return Result.success()
            }
            
            // Process batch
            database.transaction {
                itemBatch.forEach { content ->
                    database.updateContentUserId(content.id, newUserId)
                }
            }
            
            // Update progress
            val lastId = itemBatch.last().id
            val newProcessedCount = itemsProcessed + itemBatch.size
            migrationManager.updateMigrationProgress(lastId, newProcessedCount)
            
            // Check if more work remains
            return if (newProcessedCount < prefs.totalItems) {
                Result.retry()
            } else {
                migrationManager.completeMigration()
                Result.success()
            }
            
        } catch (e: Exception) {
            Napier.e("Migration failed", e)
            return Result.retry()
        }
    }
    
    companion object {
        private const val BATCH_SIZE = 50
    }
}
```

### User Interface Considerations

1. **Migration Status Indicator**:
   - Optional notification showing migration progress
   - Settings screen with migration status and progress
   - Subtle "Local Only" indicator for unmigrated content

2. **Content Display During Migration**:
   - All content remains visible and accessible
   - Queries include both old and new user IDs until migration completes
   - Content-specific IDs used for UI state management

3. **User Communication**:
   - One-time notice explaining the background process
   - Clear language about data availability
   - No technical details unless requested

## Integration with End-to-End Encryption

The user ID serves as the foundation for the end-to-end encryption system:

1. **Key Derivation**: 
   - Cryptographic keys are derived from the user ID combined with device-specific information
   - Master key = f(user_id, salt)
   - Content key = f(master_key, content_uuid)

2. **Content Signing**: 
   - User content is signed using keys derived from the user ID
   - Each piece of content maintains its chain of custody

3. **Ownership Verification**: 
   - Content ownership can be verified by validating signatures against the user's public keys
   - Verification works without central server (decentralized)

4. **Key Rotation**: 
   - Supports secure key rotation while maintaining the same user identity
   - Previous keys remain available for verifying historical content

## Content Verification in a Decentralized Context

The system supports verification of content authorship without requiring a central authority:

1. **Web of Trust Model**:
   - Users directly exchange and verify each other's public keys
   - Keys can be verified in-person via QR codes or key fingerprints
   - Once verified, keys are signed by each user, creating a decentralized trust network

2. **Direct Key Exchange**:
   - When users first connect, they exchange public keys directly
   - These keys are stored locally on each device
   - Future content verification uses these locally stored keys

3. **Key Continuity**:
   - After initial verification, the app tracks key history
   - Alerts appear if a contact's key suddenly changes
   - This "trust on first use" approach provides ongoing verification

4. **Content Self-verification**:
   - Each piece of content contains the author's public key
   - The signature can be verified directly against this included key
   - Key fingerprints visible to users allow manual verification

## User Experience

### Onboarding Flow

1. User completes initial app setup
2. System generates a unique user ID silently in the background
3. ID is stored securely on the device
4. User continues using the app with this identity

### Cloud Account Creation

1. User chooses to create a LogDate Cloud account
2. Their existing local ID is registered as their primary ID in the cloud
3. A passkey is generated for future authentication
4. The association between passkey and user ID is stored in the cloud

### New Device Setup

1. User installs LogDate on a new device
2. System generates a temporary local ID
3. User authenticates with their cloud account
4. The temporary ID is replaced with their primary ID from the cloud
5. Any content created with the temporary ID is migrated to the primary ID

### Multi-Device Scenario

1. User has LogDate on phone (UserID-A) and computer (UserID-B)
2. Creates content separately on both devices
3. Signs into LogDate Cloud on phone first
   - UserID-A becomes primary ID in cloud
4. Later signs into same account on computer
   - Computer retrieves UserID-A from cloud
   - Begins using UserID-A for all new content
   - Background process migrates existing UserID-B content to UserID-A
   - All content remains accessible during migration

### Data Export

1. All exports include the user's primary ID in the metadata
2. This ensures all exported content maintains its association with the user
3. Upon import, the system can verify content ownership

## Implementation Considerations

### Data Migration

For existing installations without a user ID:
1. Generate a new ID on first launch after update
2. Associate all existing content with this new ID
3. If the user has a cloud account, synchronize this ID with the cloud

### Edge Cases

1. **Account Deletion**: 
   - If a user deletes their cloud account, the primary ID remains on their devices
   - New cloud account creation will use this existing ID as the new primary ID

2. **Multiple Accounts**:
   - If support for multiple accounts is added in the future, each account will maintain a separate primary ID
   - The system will track which content belongs to which account ID

3. **Content Sharing**:
   - When content is shared between users, the ownership ID is preserved
   - Recipients can verify the source of shared content via the originator's ID

4. **Migration Interruptions**:
   - Migration process is resumable across app restarts
   - Progress tracking ensures no content is missed
   - Resilient to crashes and updates

5. **Offline Usage**:
   - System functions fully offline using local ID
   - Synchronizes IDs when connectivity is restored

## Security Considerations

1. **ID Confidentiality**: 
   - While not a secret, the user ID should not be unnecessarily exposed
   - Avoid including it in logs, analytics, or debug information

2. **Tampering Prevention**:
   - Implement integrity checks to detect unauthorized modifications to the ID
   - Use cryptographic techniques to verify ID authenticity

3. **Brute Force Protection**:
   - Implement rate limiting for ID verification attempts
   - Use secure comparison methods to prevent timing attacks

4. **Trust Assumptions**:
   - The system assumes trust of devices where the user has authenticated
   - This is a reasonable assumption but worth noting in security documentation
   - Additional protections like biometric verification can enhance this trust model

## API Design

### Core Interfaces

```kotlin
interface UserIdentityManager {
    /**
     * Returns the current user's primary ID
     * Generates a new ID if one doesn't exist
     */
    suspend fun getUserId(): String
    
    /**
     * Updates the local ID with the primary ID from the cloud
     * Called during cloud account sign-in
     */
    suspend fun syncCloudIdentity(cloudId: String)
    
    /**
     * Begins migration from old ID to new ID
     * Returns a flow of migration progress
     */
    suspend fun startIdMigration(oldId: String, newId: String): Flow<MigrationProgress>
    
    /**
     * Exports the user identity information for backup
     * Returns an encrypted representation of the identity data
     */
    suspend fun exportIdentity(): ByteArray
    
    /**
     * Imports user identity from backup
     * Verifies ownership before replacing current identity
     */
    suspend fun importIdentity(identityData: ByteArray): Result<Boolean>
}
```

### Storage Interface

```kotlin
interface SecureIdentityStorage {
    /**
     * Stores the user ID securely
     */
    suspend fun storeUserId(userId: String)
    
    /**
     * Retrieves the stored user ID
     * Returns null if no ID exists
     */
    suspend fun retrieveUserId(): String?
    
    /**
     * Clears the stored user ID
     * Used during complete account reset
     */
    suspend fun clearUserId()
}
```

### Content Chain of Custody

```kotlin
interface ContentSigningService {
    /**
     * Signs content with the user's private key
     * Returns a signature that can verify content authenticity
     */
    suspend fun signContent(content: ByteArray): ByteArray
    
    /**
     * Verifies content signature against a public key
     * Returns true if the signature is valid
     */
    suspend fun verifySignature(
        content: ByteArray, 
        signature: ByteArray, 
        publicKey: ByteArray
    ): Boolean
    
    /**
     * Creates a signed edit record for content
     * Links to previous version in edit history
     */
    suspend fun createSignedEdit(
        contentId: String,
        previousVersion: Int,
        newVersion: Int,
        changes: String
    ): SignedEdit
}
```

## Implementation Roadmap

1. **Phase 1: Basic Identity System**
   - Implement user ID generation during onboarding
   - Create secure storage mechanisms for all platforms
   - Add ID inclusion in data exports

2. **Phase 2: Cloud Integration**
   - Implement cloud account association with user ID
   - Develop ID migration process
   - Create user ID synchronization mechanisms

3. **Phase 3: Content Ownership**
   - Implement content signing infrastructure
   - Add chain of custody for content edits
   - Develop ownership verification processes

4. **Phase 4: Recovery Mechanisms**
   - Implement identity export/import functionality
   - Add QR code and recovery phrase options
   - Develop user interfaces for identity management

5. **Phase 5: E2E Encryption Foundation**
   - Implement key derivation from user ID
   - Develop secure key storage and management
   - Create framework for encrypted content sharing

## Conclusion

The LogDate User Identity System provides a robust foundation for content ownership, verification, and security while maintaining the local-first principles of the application. By generating unique user IDs during onboarding and carefully managing their migration when cloud accounts are created, we ensure a consistent user experience while enabling powerful features like content verification and end-to-end encryption.

The system is designed to be resilient, with careful consideration of edge cases, migration challenges, and security implications. By documenting these considerations and providing a clear implementation roadmap, we establish a path toward a secure, user-friendly identity system that enhances the core functionality of LogDate while preparing for future security features.