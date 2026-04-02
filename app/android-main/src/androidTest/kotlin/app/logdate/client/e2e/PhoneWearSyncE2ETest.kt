package app.logdate.client.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.sync.datalayer.NoteDataMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests verifying the phone side of the watch↔phone Data Layer sync.
 *
 * These tests run on a real Android phone/emulator and verify that:
 * - Data serialized by the watch can be deserialized by the phone
 * - The same NoteDataMapper produces identical results on both sides
 * - Audio note metadata is fully preserved through the sync pipeline
 * - Delete signals are correctly identified
 *
 * The actual DataClient transport is not tested here (requires paired devices).
 * These tests verify the data contract between watch and phone.
 */
@RunWith(AndroidJUnit4::class)
class PhoneWearSyncE2ETest {

    private val mapper = NoteDataMapper()
    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000)

    // =======================================================================
    // Phone receives audio note from watch
    // =======================================================================

    @Test
    fun phoneCanDeserializeWatchAudioNote() {
        val noteId = Uuid.random()
        val watchNote = JournalNote.Audio(
            uid = noteId,
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/data/data/app.logdate.wear/files/recordings/rec_$noteId.aac",
            durationMs = 4200,
            syncVersion = 0,
        )

        // Simulate watch serialization
        val dataMap = mapper.toDataMap(watchNote)

        // Phone deserialization (same as PhoneDataLayerListenerService)
        val phoneNote = mapper.fromDataMap(dataMap)

        assertTrue(phoneNote is JournalNote.Audio)
        assertEquals(noteId, phoneNote.uid)
        assertEquals(4200, phoneNote.durationMs)
        assertEquals(watchNote.mediaRef, phoneNote.mediaRef)
        assertEquals(fixedTime, phoneNote.creationTimestamp)
    }

    @Test
    fun phoneCanDeserializeWatchAudioNoteWithLocation() {
        val watchNote = JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/recording.aac",
            durationMs = 10000,
            location = NoteLocation(
                coordinates = NoteCoordinates(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    altitude = 15.0,
                    accuracy = 3.5f,
                ),
            ),
        )

        val dataMap = mapper.toDataMap(watchNote)
        val phoneNote = mapper.fromDataMap(dataMap)

        assertNotNull(phoneNote.location)
        assertEquals(37.7749, phoneNote.location?.coordinates?.latitude)
        assertEquals(-122.4194, phoneNote.location?.coordinates?.longitude)
        assertEquals(15.0, phoneNote.location?.coordinates?.altitude)
        assertEquals(3.5f, phoneNote.location?.coordinates?.accuracy)
    }

    // =======================================================================
    // Phone receives text note from watch
    // =======================================================================

    @Test
    fun phoneCanDeserializeWatchTextNote() {
        val watchNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = "Quick thought from my watch",
        )

        val dataMap = mapper.toDataMap(watchNote)
        val phoneNote = mapper.fromDataMap(dataMap)

        assertTrue(phoneNote is JournalNote.Text)
        assertEquals("Quick thought from my watch", phoneNote.content)
    }

    @Test
    fun phoneCanDeserializeMoodNoteFromWatch() {
        val watchNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = "#mood:good Feeling great today",
        )

        val dataMap = mapper.toDataMap(watchNote)
        val phoneNote = mapper.fromDataMap(dataMap) as JournalNote.Text

        assertTrue(phoneNote.content.startsWith("#mood:"))
    }

    // =======================================================================
    // Phone receives batch of notes from watch
    // =======================================================================

    @Test
    fun phoneCanDeserializeBatchOfMixedNotes() {
        val notes = listOf(
            JournalNote.Audio(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                mediaRef = "/rec1.aac", durationMs = 3000,
            ),
            JournalNote.Text(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                content = "Text entry",
            ),
            JournalNote.Audio(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                mediaRef = "/rec2.aac", durationMs = 58000,
            ),
            JournalNote.Text(
                uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime,
                content = "#mood:great",
            ),
        )

        for (watchNote in notes) {
            val dataMap = mapper.toDataMap(watchNote)
            val phoneNote = mapper.fromDataMap(dataMap)
            assertEquals(watchNote, phoneNote)
        }
    }

    // =======================================================================
    // Phone identifies delete signals
    // =======================================================================

    @Test
    fun phoneIdentifiesDeletePath() {
        val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val deletePath = NoteDataMapper.noteDeletePath(noteId)

        assertTrue(NoteDataMapper.isDeletePath(deletePath))
        assertEquals(noteId, NoteDataMapper.noteIdFromPath(deletePath))
    }

    @Test
    fun phoneDistinguishesNotePathFromDeletePath() {
        val noteId = Uuid.random()
        val notePath = NoteDataMapper.notePath(noteId)
        val deletePath = NoteDataMapper.noteDeletePath(noteId)

        assertTrue(NoteDataMapper.isNotePath(notePath))
        assertTrue(!NoteDataMapper.isDeletePath(notePath))

        assertTrue(NoteDataMapper.isDeletePath(deletePath))
        assertTrue(!NoteDataMapper.isNotePath(deletePath))
    }

    // =======================================================================
    // Data contract: syncVersion preserved
    // =======================================================================

    @Test
    fun syncVersionPreservedThroughSync() {
        val watchNote = JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/audio.aac",
            durationMs = 5000,
            syncVersion = 7,
        )

        val dataMap = mapper.toDataMap(watchNote)
        val phoneNote = mapper.fromDataMap(dataMap)

        assertEquals(7, phoneNote.syncVersion)
    }

    // =======================================================================
    // Data contract: timestamps preserved with millisecond precision
    // =======================================================================

    @Test
    fun timestampsPreservedWithMillisecondPrecision() {
        val preciseTime = Instant.fromEpochMilliseconds(1_710_000_123_456)
        val preciseUpdate = Instant.fromEpochMilliseconds(1_710_000_123_789)

        val watchNote = JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = preciseTime,
            lastUpdated = preciseUpdate,
            mediaRef = "/storage/audio.aac",
            durationMs = 5000,
        )

        val dataMap = mapper.toDataMap(watchNote)
        val phoneNote = mapper.fromDataMap(dataMap)

        assertEquals(preciseTime.toEpochMilliseconds(), phoneNote.creationTimestamp.toEpochMilliseconds())
        assertEquals(preciseUpdate.toEpochMilliseconds(), phoneNote.lastUpdated.toEpochMilliseconds())
    }

    // =======================================================================
    // Data contract: UIDs are unique and preserved
    // =======================================================================

    @Test
    fun noteUidsArePreservedAndUnique() {
        val uid1 = Uuid.random()
        val uid2 = Uuid.random()

        val note1 = JournalNote.Audio(
            uid = uid1, creationTimestamp = fixedTime, lastUpdated = fixedTime,
            mediaRef = "/a.aac", durationMs = 1000,
        )
        val note2 = JournalNote.Audio(
            uid = uid2, creationTimestamp = fixedTime, lastUpdated = fixedTime,
            mediaRef = "/b.aac", durationMs = 2000,
        )

        val restored1 = mapper.fromDataMap(mapper.toDataMap(note1))
        val restored2 = mapper.fromDataMap(mapper.toDataMap(note2))

        assertEquals(uid1, restored1.uid)
        assertEquals(uid2, restored2.uid)
        assertTrue(restored1.uid != restored2.uid)
    }

    // =======================================================================
    // Simulates the full watch→phone pipeline data integrity check
    // =======================================================================

    @Test
    fun fullPipelineDataIntegrity_audioRecordingToPhoneNote() {
        // Step 1: Watch records audio and creates note
        val recordingNoteId = Uuid.random()
        val recordingTimestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val watchCreatedNote = JournalNote.Audio(
            uid = recordingNoteId,
            creationTimestamp = recordingTimestamp,
            lastUpdated = recordingTimestamp,
            mediaRef = "/data/data/app.logdate.wear/files/recordings/walkie_talkie_$recordingNoteId.aac",
            durationMs = 4200,
            syncVersion = 0,
            location = NoteLocation(
                coordinates = NoteCoordinates(
                    latitude = 40.7128,
                    longitude = -74.0060,
                ),
            ),
        )

        // Step 2: Watch serializes for Data Layer transport
        val watchDataMap = mapper.toDataMap(watchCreatedNote)

        // Step 3: Verify the data map has the right metadata
        assertEquals(recordingNoteId.toString(), watchDataMap[NoteDataMapper.KEY_UID])
        assertEquals("AUDIO", watchDataMap[NoteDataMapper.KEY_NOTE_TYPE])
        assertNotNull(watchDataMap[NoteDataMapper.KEY_JSON_PAYLOAD])

        // Step 4: Simulate phone receiving and deserializing
        // (In production, this goes through DataItem → DataMapItem → string map)
        val phoneMapper = NoteDataMapper()
        val phoneReceivedNote = phoneMapper.fromDataMap(watchDataMap)

        // Step 5: Verify complete fidelity
        assertTrue(phoneReceivedNote is JournalNote.Audio)
        val audioNote: JournalNote.Audio = phoneReceivedNote

        assertEquals(recordingNoteId, audioNote.uid)
        assertEquals(recordingTimestamp.toEpochMilliseconds(),
            audioNote.creationTimestamp.toEpochMilliseconds())
        assertEquals(4200, audioNote.durationMs)
        assertEquals(watchCreatedNote.mediaRef, audioNote.mediaRef)
        assertEquals(0, audioNote.syncVersion)
        assertNotNull(audioNote.location)
        assertEquals(40.7128, audioNote.location?.coordinates?.latitude)
        assertEquals(-74.0060, audioNote.location?.coordinates?.longitude)

        // Full object equality
        assertEquals(watchCreatedNote, phoneReceivedNote)
    }
}
