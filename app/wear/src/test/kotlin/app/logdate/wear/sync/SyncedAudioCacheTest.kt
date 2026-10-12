package app.logdate.wear.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests [SyncedAudioCache], where audio pulled from the phone is stored apart from recordings.
 */
class SyncedAudioCacheTest {
    private val directory = Files.createTempDirectory("synced-audio").toFile()
    private val cache = SyncedAudioCache(directory)
    private val noteId = Uuid.random()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a completed pull becomes the note's audio`() =
        runTest {
            val file =
                cache.pull(noteId, "m4a") { partPath ->
                    File(partPath).writeText("phone audio")
                    true
                }

            assertEquals(File(directory, "$noteId.m4a"), file)
            assertEquals("phone audio", file?.readText())
            assertEquals(listOf("$noteId.m4a"), directory.list().orEmpty().toList())
        }

    @Test
    fun `a pull still in progress is not the note's audio yet`() =
        runTest {
            cache.pull(noteId, "m4a") { partPath ->
                File(partPath).writeText("half")
                assertNull(cache.cachedFile(noteId))
                true
            }

            assertEquals(File(directory, "$noteId.m4a"), cache.cachedFile(noteId))
        }

    @Test
    fun `a failed pull leaves nothing behind`() =
        runTest {
            val file =
                cache.pull(noteId, "m4a") { partPath ->
                    File(partPath).writeText("partial")
                    false
                }

            assertNull(file)
            assertEquals(emptyList(), directory.list().orEmpty().toList())
        }

    @Test
    fun `a pull that wrote nothing is not stored`() =
        runTest {
            assertNull(cache.pull(noteId, "m4a") { true })
            assertNull(cache.cachedFile(noteId))
        }

    @Test
    fun `a cached file belongs to its own note only`() =
        runTest {
            cache.pull(Uuid.random(), "m4a") { partPath ->
                File(partPath).writeText("another note")
                true
            }

            assertNull(cache.cachedFile(noteId))
        }

    @Test
    fun `deleting removes the note's audio`() =
        runTest {
            cache.pull(noteId, "aac") { partPath ->
                File(partPath).writeText("phone audio")
                true
            }

            assertTrue(cache.delete(noteId))
            assertNull(cache.cachedFile(noteId))
            assertFalse(cache.delete(noteId))
        }

    @Test
    fun `a part file a killed app left is cleared by the next pull`() =
        runTest {
            directory.mkdirs()
            val abandoned =
                File(directory, "${Uuid.random()}.m4a.part").apply {
                    writeText("cut off")
                    setLastModified(System.currentTimeMillis() - TWO_HOURS)
                }
            val inProgress = File(directory, "${Uuid.random()}.m4a.part").apply { writeText("arriving") }

            cache.pull(noteId, "m4a") { partPath ->
                File(partPath).writeText("phone audio")
                true
            }

            assertFalse(abandoned.exists())
            assertTrue(inProgress.exists())
        }

    @Test
    fun `deleting during a legacy pull prevents resurrecting the evicted audio`() =
        runTest {
            val result =
                cache.pull(noteId, "m4a") { path ->
                    File(path).writeText("stale")
                    cache.delete(noteId)
                    true
                }
            assertNull(result)
            assertNull(cache.cachedFile(noteId))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `overlapping pulls cannot share a partial file`() =
        runTest {
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val resume = kotlinx.coroutines.CompletableDeferred<Unit>()
            val first =
                async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    cache.pull(noteId, "m4a") { path ->
                        File(path).writeText("first")
                        started.complete(Unit)
                        resume.await()
                        assertEquals("first", File(path).readText())
                        true
                    }
                }
            started.await()
            val second =
                cache.pull(noteId, "m4a") { path ->
                    File(path).writeText("second")
                    true
                }
            assertEquals("second", second?.readText())
            resume.complete(Unit)
            assertEquals("first", first.await()?.readText())
            assertEquals(listOf("$noteId.m4a"), directory.list().orEmpty().toList())
        }

    @Test
    fun `eviction removes every completed format for a note and preserves other notes`() =
        runTest {
            File(directory, "$noteId.m4a").writeText("old format")
            File(directory, "$noteId.aac").writeText("new format")
            val other = File(directory, "${Uuid.random()}.m4a").apply { writeText("other") }
            assertTrue(cache.delete(noteId))
            assertNull(cache.cachedFile(noteId))
            assertEquals(listOf(other.name), directory.list().orEmpty().toList())
        }

    private companion object {
        const val TWO_HOURS = 2 * 60 * 60 * 1000L
    }
}
