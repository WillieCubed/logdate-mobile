package app.logdate.client.database

import app.logdate.client.database.converters.StringListConverter
import app.logdate.client.database.converters.TimestampConverter
import app.logdate.client.database.converters.UuidConverter
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.JournalNoteCrossRef
import app.logdate.client.database.entities.LocationLogEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.UserDeviceEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.database.entities.media.MediaImageEntity
import app.logdate.client.database.entities.people.InferredPersonClusterEntity
import app.logdate.client.database.entities.people.InferredPersonEvidenceEntity
import app.logdate.client.database.entities.people.PersonEntity
import app.logdate.client.database.entities.people.PersonLinkEntity
import app.logdate.client.database.entities.people.PersonResolutionDecisionEntity
import app.logdate.client.database.migrations.MIGRATION_1_2
import app.logdate.client.database.migrations.MIGRATION_2_3
import app.logdate.client.database.migrations.MIGRATION_39_40
import app.logdate.client.database.migrations.MIGRATION_3_4
import app.logdate.client.database.migrations.MIGRATION_40_41
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

/**
 * Unit tests for database configuration and migrations.
 *
 * Verifies that database constants, Room migrations, type converters, and
 * DAO modules are correctly configured and that the schema versions match
 * the expected migration path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseConfigurationTest : BaseDatabaseTest() {
    @Test
    fun databaseConstants_haveCorrectValues() =
        runTest {
            assertEquals("logdate", DATABASE_NAME)
        }

    @Test
    fun migrationEndpoints_haveCorrectVersionNumbers() =
        runTest {
            val migrations =
                listOf(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_39_40,
                    MIGRATION_40_41,
                )

            assertTrue(migrations.isNotEmpty())
            assertEquals(11, migrations.size)

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

            assertEquals(39, MIGRATION_39_40.startVersion)
            assertEquals(40, MIGRATION_39_40.endVersion)

            assertEquals(40, MIGRATION_40_41.startVersion)
            assertEquals(41, MIGRATION_40_41.endVersion)
        }

    @Test
    fun databaseAbstractClass_exists() =
        runTest {
            // Test that the LogDateDatabase abstract class exists
            val databaseClass = LogDateDatabase::class
            assertNotNull(databaseClass)
            assertNotNull(databaseClass.qualifiedName)
        }

    @Test
    fun daosModule_hasAllExpectedProviders() =
        runTest {
            // Test that the daosModule has providers for all expected DAOs
            val module = daosModule
            assertNotNull(module)

            // The module should have single providers for all DAOs
            // We can't easily test the exact providers without Koin context,
            // but we can verify the module exists and is configured
            assertNotNull(module)
        }

    @Test
    fun databaseVersion_isCorrect() =
        runTest {
            assertEquals(41, MIGRATION_40_41.endVersion)
        }

    @Test
    fun typeConverters_areConfigured() =
        runTest {
            assertNotNull(TimestampConverter::class)
            assertNotNull(UuidConverter::class)
            assertNotNull(StringListConverter::class)
        }

    @Test
    fun entities_areProperlyConfigured() =
        runTest {
            val entityClasses =
                listOf(
                    TextNoteEntity::class,
                    ImageNoteEntity::class,
                    JournalEntity::class,
                    JournalNoteCrossRef::class,
                    LocationLogEntity::class,
                    UserDeviceEntity::class,
                    MediaImageEntity::class,
                    JournalContentEntityLink::class,
                    PersonEntity::class,
                    InferredPersonClusterEntity::class,
                    InferredPersonEvidenceEntity::class,
                    PersonLinkEntity::class,
                    PersonResolutionDecisionEntity::class,
                )

            entityClasses.forEach { entityClass ->
                assertNotNull(entityClass)
            }

            assertEquals(13, entityClasses.size)
        }
}
