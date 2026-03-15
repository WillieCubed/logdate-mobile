package app.logdate.client.sync.datalayer

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.journals.NoteType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NoteDataMapperTest {
    private val mapper = NoteDataMapper()

    private val fixedTime = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val fixedUuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    // =======================================================================
    // Text note round-trips
    // =======================================================================

    @Test
    fun textNoteRoundTrip() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "Hello from watch",
                syncVersion = 1,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
    }

    @Test
    fun textNoteWithLocationRoundTrips() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "At the park",
                syncVersion = 0,
                location =
                    NoteLocation(
                        coordinates =
                            NoteCoordinates(
                                latitude = 37.7749,
                                longitude = -122.4194,
                                altitude = 10.0,
                                accuracy = 5.0f,
                            ),
                        place =
                            NotePlace(
                                id = Uuid.parse("660e8400-e29b-41d4-a716-446655440000"),
                                name = "Golden Gate Park",
                                latitude = 37.7694,
                                longitude = -122.4862,
                            ),
                    ),
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
        assertNotNull(restored.location)
        assertNotNull(restored.location?.coordinates)
        assertNotNull(restored.location?.place)
        assertEquals("Golden Gate Park", restored.location?.place?.name)
    }

    @Test
    fun textNoteWithNullLocationRoundTrips() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "No location",
                location = null,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertNull(restored.location)
    }

    @Test
    fun textNoteWithEmptyContentRoundTrips() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "",
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals("", (restored as JournalNote.Text).content)
    }

    @Test
    fun textNoteWithUnicodeContentRoundTrips() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "Hello \uD83D\uDE00 world \u2764\uFE0F \u5F88\u597D",
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note.content, (restored as JournalNote.Text).content)
    }

    @Test
    fun textNoteWithLongContentRoundTrips() {
        val longContent = "A".repeat(10_000)
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = longContent,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(longContent, (restored as JournalNote.Text).content)
    }

    // =======================================================================
    // Audio note round-trips (critical for watch→phone sync)
    // =======================================================================

    @Test
    fun audioNoteRoundTrip() {
        val note =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/emulated/0/Android/data/app.logdate.wear/files/recordings/recording_001.aac",
                durationMs = 4200,
                syncVersion = 2,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
        assertEquals(
            "/storage/emulated/0/Android/data/app.logdate.wear/files/recordings/recording_001.aac",
            (restored as JournalNote.Audio).mediaRef,
        )
        assertEquals(4200, restored.durationMs)
    }

    @Test
    fun audioNoteWithZeroDurationRoundTrips() {
        val note =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/audio/empty.aac",
                durationMs = 0,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(0, (restored as JournalNote.Audio).durationMs)
    }

    @Test
    fun audioNoteWithLongDurationRoundTrips() {
        val note =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/audio/long_recording.aac",
                durationMs = 60_000, // 60 seconds, the max for walkie-talkie
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(60_000, (restored as JournalNote.Audio).durationMs)
    }

    @Test
    fun audioNoteWithLocationRoundTrips() {
        val note =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/audio/at_park.aac",
                durationMs = 5000,
                location =
                    NoteLocation(
                        coordinates =
                            NoteCoordinates(
                                latitude = 40.7128,
                                longitude = -74.0060,
                            ),
                    ),
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertNotNull(restored.location)
        assertEquals(40.7128, restored.location?.coordinates?.latitude)
    }

    @Test
    fun audioNotePreservesAllFields() {
        val note =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = Instant.fromEpochMilliseconds(fixedTime.toEpochMilliseconds() + 1000),
                mediaRef = "/path/to/audio.aac",
                durationMs = 12345,
                syncVersion = 42,
                location =
                    NoteLocation(
                        coordinates = NoteCoordinates(latitude = 1.0, longitude = 2.0),
                    ),
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map) as JournalNote.Audio

        assertEquals(fixedUuid, restored.uid)
        assertEquals(fixedTime, restored.creationTimestamp)
        assertEquals(fixedTime.toEpochMilliseconds() + 1000, restored.lastUpdated.toEpochMilliseconds())
        assertEquals("/path/to/audio.aac", restored.mediaRef)
        assertEquals(12345, restored.durationMs)
        assertEquals(42, restored.syncVersion)
    }

    // =======================================================================
    // Image note round-trips
    // =======================================================================

    @Test
    fun imageNoteRoundTrip() {
        val note =
            JournalNote.Image(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/images/photo_001.jpg",
                caption = "Sunset at the beach",
                syncVersion = 0,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
    }

    @Test
    fun imageNoteWithEmptyCaptionRoundTrips() {
        val note =
            JournalNote.Image(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/images/photo.jpg",
                caption = "",
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals("", (restored as JournalNote.Image).caption)
    }

    // =======================================================================
    // Video note round-trips
    // =======================================================================

    @Test
    fun videoNoteRoundTrip() {
        val note =
            JournalNote.Video(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/video/clip.mp4",
                caption = "Family gathering",
                syncVersion = 3,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
    }

    // =======================================================================
    // Note type preservation
    // =======================================================================

    @Test
    fun noteTypeIsPreservedInDataMap() {
        val types =
            mapOf(
                NoteType.TEXT to
                    JournalNote.Text(
                        uid = fixedUuid,
                        creationTimestamp = fixedTime,
                        lastUpdated = fixedTime,
                        content = "test",
                    ),
                NoteType.AUDIO to
                    JournalNote.Audio(
                        uid = fixedUuid,
                        creationTimestamp = fixedTime,
                        lastUpdated = fixedTime,
                        mediaRef = "/test.aac",
                    ),
                NoteType.IMAGE to
                    JournalNote.Image(
                        uid = fixedUuid,
                        creationTimestamp = fixedTime,
                        lastUpdated = fixedTime,
                        mediaRef = "/test.jpg",
                    ),
                NoteType.VIDEO to
                    JournalNote.Video(
                        uid = fixedUuid,
                        creationTimestamp = fixedTime,
                        lastUpdated = fixedTime,
                        mediaRef = "/test.mp4",
                    ),
            )

        for ((expectedType, note) in types) {
            val map = mapper.toDataMap(note)
            assertEquals(expectedType.name, map[NoteDataMapper.KEY_NOTE_TYPE])
        }
    }

    // =======================================================================
    // Data map key validation
    // =======================================================================

    @Test
    fun dataMapContainsRequiredKeys() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "test",
            )

        val map = mapper.toDataMap(note)

        assertNotNull(map[NoteDataMapper.KEY_UID])
        assertNotNull(map[NoteDataMapper.KEY_NOTE_TYPE])
        assertNotNull(map[NoteDataMapper.KEY_JSON_PAYLOAD])
        assertEquals(fixedUuid.toString(), map[NoteDataMapper.KEY_UID])
    }

    // =======================================================================
    // Error handling
    // =======================================================================

    @Test
    fun fromDataMapThrowsOnMissingPayload() {
        val map =
            mapOf(
                NoteDataMapper.KEY_UID to fixedUuid.toString(),
                NoteDataMapper.KEY_NOTE_TYPE to "TEXT",
            )

        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(map)
        }
    }

    @Test
    fun fromDataMapThrowsOnEmptyMap() {
        assertFailsWith<IllegalArgumentException> {
            mapper.fromDataMap(emptyMap())
        }
    }

    @Test
    fun fromDataMapThrowsOnInvalidJson() {
        val map =
            mapOf(
                NoteDataMapper.KEY_UID to fixedUuid.toString(),
                NoteDataMapper.KEY_NOTE_TYPE to "TEXT",
                NoteDataMapper.KEY_JSON_PAYLOAD to "not valid json",
            )

        assertFailsWith<Exception> {
            mapper.fromDataMap(map)
        }
    }

    // =======================================================================
    // Path generation and parsing
    // =======================================================================

    @Test
    fun notePathUsesUid() {
        val path = NoteDataMapper.notePath(fixedUuid)
        assertEquals("/logdate/notes/550e8400-e29b-41d4-a716-446655440000", path)
    }

    @Test
    fun noteDeletePathUsesUid() {
        val path = NoteDataMapper.noteDeletePath(fixedUuid)
        assertEquals("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/delete", path)
    }

    @Test
    fun isNotePathReturnsTrueForNoteDataPaths() {
        assertTrue(NoteDataMapper.isNotePath("/logdate/notes/550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun isNotePathReturnsFalseForDeletePaths() {
        assertFalse(NoteDataMapper.isNotePath("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/delete"))
    }

    @Test
    fun isNotePathReturnsFalseForUnrelatedPaths() {
        assertFalse(NoteDataMapper.isNotePath("/logdate/journals/some-id"))
        assertFalse(NoteDataMapper.isNotePath("/other/path"))
    }

    @Test
    fun isDeletePathReturnsTrueForDeletePaths() {
        assertTrue(NoteDataMapper.isDeletePath("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/delete"))
    }

    @Test
    fun isDeletePathReturnsFalseForNonDeletePaths() {
        assertFalse(NoteDataMapper.isDeletePath("/logdate/notes/550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun noteIdFromPathExtractsCorrectUuid() {
        val path = "/logdate/notes/550e8400-e29b-41d4-a716-446655440000"
        val extracted = NoteDataMapper.noteIdFromPath(path)
        assertEquals(fixedUuid, extracted)
    }

    @Test
    fun noteIdFromDeletePathExtractsCorrectUuid() {
        val path = "/logdate/notes/550e8400-e29b-41d4-a716-446655440000/delete"
        val extracted = NoteDataMapper.noteIdFromPath(path)
        assertEquals(fixedUuid, extracted)
    }

    @Test
    fun noteIdFromPathThrowsOnInvalidUuid() {
        assertFailsWith<Exception> {
            NoteDataMapper.noteIdFromPath("/logdate/notes/not-a-uuid")
        }
    }

    // =======================================================================
    // Multiple notes in sequence (simulates batch sync)
    // =======================================================================

    @Test
    fun multipleNotesSerializeAndDeserializeIndependently() {
        val notes =
            listOf(
                JournalNote.Text(uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime, content = "Note 1"),
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/audio1.aac",
                    durationMs = 3000,
                ),
                JournalNote.Text(uid = Uuid.random(), creationTimestamp = fixedTime, lastUpdated = fixedTime, content = "Note 3"),
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/audio2.aac",
                    durationMs = 7500,
                ),
            )

        val maps = notes.map { mapper.toDataMap(it) }
        val restored = maps.map { mapper.fromDataMap(it) }

        assertEquals(notes.size, restored.size)
        for (i in notes.indices) {
            assertEquals(notes[i], restored[i])
        }
    }

    // =======================================================================
    // Sync version preserved through round-trip
    // =======================================================================

    @Test
    fun syncVersionPreservedForAllNoteTypes() {
        val textNote =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "test",
                syncVersion = 99,
            )
        val audioNote =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/test.aac",
                syncVersion = 42,
            )

        assertEquals(99, mapper.fromDataMap(mapper.toDataMap(textNote)).syncVersion)
        assertEquals(42, mapper.fromDataMap(mapper.toDataMap(audioNote)).syncVersion)
    }
}
