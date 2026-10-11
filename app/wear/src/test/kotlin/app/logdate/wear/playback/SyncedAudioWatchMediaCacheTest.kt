package app.logdate.wear.playback

import app.logdate.wear.sync.SyncedAudioCache
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
            val cache = cache(freeBytes = SyncedAudioWatchMediaCache.DEFAULT_RESERVE_BYTES + 1)

            val stored =
                cache.store(noteId, "m4a") { path ->
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
                cache(freeBytes = 10L).store(noteId, "m4a") {
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
            cache.store(noteId, "m4a") { path ->
                File(path).writeText("audio")
                true
            }

            assertTrue(cache.evict(noteId))
            assertNull(cache.cachedFile(noteId))
        }

    private fun cache(freeBytes: Long) = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), freeBytes = { freeBytes })
}
