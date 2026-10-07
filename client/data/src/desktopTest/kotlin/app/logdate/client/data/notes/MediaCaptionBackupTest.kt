package app.logdate.client.data.notes

import app.logdate.client.data.fakes.FakeAudioNoteDao
import app.logdate.client.data.fakes.FakeImageNoteDao
import app.logdate.client.data.fakes.FakeJournalContentDao
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeMediaCaptionDao
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.fakes.FakeTextNoteDao
import app.logdate.client.data.fakes.FakeVideoNoteDao
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MediaCaptionBackupTest {
    @Test
    fun `legacy file backup preserves every media caption in a fresh repository`() =
        runTest {
            val recordedAt = Instant.parse("2026-01-02T03:04:05Z")
            val image =
                JournalNote.Image(
                    creationTimestamp = recordedAt,
                    lastUpdated = recordedAt,
                    mediaRef = "/media/station.jpg",
                    caption = "The station platform",
                )
            val audio =
                JournalNote.Audio(
                    creationTimestamp = recordedAt,
                    lastUpdated = recordedAt,
                    mediaRef = "/media/train.m4a",
                    caption = "The train arriving",
                )
            val video =
                JournalNote.Video(
                    creationTimestamp = recordedAt,
                    lastUpdated = recordedAt,
                    mediaRef = "/media/trip.mp4",
                    caption = "Leaving the station",
                )
            val source = repository()
            listOf(image, audio, video).forEach { source.create(it) }
            val directory = createTempDirectory("logdate-caption-backup-").toFile()

            try {
                val file = directory.resolve("backup.json")
                source.exportContentToFile(
                    destination = file.path,
                    overwrite = false,
                    startTimestamp = Instant.parse("2026-01-01T00:00:00Z"),
                    endTimestamp = Instant.parse("2026-01-03T00:00:00Z"),
                )
                val backup = Json.decodeFromString<JournalContentBackup>(file.readText())
                val restored = repository()
                backup.notes.forEach { restored.createFromSync(it) }
                val notes = restored.allNotesObserved.first().associateBy { it.uid }

                assertEquals("The station platform", (notes.getValue(image.uid) as JournalNote.Image).caption)
                assertEquals("The train arriving", (notes.getValue(audio.uid) as JournalNote.Audio).caption)
                assertEquals("Leaving the station", (notes.getValue(video.uid) as JournalNote.Video).caption)
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun repository() =
        OfflineFirstJournalNotesRepository(
            textNoteDao = FakeTextNoteDao(),
            imageNoteDao = FakeImageNoteDao(),
            audioNoteDao = FakeAudioNoteDao(),
            videoNoteDao = FakeVideoNoteDao(),
            journalContentDao = FakeJournalContentDao(),
            journalRepository = FakeJournalRepository(),
            mediaCaptionDao = FakeMediaCaptionDao(),
            syncMetadataService = FakeSyncMetadataService(),
        )
}
