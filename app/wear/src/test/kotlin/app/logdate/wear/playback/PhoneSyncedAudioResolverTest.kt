package app.logdate.wear.playback

import android.content.Context
import app.logdate.client.media.storage.AndroidMediaDirectories
import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [PhoneSyncedAudioResolver], which finds a playable file for an audio note and pulls one from
 * the phone into the synced audio cache when the watch has none.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PhoneSyncedAudioResolverTest {
    private val context = mockk<Context>(relaxed = true)
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val notesRepository = mockk<SyncableJournalNotesRepository>(relaxed = true)
    private val filesDir = Files.createTempDirectory("wear-files").toFile()
    private val cacheDirectory = File(filesDir, SyncedAudioCache.DIRECTORY_NAME).apply { mkdirs() }
    private val cache = SyncedAudioCache(cacheDirectory)
    private var freeBytes = Long.MAX_VALUE
    private val mediaFiles = MediaFileResolver(AndroidMediaDirectories(filesDir))

    @Before
    fun setUp() {
        coEvery { dataLayerClient.isPhoneConnected() } returns true
        coEvery { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) } answers {
            File(secondArg<String>()).writeText("phone audio")
            true
        }
    }

    @Test
    fun `a cancelled pull propagates cancellation instead of failing a newer playback`() =
        runTest {
            coEvery { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) } throws
                kotlinx.coroutines.CancellationException("superseded")
            val at = Instant.fromEpochMilliseconds(1_760_000_000_000L)
            val note = JournalNote.Audio(mediaRef = "/phone/missing.m4a", durationMs = 1000L, creationTimestamp = at, lastUpdated = at)
            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { buildResolver().resolvePlayableUri(note) }
        }

    @After
    fun cleanUp() {
        filesDir.deleteRecursively()
    }

    private fun buildResolver(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
        PhoneSyncedAudioResolver(
            context = context,
            mediaCache = SyncedAudioWatchMediaCache(cache, freeBytes = { freeBytes }),
            dataLayerClient = dataLayerClient,
            notesRepository = notesRepository,
            ioDispatcher = dispatcher,
            mediaFiles = mediaFiles,
        )

    @Test
    fun `plays a media reference whose recording is on the watch without download`() =
        runTest {
            val noteId = Uuid.random()
            File(filesDir, "audio_notes").mkdirs()
            File(filesDir, "audio_notes/recording $noteId.m4a").writeText("audio")
            val reference = "logdate-media://recordings/recording%20$noteId.m4a"

            val result = buildResolver().resolvePlayableUri(audioNote(noteId = noteId, mediaRef = reference))

            assertEquals(reference, result.getOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
        }

    @Test
    fun `returns existing local media ref without download`() =
        runTest {
            val noteId = Uuid.random()
            val localFile = File.createTempFile("wear-note-$noteId", ".m4a").apply { writeText("audio") }
            try {
                val result = buildResolver().resolvePlayableUri(audioNote(noteId, localFile.absolutePath))

                assertEquals(localFile.absolutePath, result.getOrNull())
                coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
                coVerify(exactly = 0) { dataLayerClient.isPhoneConnected() }
            } finally {
                localFile.delete()
            }
        }

    @Test
    fun `phone audio is pulled into the cache under the note's id`() =
        runTest {
            val noteId = Uuid.random()

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId.m4a"))

            val expected = File(cacheDirectory, "$noteId.m4a").absolutePath
            assertEquals(expected, result.getOrNull())
            assertEquals("phone audio", File(expected).readText())
            coVerify { dataLayerClient.isPhoneConnected() }
            coVerify { notesRepository.updateMediaRef(noteId, expected) }
        }

    @Test
    fun `pulled phone audio stays outside the recordings collection and plays again from its stored path`() =
        runTest {
            val noteId = Uuid.random()

            val pulled = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId.m4a")).getOrThrow()

            assertEquals(null, mediaFiles.refFor(pulled))
            assertEquals(pulled, mediaFiles.storedReference(pulled))
            assertTrue(File(mediaFiles.directory(MediaCollection.Recordings)).listFiles().orEmpty().isEmpty())
            val replayed = buildResolver().resolvePlayableUri(audioNote(noteId, mediaFiles.storedReference(pulled)))
            assertEquals(pulled, replayed.getOrNull())
            coVerify(exactly = 1) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
        }

    @Test
    fun `audio pulled earlier is reused without asking the phone again`() =
        runTest {
            val noteId = Uuid.random()
            val earlier = File(cacheDirectory, "$noteId.m4a").apply { writeText("earlier pull") }

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId.m4a"))

            assertEquals(earlier.absolutePath, result.getOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
            coVerify { notesRepository.updateMediaRef(noteId, earlier.absolutePath) }
        }

    @Test
    fun `an extension that is not a plain file extension is replaced`() =
        runTest {
            val noteId = Uuid.random()

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/a.b/c"))

            assertEquals(File(cacheDirectory, "$noteId.m4a").absolutePath, result.getOrNull())
        }

    @Test
    fun `a remote url is fetched from the phone instead of streamed`() =
        runTest {
            val noteId = Uuid.random()

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "https://cloud.example/media/$noteId"))

            assertEquals(File(cacheDirectory, "$noteId.m4a").absolutePath, result.getOrNull())
            coVerify { dataLayerClient.downloadAudioFromPhone(noteId, any(), any()) }
        }

    @Test
    fun `a failed pull fails and leaves nothing in the cache`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) } answers {
                File(secondArg<String>()).writeText("partial")
                false
            }

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertTrue(result.isFailure)
            assertEquals(emptyList(), cacheDirectory.listFiles().orEmpty().toList())
            coVerify { dataLayerClient.downloadAudioFromPhone(noteId, any(), any()) }
            coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
        }

    @Test
    fun `a remote url with no phone in range fails instead of streaming`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.isPhoneConnected() } returns false

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "https://cloud.example/media/$noteId"))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
        }

    @Test
    fun `fails as not on the watch when the phone is unreachable`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { dataLayerClient.isPhoneConnected() } returns false

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertIs<AudioNotOnWatchException>(result.exceptionOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
            coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
        }

    @Test
    fun `fails as out of room without asking the phone when the watch is full`() =
        runTest {
            val noteId = Uuid.random()
            freeBytes = 1_024L

            val result = buildResolver().resolvePlayableUri(audioNote(noteId, "content://phone/audio/$noteId"))

            assertIs<NoRoomForAudioException>(result.exceptionOrNull())
            coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any(), any()) }
        }

    private fun audioNote(
        noteId: Uuid,
        mediaRef: String,
    ) = JournalNote.Audio(
        uid = noteId,
        creationTimestamp = Instant.fromEpochMilliseconds(1_710_000_000_000),
        lastUpdated = Instant.fromEpochMilliseconds(1_710_000_000_000),
        mediaRef = mediaRef,
        durationMs = 4_200,
    )
}
