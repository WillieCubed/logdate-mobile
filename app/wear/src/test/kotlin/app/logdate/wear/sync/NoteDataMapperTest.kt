package app.logdate.wear.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.journals.NoteType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NoteDataMapperTest {
    private val mapper = NoteDataMapper()

    private val fixedTime = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val fixedUuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    // -----------------------------------------------------------------------
    // Text note round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `text note serializes to map and back`() {
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
    fun `text note with location round-trips`() {
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
    }

    @Test
    fun `text note with null location round-trips`() {
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

    // -----------------------------------------------------------------------
    // Audio note round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `audio note serializes to map and back`() {
        val note =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/audio/recording_001.aac",
                durationMs = 4200,
                syncVersion = 2,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
    }

    @Test
    fun `audio note with zero duration round-trips`() {
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

    // -----------------------------------------------------------------------
    // Image note round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `image note serializes to map and back`() {
        val note =
            JournalNote.Image(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/images/photo_001.jpg",
                caption = "Sunset",
                syncVersion = 0,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
    }

    @Test
    fun `image note with empty caption round-trips`() {
        val note =
            JournalNote.Image(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/images/photo_002.jpg",
                caption = "",
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals("", (restored as JournalNote.Image).caption)
    }

    // -----------------------------------------------------------------------
    // Video note round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `video note serializes to map and back`() {
        val note =
            JournalNote.Video(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/storage/video/clip_001.mp4",
                caption = "Family gathering",
                syncVersion = 3,
            )

        val map = mapper.toDataMap(note)
        val restored = mapper.fromDataMap(map)

        assertEquals(note, restored)
    }

    // -----------------------------------------------------------------------
    // Note type preserved
    // -----------------------------------------------------------------------

    @Test
    fun `note type is preserved in data map`() {
        val textNote =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "test",
            )
        val audioNote =
            JournalNote.Audio(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                mediaRef = "/test.aac",
            )

        val textMap = mapper.toDataMap(textNote)
        val audioMap = mapper.toDataMap(audioNote)

        assertEquals(NoteType.TEXT.name, textMap[NoteDataMapper.KEY_NOTE_TYPE])
        assertEquals(NoteType.AUDIO.name, audioMap[NoteDataMapper.KEY_NOTE_TYPE])
    }

    // -----------------------------------------------------------------------
    // Data map contains expected keys
    // -----------------------------------------------------------------------

    @Test
    fun `data map contains uid key`() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "test",
            )

        val map = mapper.toDataMap(note)

        assertEquals(fixedUuid.toString(), map[NoteDataMapper.KEY_UID])
    }

    @Test
    fun `data map contains json payload`() {
        val note =
            JournalNote.Text(
                uid = fixedUuid,
                creationTimestamp = fixedTime,
                lastUpdated = fixedTime,
                content = "test",
            )

        val map = mapper.toDataMap(note)

        assertNotNull(map[NoteDataMapper.KEY_JSON_PAYLOAD])
    }

    // -----------------------------------------------------------------------
    // Path generation
    // -----------------------------------------------------------------------

    @Test
    fun `note path uses uid`() {
        val path = NoteDataMapper.notePath(fixedUuid)
        assertEquals("/logdate/notes/550e8400-e29b-41d4-a716-446655440000", path)
    }

    @Test
    fun `note delete path uses uid`() {
        val path = NoteDataMapper.noteDeletePath(fixedUuid)
        assertEquals("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/delete", path)
    }
}
