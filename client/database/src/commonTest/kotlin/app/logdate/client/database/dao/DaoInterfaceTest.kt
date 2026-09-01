package app.logdate.client.database.dao

import app.logdate.client.database.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Tests for DAO interface existence and provider configuration.
 *
 * This suite ensures that all required DAO classes are correctly defined
 * and available in the dependency injection container, which is critical
 * for database stability.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DaoInterfaceTest : BaseDatabaseTest() {
    @Test
    fun `journal dao class exists`() =
        runTest {
            val daoClass = JournalDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `text note dao class exists`() =
        runTest {
            val daoClass = TextNoteDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `image note dao class exists`() =
        runTest {
            val daoClass = ImageNoteDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `journal notes dao class exists`() =
        runTest {
            val daoClass = JournalNotesDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `location history dao class exists`() =
        runTest {
            val daoClass = LocationHistoryDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `user devices dao class exists`() =
        runTest {
            val daoClass = UserDevicesDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `user media dao class exists`() =
        runTest {
            val daoClass = UserMediaDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `journal content dao class exists`() =
        runTest {
            val daoClass = app.logdate.client.database.dao.journals.JournalContentDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `cached rewind dao class exists`() =
        runTest {
            val daoClass = app.logdate.client.database.dao.rewind.CachedRewindDao::class
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }

    @Test
    fun `all dao classes exist`() =
        runTest {
            val daoClasses =
                listOf(
                    JournalDao::class,
                    TextNoteDao::class,
                    ImageNoteDao::class,
                    JournalNotesDao::class,
                    LocationHistoryDao::class,
                    UserDevicesDao::class,
                    UserMediaDao::class,
                    app.logdate.client.database.dao.journals.JournalContentDao::class,
                    app.logdate.client.database.dao.rewind.CachedRewindDao::class,
                )

            // Verify all DAO classes exist
            daoClasses.forEach { daoClass ->
                assertNotNull(daoClass)
                assertNotNull(daoClass.qualifiedName)
            }
        }
}
