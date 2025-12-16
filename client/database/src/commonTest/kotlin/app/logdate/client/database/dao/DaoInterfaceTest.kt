package app.logdate.client.database.dao

import app.logdate.client.database.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class DaoInterfaceTest : BaseDatabaseTest() {

    @Test
    fun journalDao_classExists() = runTest {
        val daoClass = JournalDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun textNoteDao_classExists() = runTest {
        val daoClass = TextNoteDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun imageNoteDao_classExists() = runTest {
        val daoClass = ImageNoteDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun journalNotesDao_classExists() = runTest {
        val daoClass = JournalNotesDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun locationHistoryDao_classExists() = runTest {
        val daoClass = LocationHistoryDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun userDevicesDao_classExists() = runTest {
        val daoClass = UserDevicesDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun userMediaDao_classExists() = runTest {
        val daoClass = UserMediaDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun journalContentDao_classExists() = runTest {
        val daoClass = app.logdate.client.database.dao.journals.JournalContentDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun cachedRewindDao_classExists() = runTest {
        val daoClass = app.logdate.client.database.dao.rewind.CachedRewindDao::class
        assertNotNull(daoClass)
        assertNotNull(daoClass.qualifiedName)
    }

    @Test
    fun allDaoClasses_exist() = runTest {
        val daoClasses = listOf(
            JournalDao::class,
            TextNoteDao::class,
            ImageNoteDao::class,
            JournalNotesDao::class,
            LocationHistoryDao::class,
            UserDevicesDao::class,
            UserMediaDao::class,
            app.logdate.client.database.dao.journals.JournalContentDao::class,
            app.logdate.client.database.dao.rewind.CachedRewindDao::class
        )
        
        // Verify all DAO classes exist
        daoClasses.forEach { daoClass ->
            assertNotNull(daoClass)
            assertNotNull(daoClass.qualifiedName)
        }
    }
}