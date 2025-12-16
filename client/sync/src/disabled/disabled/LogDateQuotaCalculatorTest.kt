package app.logdate.client.sync.quota

import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.database.entities.VoiceNoteEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LogDateQuotaCalculatorTest {

    private val mockTextNoteDao = MockTextNoteDao()
    private val mockImageNoteDao = MockImageNoteDao()
    private val mockVideoNoteDao = MockVideoNoteDao()
    private val mockVoiceNoteDao = MockAudioNoteDao()
    private val mockJournalDao = MockJournalDao()

    private val calculator = LogDateQuotaCalculator(
        textNoteDao = mockTextNoteDao,
        imageNoteDao = mockImageNoteDao,
        videoNoteDao = mockVideoNoteDao,
        voiceNoteDao = mockVoiceNoteDao,
        journalDao = mockJournalDao
    )

    @Test
    fun `calculateTotalUsage returns correct quota with all categories`() = runTest {
        // Setup test data
        val now = Clock.System.now()
        
        mockTextNoteDao.notes = listOf(
            TextNoteEntity(content = "Hello", uid = Uuid.random(), lastUpdated = now, created = now),
            TextNoteEntity(content = "World", uid = Uuid.random(), lastUpdated = now, created = now)
        )
        
        mockImageNoteDao.notes = listOf(
            ImageNoteEntity(
                contentUri = "image1.jpg",
                fileSizeBytes = 1024 * 1024, // 1MB
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )
        
        mockJournalDao.journals = listOf(
            JournalEntity(
                id = Uuid.random(),
                title = "Test Journal",
                description = "A test journal",
                created = now,
                lastUpdated = now
            )
        )

        // Execute
        val result = calculator.calculateTotalUsage()

        // Verify
        assertEquals(100L * 1024L * 1024L * 1024L, result.totalBytes) // 100GB default
        
        val textSize = ("Hello".length + "World".length) * 2L // UTF-16
        val imageSize = 1024L * 1024L // 1MB
        val journalSize = ("Test Journal".length + "A test journal".length) * 2L
        val expectedUsed = textSize + imageSize + journalSize
        
        assertEquals(expectedUsed, result.usedBytes)
        assertEquals(3, result.categories.size) // Only categories with data
    }

    @Test
    fun `calculateCategoryUsage for TEXT_NOTES calculates UTF-16 character size`() = runTest {
        val now = Clock.System.now()
        mockTextNoteDao.notes = listOf(
            TextNoteEntity(content = "Hello", uid = Uuid.random(), lastUpdated = now, created = now), // 5 chars = 10 bytes
            TextNoteEntity(content = "🌟", uid = Uuid.random(), lastUpdated = now, created = now)     // 1 char = 2 bytes
        )

        val result = calculator.calculateCategoryUsage(CloudObjectType.TEXT_NOTES)

        assertEquals(CloudObjectType.TEXT_NOTES, result.category)
        assertEquals(12L, result.sizeBytes) // (5 + 1) * 2 = 12 bytes
        assertEquals(2, result.objectCount)
    }

    @Test
    fun `calculateCategoryUsage for IMAGE_NOTES uses actual file sizes`() = runTest {
        val now = Clock.System.now()
        mockImageNoteDao.notes = listOf(
            ImageNoteEntity(
                contentUri = "image1.jpg",
                fileSizeBytes = 2_000_000, // 2MB
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            ),
            ImageNoteEntity(
                contentUri = "image2.png",
                fileSizeBytes = 5_500_000, // 5.5MB
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )

        val result = calculator.calculateCategoryUsage(CloudObjectType.IMAGE_NOTES)

        assertEquals(CloudObjectType.IMAGE_NOTES, result.category)
        assertEquals(7_500_000L, result.sizeBytes) // 2MB + 5.5MB
        assertEquals(2, result.objectCount)
    }

    @Test
    fun `calculateCategoryUsage validates file sizes and throws on negative size`() = runTest {
        val now = Clock.System.now()
        mockImageNoteDao.notes = listOf(
            ImageNoteEntity(
                contentUri = "corrupt.jpg",
                fileSizeBytes = -100, // Invalid negative size
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )

        val exception = assertFailsWith<IllegalStateException> {
            calculator.calculateCategoryUsage(CloudObjectType.IMAGE_NOTES)
        }
        
        assertTrue(exception.message!!.contains("Invalid negative file size"))
        assertTrue(exception.message!!.contains("image"))
    }

    @Test
    fun `calculateCategoryUsage validates file sizes and throws on zero size`() = runTest {
        val now = Clock.System.now()
        mockVideoNoteDao.notes = listOf(
            VideoNoteEntity(
                contentUri = "empty.mp4",
                fileSizeBytes = 0, // Invalid zero size
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )

        val exception = assertFailsWith<IllegalStateException> {
            calculator.calculateCategoryUsage(CloudObjectType.VIDEO_NOTES)
        }
        
        assertTrue(exception.message!!.contains("Zero file size"))
        assertTrue(exception.message!!.contains("video"))
    }

    @Test
    fun `calculateCategoryUsage validates file sizes and throws on unreasonably large size`() = runTest {
        val now = Clock.System.now()
        mockVoiceNoteDao.notes = listOf(
            VoiceNoteEntity(
                contentUri = "huge.wav",
                fileSizeBytes = 20L * 1024L * 1024L * 1024L, // 20GB - too large
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )

        val exception = assertFailsWith<IllegalStateException> {
            calculator.calculateCategoryUsage(CloudObjectType.VOICE_NOTES)
        }
        
        assertTrue(exception.message!!.contains("Unreasonably large file size"))
        assertTrue(exception.message!!.contains("voice"))
    }

    @Test
    fun `calculateCategoryUsage for JOURNAL_DATA calculates metadata size correctly`() = runTest {
        val now = Clock.System.now()
        mockJournalDao.journals = listOf(
            JournalEntity(
                id = Uuid.random(),
                title = "Short",      // 5 chars
                description = "Test", // 4 chars  
                created = now,
                lastUpdated = now
            ),
            JournalEntity(
                id = Uuid.random(),
                title = "Longer Title",        // 12 chars
                description = "Longer desc",   // 11 chars
                created = now,
                lastUpdated = now
            )
        )

        val result = calculator.calculateCategoryUsage(CloudObjectType.JOURNAL_DATA)

        assertEquals(CloudObjectType.JOURNAL_DATA, result.category)
        assertEquals(64L, result.sizeBytes) // (5+4+12+11) * 2 = 64 bytes UTF-16
        assertEquals(2, result.objectCount)
    }

    @Test
    fun `calculateCategoryUsage for empty categories returns zero`() = runTest {
        // All DAOs return empty lists by default
        
        val textResult = calculator.calculateCategoryUsage(CloudObjectType.TEXT_NOTES)
        assertEquals(0L, textResult.sizeBytes)
        assertEquals(0, textResult.objectCount)
        
        val imageResult = calculator.calculateCategoryUsage(CloudObjectType.IMAGE_NOTES)
        assertEquals(0L, imageResult.sizeBytes)
        assertEquals(0, imageResult.objectCount)
    }

    @Test
    fun `calculateCategoryUsage validates text size limits`() = runTest {
        val now = Clock.System.now()
        val hugeTxt = "x".repeat(6 * 1024 * 1024) // 6MB of text > 10MB limit when UTF-16
        
        mockTextNoteDao.notes = listOf(
            TextNoteEntity(content = hugeTxt, uid = Uuid.random(), lastUpdated = now, created = now)
        )

        val exception = assertFailsWith<IllegalStateException> {
            calculator.calculateCategoryUsage(CloudObjectType.TEXT_NOTES)
        }
        
        assertTrue(exception.message!!.contains("Unreasonably large text size"))
    }

    @Test
    fun `totalUsage correctly aggregates all categories`() = runTest {
        val now = Clock.System.now()
        
        // Add data to multiple categories
        mockTextNoteDao.notes = listOf(
            TextNoteEntity(content = "Test", uid = Uuid.random(), lastUpdated = now, created = now) // 8 bytes
        )
        
        mockImageNoteDao.notes = listOf(
            ImageNoteEntity(
                contentUri = "test.jpg",
                fileSizeBytes = 1000,
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )
        
        mockVideoNoteDao.notes = listOf(
            VideoNoteEntity(
                contentUri = "test.mp4",
                fileSizeBytes = 5000,
                uid = Uuid.random(),
                lastUpdated = now,
                created = now
            )
        )

        val result = calculator.calculateTotalUsage()

        // Should sum all categories: 8 + 1000 + 5000 = 6008 bytes
        assertEquals(6008L, result.usedBytes)
        assertTrue(result.usagePercentage > 0)
        assertTrue(result.availableBytes < result.totalBytes)
    }
}

// Mock DAOs for testing
class MockTextNoteDao : TextNoteDao {
    var notes: List<TextNoteEntity> = emptyList()
    
    override suspend fun getAll(): List<TextNoteEntity> = notes
    
    // Unused methods for testing
    override fun getNote(uid: Uuid) = TODO()
    override suspend fun getNoteOneOff(uid: Uuid) = TODO()
    override fun getAllNotes() = TODO()
    override fun getRecentNotes(limit: Int) = TODO()
    override fun getNotesPage(limit: Int, offset: Int) = TODO()
    override fun getNotesInRange(startTimestamp: Long, endTimestamp: Long) = TODO()
    override suspend fun addNote(note: TextNoteEntity) = TODO()
    override suspend fun removeNote(noteId: Uuid) = TODO()
    override suspend fun removeNote(noteIds: List<Uuid>) = TODO()
    override suspend fun getTextNotesByContent(content: String) = TODO()
}

class MockImageNoteDao : ImageNoteDao {
    var notes: List<ImageNoteEntity> = emptyList()
    
    override suspend fun getAll(): List<ImageNoteEntity> = notes
    
    // Unused methods for testing
    override fun getNote(uid: Uuid) = TODO()
    override suspend fun getNoteOneOff(uid: Uuid) = TODO()
    override fun getAllNotes() = TODO()
    override fun getRecentNotes(limit: Int) = TODO()
    override fun getNotesPage(limit: Int, offset: Int) = TODO()
    override fun getNotesInRange(startTimestamp: Long, endTimestamp: Long) = TODO()
    override suspend fun addNote(note: ImageNoteEntity) = TODO()
    override suspend fun removeNote(noteId: Uuid) = TODO()
    override suspend fun removeNote(noteIds: List<Uuid>) = TODO()
}

class MockVideoNoteDao : VideoNoteDao {
    var notes: List<VideoNoteEntity> = emptyList()
    
    override suspend fun getAll(): List<VideoNoteEntity> = notes
    
    // Unused methods for testing
    override fun getNote(uid: Uuid) = TODO()
    override suspend fun getNoteOneOff(uid: Uuid) = TODO()
    override fun getAllNotes() = TODO()
    override fun getRecentNotes(limit: Int) = TODO()
    override fun getNotesPage(limit: Int, offset: Int) = TODO()
    override fun getNotesInRange(startTimestamp: Long, endTimestamp: Long) = TODO()
    override suspend fun addNote(note: VideoNoteEntity) = TODO()
    override suspend fun removeNote(noteId: Uuid) = TODO()
    override suspend fun removeNote(noteIds: List<Uuid>) = TODO()
}

class MockAudioNoteDao : AudioNoteDao {
    var notes: List<VoiceNoteEntity> = emptyList()
    
    override suspend fun getAll(): List<VoiceNoteEntity> = notes
    
    // Unused methods for testing
    override fun getNote(uid: Uuid) = TODO()
    override suspend fun getNoteOneOff(uid: Uuid) = TODO()
    override fun getAllNotes() = TODO()
    override fun getRecentNotes(limit: Int) = TODO()
    override fun getNotesPage(limit: Int, offset: Int) = TODO()
    override fun getNotesInRange(startTimestamp: Long, endTimestamp: Long) = TODO()
    override suspend fun addNote(note: VoiceNoteEntity) = TODO()
    override suspend fun removeNote(noteId: Uuid) = TODO()
    override suspend fun removeNote(noteIds: List<Uuid>) = TODO()
}

class MockJournalDao : JournalDao {
    var journals: List<JournalEntity> = emptyList()
    
    override suspend fun getAll(): List<JournalEntity> = journals
    
    // Unused methods for testing
    override fun observeJournalById(id: Uuid) = TODO()
    override fun observeAll() = TODO()
    override suspend fun create(journal: JournalEntity) = TODO()
    override suspend fun update(journal: JournalEntity) = TODO()
    override suspend fun delete(journalId: Uuid) = TODO()
}