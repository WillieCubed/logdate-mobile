package app.logdate.client.database.entities

import app.logdate.client.database.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class EntityStructureTest : BaseDatabaseTest() {

    @Test
    fun journalEntity_creation_hasCorrectProperties() = runTest {
        val now = Clock.System.now()
        val journal = JournalEntity(
            id = Uuid.random(),
            title = "Test Journal",
            description = "Test Description",
            created = now,
            lastUpdated = now
        )
        
        assertNotNull(journal.id)
        assertEquals("Test Journal", journal.title)
        assertEquals("Test Description", journal.description)
        assertEquals(now, journal.created)
        assertEquals(now, journal.lastUpdated)
    }

    @Test
    fun textNoteEntity_creation_hasCorrectProperties() = runTest {
        val now = Clock.System.now()
        val uid = Uuid.random()
        val note = TextNoteEntity(
            uid = uid,
            content = "Test content",
            created = now,
            lastUpdated = now
        )
        
        assertEquals(uid, note.uid)
        assertEquals("Test content", note.content)
        assertEquals(now, note.created)
        assertEquals(now, note.lastUpdated)
    }

    @Test
    fun imageNoteEntity_creation_hasCorrectProperties() = runTest {
        val now = Clock.System.now()
        val uid = Uuid.random()
        val note = ImageNoteEntity(
            uid = uid,
            contentUri = "image.jpg",
            created = now,
            lastUpdated = now
        )
        
        assertEquals(uid, note.uid)
        assertEquals("image.jpg", note.contentUri)
        assertEquals(now, note.created)
        assertEquals(now, note.lastUpdated)
    }

    @Test
    fun journalNoteCrossRef_creation_hasCorrectProperties() = runTest {
        val journalId = Uuid.random()
        val noteId = Uuid.random()
        val crossRef = JournalNoteCrossRef(
            journalId = journalId,
            noteId = noteId
        )
        
        assertEquals(journalId, crossRef.journalId)
        assertEquals(noteId, crossRef.noteId)
    }

    @Test
    fun entitiesWithSameData_areEqual() = runTest {
        val now = Clock.System.now()
        val uid = Uuid.random()
        
        val journal1 = JournalEntity(
            id = uid,
            title = "Test",
            description = "Desc",
            created = now,
            lastUpdated = now
        )
        
        val journal2 = JournalEntity(
            id = uid,
            title = "Test",
            description = "Desc",
            created = now,
            lastUpdated = now
        )
        
        assertEquals(journal1, journal2)
        assertEquals(journal1.hashCode(), journal2.hashCode())
    }

    @Test
    fun entitiesWithDifferentData_areNotEqual() = runTest {
        val now = Clock.System.now()
        
        val journal1 = JournalEntity(
            id = Uuid.random(),
            title = "Test1",
            description = "Desc",
            created = now,
            lastUpdated = now
        )
        
        val journal2 = JournalEntity(
            id = Uuid.random(),
            title = "Test2",
            description = "Desc",
            created = now,
            lastUpdated = now
        )
        
        assertNotEquals(journal1, journal2)
    }

    @Test
    fun entityCopy_modifiesOnlySpecifiedFields() = runTest {
        val now = Clock.System.now()
        val original = JournalEntity(
            id = Uuid.random(),
            title = "Original",
            description = "Original Desc",
            created = now,
            lastUpdated = now
        )
        
        val modified = original.copy(title = "Modified")
        
        assertEquals("Modified", modified.title)
        assertEquals(original.description, modified.description)
        assertEquals(original.id, modified.id)
        assertEquals(original.created, modified.created)
        assertEquals(original.lastUpdated, modified.lastUpdated)
    }

    @Test
    fun noteEntity_implementsGenericNoteData() = runTest {
        val now = Clock.System.now()
        val uid = Uuid.random()
        
        val textNote = TextNoteEntity(
            uid = uid,
            content = "Test",
            created = now,
            lastUpdated = now
        )
        
        val imageNote = ImageNoteEntity(
            uid = uid,
            contentUri = "image.jpg",
            created = now,
            lastUpdated = now
        )
        
        // Both should extend GenericNoteData
        val genericText: GenericNoteData = textNote
        val genericImage: GenericNoteData = imageNote
        
        assertEquals(uid, genericText.uid)
        assertEquals(uid, genericImage.uid)
        assertEquals(now, genericText.created)
        assertEquals(now, genericImage.created)
    }

    @Test
    fun uuidGeneration_createsUniqueIds() = runTest {
        val entity1 = JournalEntity(
            id = Uuid.random(),
            title = "Test1",
            description = "Desc",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val entity2 = JournalEntity(
            id = Uuid.random(),
            title = "Test2",
            description = "Desc",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        assertNotEquals(entity1.id, entity2.id)
    }
}