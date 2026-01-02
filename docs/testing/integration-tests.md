# Integration Testing Guide

Integration tests verify interactions between components and with real data sources like databases and APIs. They validate that components work together correctly.

## Scope

Integration tests cover:
- Database operations (Room DAOs)
- Repository implementations with real database
- Network API interactions
- Data synchronization workflows
- Cross-layer interactions
- Data consistency between local and remote

## Database Testing

### Room Database Testing

Test Room DAOs with real database instances:

```kotlin
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class JournalDaoTest {

    @get:Rule
    val database = RoomTestDatabase.create()

    private val journalDao by lazy { database.journalDao() }

    @Test
    fun whenInsertingJournal_thenCanRetrieve() {
        // Arrange
        val journal = JournalEntity(
            id = "test-id",
            name = "Test Journal",
            createdAt = Instant.now()
        )

        // Act
        journalDao.insert(journal)
        val retrieved = journalDao.getJournalById("test-id")

        // Assert
        assertEquals(journal, retrieved)
    }

    @Test
    fun whenDeletingJournal_thenNoLongerExists() {
        // Arrange
        val journal = JournalEntity(id = "test-id", name = "Test", createdAt = Instant.now())
        journalDao.insert(journal)

        // Act
        journalDao.delete(journal.id)
        val result = journalDao.getJournalById("test-id")

        // Assert
        assertNull(result)
    }

    @Test
    fun whenUpdateJournal_thenChangesArePersisted() {
        // Arrange
        val journal = JournalEntity(id = "1", name = "Original", createdAt = Instant.now())
        journalDao.insert(journal)

        // Act
        val updated = journal.copy(name = "Updated")
        journalDao.update(updated)
        val retrieved = journalDao.getJournalById("1")

        // Assert
        assertEquals("Updated", retrieved?.name)
    }

    @Test
    fun whenQueryingJournals_thenReturnsInCorrectOrder() {
        // Arrange
        repeat(3) { i ->
            journalDao.insert(
                JournalEntity(
                    id = "id-$i",
                    name = "Journal $i",
                    createdAt = Instant.now().plusSeconds(i.toLong())
                )
            )
        }

        // Act
        val journals = journalDao.getAllJournals()

        // Assert
        assertEquals(3, journals.size)
        assertEquals("Journal 0", journals[0].name)
    }
}
```

### Test Database Setup

```kotlin
// src/androidTest/kotlin/RoomTestDatabase.kt
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RoomTestDatabase : TestRule {
    private lateinit var database: LogDateDatabase

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                database = Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    LogDateDatabase::class.java
                ).allowMainThreadQueries().build()

                try {
                    base.evaluate()
                } finally {
                    database.close()
                }
            }
        }
    }

    fun getDatabase(): LogDateDatabase = database

    companion object {
        fun create() = RoomTestDatabase()
    }
}
```

## API Testing

### Mock Server Testing

Use MockWebServer for testing network interactions:

```kotlin
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class JournalApiTest {

    @get:Rule
    val mockWebServer = MockWebServer()

    private lateinit var apiClient: JournalApiClient

    @Before
    fun setup() {
        apiClient = JournalApiClient(
            httpClient = createTestHttpClient(),
            baseUrl = mockWebServer.url("/").toString()
        )
    }

    @Test
    fun whenFetchingJournals_thenParsesResponse() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "journals": [
                            {"id": "1", "name": "Journal 1"},
                            {"id": "2", "name": "Journal 2"}
                        ]
                    }
                """.trimIndent())
        )

        // Act
        val result = apiClient.getJournals()

        // Assert
        assertTrue(result.isSuccess)
        val journals = result.getOrNull()
        assertEquals(2, journals?.size)
        assertEquals("Journal 1", journals?.first()?.name)

        // Verify request
        val request: RecordedRequest = mockWebServer.takeRequest(timeout = 5, unit = TimeUnit.SECONDS)
        assertEquals("/api/journals", request.path)
    }

    @Test
    fun whenServerReturnsError_thenReturnsFailure() {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        // Act
        val result = apiClient.getJournals()

        // Assert
        assertTrue(result.isFailure)
    }
}
```

## Repository Integration Tests

Test repository with real database and mocked network:

```kotlin
class JournalRepositoryTest {

    @get:Rule
    val database = RoomTestDatabase.create()

    @get:Rule
    val mockWebServer = MockWebServer()

    private lateinit var repository: JournalRepository
    private val journalDao by lazy { database.getDatabase().journalDao() }
    private val apiClient by lazy { mockk<JournalApiClient>() }

    @Before
    fun setup() {
        repository = DefaultJournalRepository(
            journalDao = journalDao,
            apiClient = apiClient
        )
    }

    @Test
    fun whenCreatingJournal_thenSavesToDatabase() {
        // Arrange
        val journal = Journal(id = "1", name = "Test")

        // Act
        val result = repository.createJournal(journal)

        // Assert
        assertTrue(result.isSuccess)
        val saved = journalDao.getJournalById("1")
        assertEquals(journal.name, saved?.name)
    }

    @Test
    fun whenSyncingJournals_thenFetchesFromNetworkAndSavesToDatabase() {
        // Arrange
        val remoteJournals = listOf(
            Journal(id = "1", name = "Remote Journal")
        )
        coEvery { apiClient.getJournals() } returns Result.success(remoteJournals)

        // Act
        val result = repository.syncJournals()

        // Assert
        assertTrue(result.isSuccess)
        val saved = journalDao.getAllJournals()
        assertEquals(1, saved.size)
        assertEquals("Remote Journal", saved.first().name)
    }
}
```

## Sync Integration Tests

Test synchronization between local and remote data:

```kotlin
class SyncManagerTest {

    @get:Rule
    val database = RoomTestDatabase.create()

    private val noteDao by lazy { database.getDatabase().noteDao() }
    private val apiClient = mockk<NoteApiClient>()
    private val syncManager = SyncManager(noteDao, apiClient)

    @Test
    fun givenLocalChanges_whenSyncing_thenUploadsToServer() = runTest {
        // Arrange
        val localNote = Note(
            id = "1",
            content = "Local note",
            isModified = true
        )
        noteDao.insert(localNote)

        coEvery { apiClient.updateNote(any()) } returns Result.success(Unit)

        // Act
        syncManager.syncNotes()

        // Assert
        coVerify { apiClient.updateNote(match { it.content == "Local note" }) }
    }

    @Test
    fun givenRemoteChanges_whenSyncing_thenDownloadsAndSaves() = runTest {
        // Arrange
        val remoteNote = Note(id = "1", content = "Remote content")
        coEvery { apiClient.getNotes() } returns Result.success(listOf(remoteNote))

        // Act
        syncManager.syncNotes()

        // Assert
        val saved = noteDao.getNoteById("1")
        assertEquals("Remote content", saved?.content)
    }

    @Test
    fun givenConflict_whenSyncing_thenResolvesWithStrategy() = runTest {
        // Arrange
        val localNote = Note(id = "1", content = "Local", version = 1)
        val remoteNote = Note(id = "1", content = "Remote", version = 2)
        noteDao.insert(localNote)
        coEvery { apiClient.getNotes() } returns Result.success(listOf(remoteNote))

        // Act
        syncManager.syncNotes(conflictStrategy = ConflictStrategy.REMOTE_WINS)

        // Assert
        val result = noteDao.getNoteById("1")
        assertEquals("Remote", result?.content)
        assertEquals(2, result?.version)
    }
}
```

## State Flow Integration Tests

Test interactions between components using real Flows:

```kotlin
class TimelineRepositoryTest {

    @get:Rule
    val database = RoomTestDatabase.create()

    private val entryDao by lazy { database.getDatabase().entryDao() }
    private val repository = TimelineRepository(entryDao)

    @Test
    fun whenObservingEntries_thenEmitsUpdates() = runTest {
        // Arrange
        val entries = listOf(
            Entry(id = "1", timestamp = Instant.now()),
            Entry(id = "2", timestamp = Instant.now())
        )
        entries.forEach { entryDao.insert(it) }

        // Act
        val values = repository.observeEntries().take(2).toList()

        // Assert
        assertEquals(2, values.size)
        assertEquals(2, values.last().size)
    }

    @Test
    fun whenInsertingEntry_thenObserversNotified() = runTest {
        // Arrange
        val entry = Entry(id = "1", timestamp = Instant.now())
        val flow = repository.observeEntries()

        // Act - collect in background
        val values = mutableListOf<List<Entry>>()
        val job = launch {
            flow.collect { values.add(it) }
        }
        advanceUntilIdle()
        entryDao.insert(entry)
        advanceUntilIdle()

        // Assert
        assert(values.any { it.size == 1 })
        job.cancel()
    }
}
```

## Running Integration Tests

```bash
# Run all integration tests (including database tests)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest --tests "JournalDaoTest"

# Run specific module
./gradlew :client:database:connectedAndroidTest
```

## Best Practices

1. **Use In-Memory Databases**: Faster than actual database
   ```kotlin
   Room.inMemoryDatabaseBuilder(context, Database::class.java)
   ```

2. **Mock External Services**: Network, sensors, system
   ```kotlin
   private val apiClient = mockk<ApiClient>()
   ```

3. **Test Real Data Persistence**: Use actual database schema
   ```kotlin
   val saved = dao.insert(entity)
   val retrieved = dao.getById(entity.id)
   assertEquals(entity, retrieved)
   ```

4. **Verify Sync Correctness**: Test conflict resolution and ordering
   ```kotlin
   // Test both upload and download scenarios
   // Test conflict resolution strategies
   // Verify data consistency after sync
   ```

5. **Test Error Handling**: Network failures, database locks, etc.
   ```kotlin
   mockWebServer.enqueue(MockResponse().setResponseCode(500))
   val result = repository.fetch()
   assertTrue(result.isFailure)
   ```

6. **Clean Up Resources**: Close databases, servers, etc.
   ```kotlin
   @After
   fun teardown() {
       database.close()
   }
   ```

## Resources

- [Room Testing](https://developer.android.com/training/data-storage/room/testing-db)
- [MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver)
- [Mockk Documentation](https://mockk.io/)
- [JUnit Rules](https://junit.org/junit4/javadoc/latest/org/junit/Rule.html)
