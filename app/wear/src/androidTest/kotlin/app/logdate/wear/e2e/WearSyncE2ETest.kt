package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.sync.datalayer.NoteDataMapper
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented tests verifying the Data Layer sync pipeline on a real Wear OS device/emulator.
 *
 * This suite exercises the [NoteDataMapper] and the underlying Data Layer serialization
 * logic within the actual Android runtime environment. It ensures that complex journal
 * notes—including those with audio metadata and location coordinates—correctly survive
 * round-trip serialization. It also validates the path generation logic used for
 * cross-device communication between the watch and phone.
 */
@RunWith(AndroidJUnit4::class)
class WearSyncE2ETest {
    private val mapper = NoteDataMapper()
    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000)

    // =======================================================================
    // Audio note serialization on real Android runtime
    // =======================================================================

    @Test
    fun audioNoteSurvivesSerializationOnDevice() {
        val noteId = Uuid.random()
        val note =
            JournalNote.Audio(
                uid = noteId,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/data/data/app.logdate.wear/files/recordings/rec_$noteId.aac",
                durationMs = 4200,
                syncVersion = 0,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
        assertTrue(restored is JournalNote.Audio)
        assertEquals(noteId, restored.uid)
        assertEquals(4200, restored.durationMs)
        assertEquals(note.mediaRef, (restored as JournalNote.Audio).mediaRef)
    }

    @Test
    fun audioNoteWithLocationSurvivesSerializationOnDevice() {
        val note =
            JournalNote.Audio(
                uid = Uuid.random(),
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/audio/recording.aac",
                durationMs = 10000,
                location =
                    NoteLocation(
                        coordinates =
                            NoteCoordinates(
                                latitude = 37.7749,
                                longitude = -122.4194,
                                altitude = 15.0,
                                accuracy = 3.5f,
                            ),
                    ),
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertNotNull(restored.location)
        assertEquals(37.7749, restored.location?.coordinates?.latitude)
        assertEquals(-122.4194, restored.location?.coordinates?.longitude)
    }

    // =======================================================================
    // Batch serialization (multiple notes queued offline)
    // =======================================================================

    @Test
    fun batchOfNotesSerializeAndDeserializeOnDevice() {
        val notes =
            listOf(
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/storage/rec1.aac",
                    durationMs = 2000,
                ),
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    content = "Quick thought after recording",
                ),
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/storage/rec2.aac",
                    durationMs = 58000,
                ),
            )

        for (note in notes) {
            val map = mapper.toDataMap(note)
            val restored = mapper.fromDataMap(map)
            assertEquals(note, restored)
        }
    }

    // =======================================================================
    // Path generation for audio sync
    // =======================================================================

    @Test
    fun audioNotePathIsCorrect() {
        val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val path = NoteDataMapper.notePath(noteId)
        assertEquals("/logdate/notes/550e8400-e29b-41d4-a716-446655440000", path)
        assertTrue(NoteDataMapper.isNotePath(path))
    }

    @Test
    fun audioChannelPathDerivedFromNotePath() {
        val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val channelPath = "${NoteDataMapper.notePath(noteId)}/audio"
        assertEquals("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/audio", channelPath)
    }

    @Test
    fun deletePathIsDistinctFromNotePath() {
        val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val notePath = NoteDataMapper.notePath(noteId)
        val deletePath = NoteDataMapper.noteDeletePath(noteId)

        assertTrue(NoteDataMapper.isNotePath(notePath))
        assertTrue(NoteDataMapper.isDeletePath(deletePath))
        assertTrue(!NoteDataMapper.isNotePath(deletePath))
        assertTrue(!NoteDataMapper.isDeletePath(notePath))
    }

    // =======================================================================
    // Uuid round-trip through path
    // =======================================================================

    @Test
    fun uuidSurvivesPathRoundTrip() {
        val originalId = Uuid.random()
        val path = NoteDataMapper.notePath(originalId)
        val extracted = NoteDataMapper.noteIdFromPath(path)
        assertEquals(originalId, extracted)
    }

    @Test
    fun uuidSurvivesDeletePathRoundTrip() {
        val originalId = Uuid.random()
        val path = NoteDataMapper.noteDeletePath(originalId)
        val extracted = NoteDataMapper.noteIdFromPath(path)
        assertEquals(originalId, extracted)
    }

    // =======================================================================
    // Data map key integrity
    // =======================================================================

    @Test
    fun dataMapContainsAllRequiredKeys() {
        val note =
            JournalNote.Audio(
                uid = Uuid.random(),
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/audio.aac",
                durationMs = 5000,
            )

        val map = mapper.toDataMap(note)

        assertTrue(map.containsKey(NoteDataMapper.KEY_UID))
        assertTrue(map.containsKey(NoteDataMapper.KEY_NOTE_TYPE))
        assertTrue(map.containsKey(NoteDataMapper.KEY_JSON_PAYLOAD))
        assertEquals("AUDIO", map[NoteDataMapper.KEY_NOTE_TYPE])
    }

    // =======================================================================
    // Simulates what phone's listener does: receive data map → create note
    // =======================================================================

    @Test
    fun phoneListenerCanDeserializeWatchAudioNote() {
        // Watch side: create and serialize
        val watchNote =
            JournalNote.Audio(
                uid = Uuid.random(),
                creationTimestamp = Instant.fromEpochMilliseconds(1_710_000_123_456),
                lastUpdated = Instant.fromEpochMilliseconds(1_710_000_123_789),
                mediaRef = "/data/data/app.logdate.wear/files/recordings/walkie_talkie_001.aac",
                durationMs = 4200,
                syncVersion = 0,
            )
        val dataMap = mapper.toDataMap(watchNote)

        // Simulate phone side: strip internal keys (as the listener does)
        val phoneMapper = NoteDataMapper()
        val phoneReceivedNote = phoneMapper.fromDataMap(dataMap)

        // Exact match
        assertEquals(watchNote.uid, phoneReceivedNote.uid)
        assertEquals(watchNote.creationTimestamp, phoneReceivedNote.creationTimestamp)
        assertEquals(watchNote.lastUpdated, phoneReceivedNote.lastUpdated)
        assertTrue(phoneReceivedNote is JournalNote.Audio)
        val audioNote = phoneReceivedNote as JournalNote.Audio
        assertEquals(watchNote.mediaRef, audioNote.mediaRef)
        assertEquals(watchNote.durationMs, audioNote.durationMs)
    }

    // =======================================================================
    // Edge case: very long media ref path
    // =======================================================================

    @Test
    fun longMediaRefPathSurvivesRoundTrip() {
        val longPath = "/data/data/app.logdate.wear/files/recordings/" + "a".repeat(200) + ".aac"
        val note =
            JournalNote.Audio(
                uid = Uuid.random(),
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = longPath,
                durationMs = 1000,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map) as JournalNote.Audio

        assertEquals(longPath, restored.mediaRef)
    }
}
