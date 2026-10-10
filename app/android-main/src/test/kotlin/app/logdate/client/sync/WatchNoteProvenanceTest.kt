package app.logdate.client.sync

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Notes that reach the phone from the watch are recorded as captured on the watch. Watch builds that
 * predate the capture device send none, so the phone records it; a device the watch named stays.
 */
class WatchNoteProvenanceTest {
    private val mapper = NoteDataMapper()
    private val store = InMemoryWatchAudioStore()
    private val stored = mutableMapOf<Uuid, JournalNote>()
    private val notesRepository =
        mockk<SyncableJournalNotesRepository>(relaxed = true).also { repository ->
            coEvery { repository.getNoteById(any()) } answers { stored[firstArg()] }
            coEvery { repository.create(any<JournalNote>()) } answers {
                val note = firstArg<JournalNote>()
                stored[note.uid] = note
                note.uid
            }
        }
    private val ingestor =
        WatchNoteIngestor(
            notesRepository = notesRepository,
            audioStore = store,
            acknowledger = RecordingAcknowledger(),
            notifier = {},
            noteDataMapper = mapper,
        )
    private val at = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun `a text note from an older watch is stored as captured on the watch`() =
        runTest {
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "From the watch")

            ingestor.onNoteMetadata(mapper.toDataMap(note))

            assertEquals(CaptureDevice.WATCH, stored.getValue(note.uid).capturedOn)
        }

    @Test
    fun `an audio note from an older watch is stored as captured on the watch`() =
        runTest {
            val note = JournalNote.Audio(mediaRef = "/watch/voice.m4a", durationMs = 4_000, creationTimestamp = at, lastUpdated = at)

            ingestor.onNoteMetadata(mapper.toDataMap(note))
            ingestor.onAudioBytes(note.uid, ByteArrayInputStream("m4a".encodeToByteArray()))

            assertEquals(CaptureDevice.WATCH, stored.getValue(note.uid).capturedOn)
        }

    @Test
    fun `a device the watch named stays as it is`() =
        runTest {
            val note =
                JournalNote.Text(
                    creationTimestamp = at,
                    lastUpdated = at,
                    content = "Written on the phone",
                    capturedOn = CaptureDevice.PHONE,
                )

            ingestor.onNoteMetadata(mapper.toDataMap(note))

            assertEquals(CaptureDevice.PHONE, stored.getValue(note.uid).capturedOn)
        }
}
