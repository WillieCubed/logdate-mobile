package app.logdate.client.domain.account.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for the UserIdentity domain model.
 */
class UserIdentityTest {
    
    @Test
    fun `UserIdentity creation with default values`() {
        // Arrange
        val userId = Uuid.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        
        // Act
        val userIdentity = UserIdentity(userId = userId)
        
        // Assert
        assertEquals(userId, userIdentity.userId)
        assertFalse(userIdentity.isCloudLinked)
        assertNull(userIdentity.cloudAccountId)
    }
    
    @Test
    fun `UserIdentity creation with cloud account linked`() {
        // Arrange
        val userId = Uuid.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        val cloudAccountId = "acc_12345"
        
        // Act
        val userIdentity = UserIdentity(
            userId = userId,
            isCloudLinked = true,
            cloudAccountId = cloudAccountId
        )
        
        // Assert
        assertEquals(userId, userIdentity.userId)
        assertTrue(userIdentity.isCloudLinked)
        assertEquals(cloudAccountId, userIdentity.cloudAccountId)
    }
    
    @Test
    fun `IdentityMigrationState creation with all values`() {
        // Arrange
        val oldUserId = Uuid.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        val newUserId = Uuid.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8")
        val itemsProcessed = 50
        val totalItems = 100
        val lastProcessedId = "last_processed_id"
        
        // Act
        val migrationState = IdentityMigrationState(
            inProgress = true,
            oldUserId = oldUserId,
            newUserId = newUserId,
            itemsProcessed = itemsProcessed,
            totalItems = totalItems,
            lastProcessedId = lastProcessedId
        )
        
        // Assert
        assertTrue(migrationState.inProgress)
        assertEquals(oldUserId, migrationState.oldUserId)
        assertEquals(newUserId, migrationState.newUserId)
        assertEquals(itemsProcessed, migrationState.itemsProcessed)
        assertEquals(totalItems, migrationState.totalItems)
        assertEquals(lastProcessedId, migrationState.lastProcessedId)
    }
    
    @Test
    fun `IdentityMigrationState creation with null values`() {
        // Arrange
        val itemsProcessed = 0
        val totalItems = 0
        
        // Act
        val migrationState = IdentityMigrationState(
            inProgress = false,
            oldUserId = null,
            newUserId = null,
            itemsProcessed = itemsProcessed,
            totalItems = totalItems,
            lastProcessedId = null
        )
        
        // Assert
        assertFalse(migrationState.inProgress)
        assertNull(migrationState.oldUserId)
        assertNull(migrationState.newUserId)
        assertEquals(itemsProcessed, migrationState.itemsProcessed)
        assertEquals(totalItems, migrationState.totalItems)
        assertNull(migrationState.lastProcessedId)
    }
    
    @Test
    fun `MigrationProgress calculates percentComplete correctly`() {
        // Arrange
        val itemsProcessed = 25
        val totalItems = 100
        
        // Act
        val migrationProgress = MigrationProgress(
            inProgress = true,
            itemsProcessed = itemsProcessed,
            totalItems = totalItems
        )
        
        // Assert
        assertTrue(migrationProgress.inProgress)
        assertEquals(itemsProcessed, migrationProgress.itemsProcessed)
        assertEquals(totalItems, migrationProgress.totalItems)
        assertEquals(0.25f, migrationProgress.percentComplete)
    }
    
    @Test
    fun `MigrationProgress handles zero total items`() {
        // Arrange
        val itemsProcessed = 0
        val totalItems = 0
        
        // Act
        val migrationProgress = MigrationProgress(
            inProgress = false,
            itemsProcessed = itemsProcessed,
            totalItems = totalItems
        )
        
        // Assert
        assertFalse(migrationProgress.inProgress)
        assertEquals(itemsProcessed, migrationProgress.itemsProcessed)
        assertEquals(totalItems, migrationProgress.totalItems)
        assertEquals(0f, migrationProgress.percentComplete)
    }
}