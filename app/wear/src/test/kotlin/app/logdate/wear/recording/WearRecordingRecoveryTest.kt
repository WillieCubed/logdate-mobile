package app.logdate.wear.recording

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRemuxer
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [WearRecordingRecovery], which turns recordings a dead process left behind into notes.
 */
class WearRecordingRecoveryTest {
    private val directory = Files.createTempDirectory("wear-recovery").toFile()
    private val repository = mockk<JournalNotesRepository>(relaxed = true)
    private val created = mutableListOf<JournalNote.Audio>()
    private val durations = mutableMapOf<String, Long?>()
    private var remuxSucceeds = true
    private val referenced = mutableSetOf<String>()

    private val remuxer =
        AudioRemuxer { source, destination ->
            if (remuxSucceeds) destination.writeBytes(source.readBytes())
            remuxSucceeds
        }
    private val resolver = AudioDurationResolver { path -> durations[path] }
    private val recovery = WearRecordingRecovery(directory, repository, resolver, remuxer)

    init {
        coEvery { repository.notesReferencingMediaPaths(any()) } answers {
            firstArg<Set<String>>().filterTo(mutableSetOf()) { it in referenced }
        }
        coEvery { repository.create(any()) } answers {
            created += firstArg<JournalNote>() as JournalNote.Audio
            firstArg<JournalNote>().uid
        }
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun file(
        name: String,
        bytes: Int = 100,
        modifiedAt: Long = 1_700_000_000_000L,
    ): File =
        File(directory, name).apply {
            writeBytes(ByteArray(bytes) { 1 })
            setLastModified(modifiedAt)
        }

    @Test
    fun `an in-flight recording is wrapped and stored as a note`() =
        runTest {
            val id = Uuid.random()
            val inFlight = file("recording_$id.aac", modifiedAt = 1_700_000_100_000L)
            val final = File(directory, "recording_$id.m4a")
            durations[final.absolutePath] = 4_000L

            val recovered = recovery.recover()

            assertEquals(1, recovered)
            val note = created.single()
            assertEquals(final.absolutePath, note.mediaRef)
            assertEquals(4_000L, note.durationMs)
            assertEquals(Instant.fromEpochMilliseconds(1_700_000_096_000L), note.creationTimestamp)
            assertTrue(final.isFile)
            assertFalse(inFlight.exists())
        }

    @Test
    fun `a recording too short to keep is removed without a note`() =
        runTest {
            val id = Uuid.random()
            file("recording_$id.aac")
            durations[File(directory, "recording_$id.m4a").absolutePath] = 200L

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertEquals(emptyList(), directory.listFiles().orEmpty().toList())
        }

    @Test
    fun `an in-flight recording that cannot be wrapped stays for the next launch`() =
        runTest {
            val id = Uuid.random()
            val inFlight = file("recording_$id.aac", bytes = 50_000)
            remuxSucceeds = false

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(inFlight.exists())
        }

    @Test
    fun `an empty in-flight file is removed`() =
        runTest {
            val id = Uuid.random()
            file("recording_$id.aac", bytes = 0)
            remuxSucceeds = false

            recovery.recover()

            assertEquals(emptyList(), directory.listFiles().orEmpty().toList())
        }

    @Test
    fun `a finished recording with its raw file that no note references is stored as a note`() =
        runTest {
            val id = Uuid.random()
            val final = file("recording_$id.m4a")
            val raw = file("recording_$id.aac", modifiedAt = 1_700_000_050_000L)
            durations[final.absolutePath] = 10_000L

            val recovered = recovery.recover()

            assertEquals(1, recovered)
            assertEquals(final.absolutePath, created.single().mediaRef)
            assertEquals(Instant.fromEpochMilliseconds(1_700_000_040_000L), created.single().creationTimestamp)
            assertTrue(final.isFile)
            assertFalse(raw.exists())
        }

    @Test
    fun `a finished recording without a raw file is never adopted`() =
        runTest {
            val final = file("recording_${Uuid.random()}.m4a")
            durations[final.absolutePath] = 10_000L

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(final.isFile)
        }

    @Test
    fun `a raw file beside a finished recording a note references is removed`() =
        runTest {
            val id = Uuid.random()
            val final = file("recording_$id.m4a")
            val raw = file("recording_$id.aac")
            referenced += final.absolutePath

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(final.isFile)
            assertFalse(raw.exists())
        }

    @Test
    fun `a finished recording whose length cannot be read stays with its raw file`() =
        runTest {
            val id = Uuid.random()
            val final = file("recording_$id.m4a", bytes = 50_000)
            val raw = file("recording_$id.aac")

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(final.isFile)
            assertTrue(raw.isFile)
        }

    @Test
    fun `an empty finished recording is removed with its raw file`() =
        runTest {
            val id = Uuid.random()
            file("recording_$id.m4a", bytes = 0)
            file("recording_$id.aac")

            recovery.recover()

            assertEquals(emptyList(), directory.listFiles().orEmpty().toList())
        }

    @Test
    fun `files that are not recordings are ignored`() =
        runTest {
            val other = file("wear_${Uuid.random()}.m4a")
            durations[other.absolutePath] = 10_000L

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(other.isFile)
            coVerify(exactly = 0) { repository.create(any()) }
        }

    @Test
    fun `a missing directory recovers nothing`() =
        runTest {
            directory.deleteRecursively()

            assertEquals(0, recovery.recover())
        }

    @Test
    fun `a note that fails to save leaves the files for the next launch`() =
        runTest {
            val id = Uuid.random()
            val inFlight = file("recording_$id.aac")
            val final = File(directory, "recording_$id.m4a")
            durations[final.absolutePath] = 4_000L
            coEvery { repository.create(any()) } throws IllegalStateException("database closed")

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(inFlight.exists())
            assertTrue(final.isFile)
        }

    @Test
    fun `a recording newer than the cutoff belongs to a live session and is left alone`() =
        runTest {
            val id = Uuid.random()
            val live = file("recording_$id.aac", modifiedAt = 1_700_000_500_000L)
            val liveFinal = file("recording_${Uuid.random()}.m4a", modifiedAt = 1_700_000_500_000L)
            durations[liveFinal.absolutePath] = 10_000L

            val recovered = recovery.recover(modifiedBefore = 1_700_000_400_000L)

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(live.exists())
            assertTrue(liveFinal.exists())
            assertFalse(File(directory, "recording_$id.m4a").exists())
        }

    @Test
    fun `a recording older than the cutoff is recovered`() =
        runTest {
            val id = Uuid.random()
            file("recording_$id.aac", modifiedAt = 1_700_000_100_000L)
            durations[File(directory, "recording_$id.m4a").absolutePath] = 4_000L

            val recovered = recovery.recover(modifiedBefore = 1_700_000_400_000L)

            assertEquals(1, recovered)
        }

    @Test
    fun `an aac file a note references is audio pulled from the phone and is left alone`() =
        runTest {
            val id = Uuid.random()
            val pulled = file("recording_$id.aac", bytes = 50_000)
            referenced += pulled.absolutePath
            durations[File(directory, "recording_$id.m4a").absolutePath] = 10_000L

            val recovered = recovery.recover()

            assertEquals(0, recovered)
            assertTrue(created.isEmpty())
            assertTrue(pulled.isFile)
            assertFalse(File(directory, "recording_$id.m4a").exists())
        }
}
