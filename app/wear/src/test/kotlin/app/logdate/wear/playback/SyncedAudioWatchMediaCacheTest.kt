package app.logdate.wear.playback

import app.logdate.wear.sync.SyncedAudioCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SyncedAudioWatchMediaCacheTest {
    private val directory = Files.createTempDirectory("watch-media").toFile()
    private val noteId = Uuid.random()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `audio is stored while enough space is free`() =
        runTest {
            val cache = cache(freeBytes = SyncedAudioWatchMediaCache.DEFAULT_RESERVE_BYTES + 1024)

            val stored =
                cache.store(noteId, "m4a") { path, _ ->
                    File(path).writeText("audio")
                    true
                }

            assertEquals(File(directory, "$noteId.m4a"), stored)
            assertEquals(stored, cache.cachedFile(noteId))
        }

    @Test
    fun `nothing is fetched when the watch is short of space`() =
        runTest {
            var asked = false

            assertFailsWith<NoRoomForAudioException> {
                cache(freeBytes = 10L).store(noteId, "m4a") { _, _ ->
                    asked = true
                    true
                }
            }

            assertFalse(asked)
            assertNull(cache(freeBytes = 10L).cachedFile(noteId))
        }

    @Test
    fun `evicting removes the stored audio`() =
        runTest {
            val cache = cache(freeBytes = Long.MAX_VALUE)
            cache.store(noteId, "m4a") { path, _ ->
                File(path).writeText("audio")
                true
            }

            assertTrue(cache.evict(noteId))
            assertNull(cache.cachedFile(noteId))
        }

    @Test
    fun `download stops before crossing the recording reserve and removes its part`() =
        runTest {
            val cache = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), freeBytes = { 210L }, reserveBytes = 200L)
            assertFailsWith<NoRoomForAudioException> {
                cache.store(noteId, "m4a") { path, beforeWrite ->
                    File(path).outputStream().use { output ->
                        beforeWrite(9)
                        output.write(ByteArray(9))
                        beforeWrite(2)
                        output.write(ByteArray(2))
                    }
                    true
                }
            }
            assertNull(cache.cachedFile(noteId))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `other disk use during download also protects the reserve`() =
        runTest {
            var free = 220L
            val cache = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), freeBytes = { free }, reserveBytes = 200L)
            assertFailsWith<NoRoomForAudioException> {
                cache.store(noteId, "m4a") { _, beforeWrite ->
                    free = 202L
                    beforeWrite(3)
                    true
                }
            }
        }

    @Test
    fun `downloads serialize their available disk budget`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val cache = cache(Long.MAX_VALUE)
            val first =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.store(noteId, "m4a") { path, _ ->
                        gate.await()
                        File(path).writeText("one")
                        true
                    }
                }
            var secondStarted = false
            val second =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.store(Uuid.random(), "m4a") { path, _ ->
                        secondStarted = true
                        File(path).writeText("two")
                        true
                    }
                }
            assertFalse(secondStarted)
            gate.complete(Unit)
            first.await()
            second.await()
        }

    @Test
    fun `cancelled downloads leave no partial audio`() =
        runTest {
            val cache = cache(Long.MAX_VALUE)
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                cache.store(noteId, "m4a") { path, _ ->
                    File(path).writeText("partial")
                    throw kotlinx.coroutines.CancellationException("cancelled")
                }
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `eviction invalidates a download waiting for the disk budget`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val cache = cache(Long.MAX_VALUE)
            val first =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.store(Uuid.random(), "m4a") { path, _ ->
                        gate.await()
                        File(path).writeText("first")
                        true
                    }
                }
            var fetched = false
            val queued =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.store(noteId, "m4a") { path, _ ->
                        fetched = true
                        File(path).writeText("evicted")
                        true
                    }
                }
            cache.evict(noteId)
            gate.complete(Unit)
            first.await()
            assertNull(queued.await())
            assertFalse(fetched)
            assertNull(cache.cachedFile(noteId))
        }

    @Test
    fun `eviction after the last chunk prevents publication and allows a fresh download`() =
        runTest {
            val cache = cache(Long.MAX_VALUE)
            val stored =
                cache.store(noteId, "m4a") { path, beforeWrite ->
                    beforeWrite(5)
                    File(path).writeText("stale")
                    cache.evict(noteId)
                    true
                }
            assertNull(stored)
            assertNull(cache.cachedFile(noteId))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            val fresh =
                cache.store(noteId, "m4a") { path, beforeWrite ->
                    beforeWrite(5)
                    File(path).writeText("fresh")
                    true
                }
            assertEquals("fresh", fresh?.readText())
        }

    @Test
    fun `an evicted transfer cannot write another chunk`() =
        runTest {
            val cache = cache(Long.MAX_VALUE)
            assertFailsWith<java.io.IOException> {
                cache.store(noteId, "m4a") { path, beforeWrite ->
                    File(path).writeText("first")
                    cache.evict(noteId)
                    beforeWrite(4)
                    File(path).appendText("late")
                    true
                }
            }
            assertNull(cache.cachedFile(noteId))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    private fun cache(freeBytes: Long) = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), freeBytes = { freeBytes })
}
