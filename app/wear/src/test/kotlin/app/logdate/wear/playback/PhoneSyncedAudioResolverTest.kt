package app.logdate.wear.playback

import android.content.Context
import app.logdate.client.media.audio.AudioRecordingTarget
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.Before
import org.junit.Test

class PhoneSyncedAudioResolverTest {

    private val context = mockk<Context>(relaxed = true)
    private val audioStorage = mockk<AudioStorage>()
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val notesRepository = mockk<SyncableJournalNotesRepository>(relaxed = true)

    @Before
    fun setUp() {
        coEvery { audioStorage.createRecordingTarget(any()) } returns AudioRecordingTarget("/tmp/watch-cache-default.m4a")
        coEvery { dataLayerClient.downloadAudioFromPhone(any(), any()) } returns true
        coEvery { dataLayerClient.isPhoneConnected() } returns true
    }

    private fun buildResolver(
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ) =
        PhoneSyncedAudioResolver(
            context = context,
            audioStorage = audioStorage,
            dataLayerClient = dataLayerClient,
            notesRepository = notesRepository,
            ioDispatcher = dispatcher,
        )

    @Test
    fun `returns existing local media ref without download`() = runTest {
        val noteId = Uuid.random()
        val localFile = File.createTempFile("wear-note-$noteId", ".m4a")
        localFile.writeText("audio")
        val note = audioNote(noteId = noteId, mediaRef = localFile.absolutePath)

        val result = buildResolver().resolvePlayableUri(note)

        assertTrue(result.isSuccess)
        assertEquals(localFile.absolutePath, result.getOrNull())
        coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
        coVerify(exactly = 0) { audioStorage.createRecordingTarget(any()) }
        coVerify(exactly = 0) { dataLayerClient.isPhoneConnected() }

        localFile.delete()
    }

    @Test
    fun `downloads phone media and updates synced note media ref`() = runTest {
        val noteId = Uuid.random()
        val note = audioNote(noteId = noteId, mediaRef = "content://phone/audio/$noteId")
        val targetPath = "/tmp/watch-cache-$noteId.m4a"

        coEvery { audioStorage.createRecordingTarget("m4a") } returns AudioRecordingTarget(targetPath)
        coEvery { dataLayerClient.downloadAudioFromPhone(noteId, targetPath) } returns true

        val result = buildResolver().resolvePlayableUri(note)

        assertTrue(result.isSuccess)
        assertEquals(targetPath, result.getOrNull())
        coVerify { dataLayerClient.isPhoneConnected() }
        coVerify { dataLayerClient.downloadAudioFromPhone(noteId, targetPath) }
        coVerify { notesRepository.updateMediaRef(noteId, targetPath) }
    }

    @Test
    fun `fails when phone media download fails`() = runTest {
        val noteId = Uuid.random()
        val note = audioNote(noteId = noteId, mediaRef = "content://phone/audio/$noteId")
        val targetPath = "/tmp/watch-cache-$noteId.m4a"

        coEvery { audioStorage.createRecordingTarget("m4a") } returns AudioRecordingTarget(targetPath)
        coEvery { dataLayerClient.downloadAudioFromPhone(noteId, targetPath) } returns false

        val result = buildResolver().resolvePlayableUri(note)

        assertTrue(result.isFailure)
        coVerify { dataLayerClient.isPhoneConnected() }
        coVerify { dataLayerClient.downloadAudioFromPhone(noteId, targetPath) }
        coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
    }

    @Test
    fun `fails when phone is unreachable`() = runTest {
        val noteId = Uuid.random()
        val note = audioNote(noteId = noteId, mediaRef = "content://phone/audio/$noteId")
        val targetPath = "/tmp/watch-cache-$noteId.m4a"

        coEvery { audioStorage.createRecordingTarget("m4a") } returns AudioRecordingTarget(targetPath)
        coEvery { dataLayerClient.isPhoneConnected() } returns false

        val result = buildResolver().resolvePlayableUri(note)

        assertTrue(result.isFailure)
        coVerify { dataLayerClient.isPhoneConnected() }
        coVerify(exactly = 0) { dataLayerClient.downloadAudioFromPhone(any(), any()) }
        coVerify(exactly = 0) { notesRepository.updateMediaRef(any(), any()) }
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
