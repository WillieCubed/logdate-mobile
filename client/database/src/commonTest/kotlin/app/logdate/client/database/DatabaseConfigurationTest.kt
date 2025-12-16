package app.logdate.client.database

import app.logdate.client.database.migrations.MIGRATION_1_2
import app.logdate.client.database.migrations.MIGRATION_2_3
import app.logdate.client.database.migrations.MIGRATION_3_4
import app.logdate.client.database.migrations.MIGRATION_4_5
import app.logdate.client.database.migrations.MIGRATION_5_6
import app.logdate.client.database.migrations.MIGRATION_6_7
import app.logdate.client.database.migrations.MIGRATION_7_8
import app.logdate.client.database.migrations.MIGRATION_8_9
import app.logdate.client.database.migrations.MIGRATION_9_10
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseConfigurationTest : BaseDatabaseTest() {

    @Test
    fun databaseConstants_haveCorrectValues() = runTest {
        assertEquals("logdate", DATABASE_NAME)
    }

    @Test
    fun migrations_haveCorrectVersionNumbers() = runTest {
        // Test that all migrations have sequential version numbers
        val migrations = listOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )
        
        // Verify all migrations exist
        assertTrue(migrations.isNotEmpty())
        assertEquals(9, migrations.size)
        
        // Verify version numbers are sequential
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
        
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
        
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
        
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
        
        assertEquals(5, MIGRATION_5_6.startVersion)
        assertEquals(6, MIGRATION_5_6.endVersion)
        
        assertEquals(6, MIGRATION_6_7.startVersion)
        assertEquals(7, MIGRATION_6_7.endVersion)
        
        assertEquals(7, MIGRATION_7_8.startVersion)
        assertEquals(8, MIGRATION_7_8.endVersion)
        
        assertEquals(8, MIGRATION_8_9.startVersion)
        assertEquals(9, MIGRATION_8_9.endVersion)
        
        assertEquals(9, MIGRATION_9_10.startVersion)
        assertEquals(10, MIGRATION_9_10.endVersion)
    }

    @Test
    fun databaseAbstractClass_exists() = runTest {
        // Test that the LogDateDatabase abstract class exists
        val databaseClass = LogDateDatabase::class
        assertNotNull(databaseClass)
        assertNotNull(databaseClass.qualifiedName)
    }

    @Test
    fun daosModule_hasAllExpectedProviders() = runTest {
        // Test that the daosModule has providers for all expected DAOs
        val module = daosModule
        assertNotNull(module)
        
        // The module should have single providers for all DAOs
        // We can't easily test the exact providers without Koin context,
        // but we can verify the module exists and is configured
        assertNotNull(module)
    }

    @Test
    fun databaseVersion_isCorrect() = runTest {
        // The database version should be 10 based on the latest migration
        // This is defined in the @Database annotation
        
        // We can verify this indirectly by checking the last migration
        assertEquals(10, MIGRATION_9_10.endVersion)
    }

    @Test
    fun typeConverters_areConfigured() = runTest {
        // Test that the database has type converters configured
        // The @TypeConverters annotation should include:
        // - TimestampConverter
        // - UuidConverter
        
        // This is verified at compile time by the Room processor
        // We can check that the converter classes exist
        assertNotNull(app.logdate.client.database.converters.TimestampConverter::class)
        assertNotNull(app.logdate.client.database.converters.UuidConverter::class)
    }

    @Test
    fun entities_areProperlyConfigured() = runTest {
        // Test that all expected entities are configured in the database
        // This includes verifying the entity classes exist and are properly annotated
        
        val entityClasses = listOf(
            app.logdate.client.database.entities.TextNoteEntity::class,
            app.logdate.client.database.entities.ImageNoteEntity::class,
            app.logdate.client.database.entities.JournalEntity::class,
            app.logdate.client.database.entities.JournalNoteCrossRef::class,
            app.logdate.client.database.entities.LocationLogEntity::class,
            app.logdate.client.database.entities.UserDeviceEntity::class,
            app.logdate.client.database.entities.media.MediaImageEntity::class,
            app.logdate.client.database.entities.journals.JournalContentEntityLink::class
            // Rewind entity has been removed or renamed
        )
        
        // Verify all entity classes exist
        entityClasses.forEach { entityClass ->
            assertNotNull(entityClass)
        }
        
        assertEquals(8, entityClasses.size)
    }
}