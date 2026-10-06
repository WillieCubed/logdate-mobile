package app.logdate.server.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoBlock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RepoBlockReadCacheTest {
    private fun block(value: String): RepoBlock {
        val bytes = value.encodeToByteArray()
        return RepoBlock(Cid.sha256(0x71, bytes), bytes)
    }

    @Test
    fun `repeated reads reuse immutable bytes without retaining caller mutations`() =
        runTest {
            val cache = RepoBlockReadCache()
            val stored = block("encrypted record")
            var loads = 0

            suspend fun read() =
                cache
                    .read(stored.cid) {
                        loads++
                        Result.success(stored)
                    }.getOrThrow()!!
            read().bytes[0] = 0
            assertContentEquals("encrypted record".encodeToByteArray(), read().bytes)
            assertEquals(1, loads)
        }

    @Test
    fun `successful writes are immediately readable while old block identities stay independent`() =
        runTest {
            val cache = RepoBlockReadCache()
            val first = block("first")
            val second = block("second")
            cache.remember(first, cache.generation())
            cache.remember(second, cache.generation())
            for (stored in listOf(first, second)) {
                val actual = cache.read(stored.cid) { error("A persisted block should already be cached") }.getOrThrow()!!
                assertContentEquals(stored.bytes, actual.bytes)
            }
        }

    @Test
    fun `byte budget evicts old blocks and never retains oversized blocks`() =
        runTest {
            val cache = RepoBlockReadCache(6)
            val first = block("one")
            val second = block("two")
            val third = block("new")
            cache.remember(first, cache.generation())
            cache.remember(second, cache.generation())
            cache.read(first.cid) { error("Unexpected miss") }.getOrThrow()
            cache.remember(third, cache.generation())
            var misses = 0
            cache
                .read(second.cid) {
                    misses++
                    Result.success(second)
                }.getOrThrow()
            val large = block("too large for cache")
            repeat(2) {
                cache
                    .read(large.cid) {
                        misses++
                        Result.success(large)
                    }.getOrThrow()
            }
            assertEquals(3, misses)
        }

    @Test
    fun `failed and missing reads are not cached`() =
        runTest {
            val cache = RepoBlockReadCache()
            val stored = block("record")
            assertTrue(cache.read(stored.cid) { Result.failure(IllegalStateException()) }.isFailure)
            assertEquals(null, cache.read(stored.cid) { Result.success(null) }.getOrThrow())
            assertContentEquals(stored.bytes, cache.read(stored.cid) { Result.success(stored) }.getOrThrow()!!.bytes)
        }

    @Test
    fun `clearing does not let an earlier read or write repopulate deleted bytes`() =
        runTest {
            val cache = RepoBlockReadCache()
            val stored = block("record")
            val completed = CompletableDeferred<Unit>()
            val generation = cache.generation()
            val oldRead =
                async {
                    cache.read(stored.cid) {
                        completed.await()
                        Result.success(stored)
                    }
                }
            runCurrent()
            cache.clear()
            completed.complete(Unit)
            oldRead.await().getOrThrow()
            cache.remember(stored, generation)
            assertEquals(null, cache.read(stored.cid) { Result.success(null) }.getOrThrow())
        }
}
