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
class BatchRepoBlockReadCacheTest {
    private fun block(value: String): RepoBlock {
        val bytes = value.encodeToByteArray()
        return RepoBlock(Cid.sha256(0x71, bytes), bytes)
    }

    @Test
    fun `one load handles uncached blocks while preserving order duplicates and absent records`() =
        runTest {
            val cache = RepoBlockReadCache()
            val cached = block("cached")
            val first = block("first")
            val second = block("second")
            val absent = block("absent")
            cache.remember(cached, cache.generation())
            var loads = 0
            val records =
                cache
                    .readMany(listOf(second.cid, cached.cid, absent.cid, first.cid, second.cid)) { missing ->
                        loads++
                        assertEquals(setOf(first.cid, second.cid, absent.cid), missing.toSet())
                        assertEquals(3, missing.size)
                        Result.success(mapOf(first.cid to first, second.cid to second))
                    }.getOrThrow()
            assertEquals(listOf(second.cid, cached.cid, null, first.cid, second.cid), records.map { it?.cid })
            assertEquals(1, loads)
            records[0]!!.bytes[0] = 0
            assertContentEquals("second".encodeToByteArray(), records[4]!!.bytes)
            val reused = cache.readMany(listOf(first.cid, second.cid)) { error("Unexpected database load") }.getOrThrow()
            assertContentEquals(first.bytes, reused[0]!!.bytes)
            assertContentEquals(second.bytes, reused[1]!!.bytes)
        }

    @Test
    fun `empty requests avoid storage and failed or absent reads remain retryable`() =
        runTest {
            val cache = RepoBlockReadCache()
            val stored = block("record")
            assertTrue(cache.readMany(emptyList()) { error("Unexpected empty load") }.getOrThrow().isEmpty())
            assertTrue(cache.readMany(listOf(stored.cid)) { Result.failure(IllegalStateException()) }.isFailure)
            assertEquals(listOf(null), cache.readMany(listOf(stored.cid)) { Result.success(emptyMap()) }.getOrThrow())
            assertContentEquals(
                stored.bytes,
                cache.readMany(listOf(stored.cid)) { Result.success(mapOf(stored.cid to stored)) }.getOrThrow()[0]!!.bytes,
            )
        }

    @Test
    fun `batch loads respect the same cache byte budget as individual reads`() =
        runTest {
            val first = block("first")
            val second = block("second")
            val cache = RepoBlockReadCache(maxBytes = second.bytes.size)
            cache
                .readMany(listOf(first.cid, second.cid)) {
                    Result.success(mapOf(first.cid to first, second.cid to second))
                }.getOrThrow()
            assertContentEquals(second.bytes, cache.read(second.cid) { error("Second block was not cached") }.getOrThrow()!!.bytes)
            assertEquals(null, cache.read(first.cid) { Result.success(null) }.getOrThrow())
        }

    @Test
    fun `clearing during a batch load does not repopulate its old bytes`() =
        runTest {
            val cache = RepoBlockReadCache()
            val stored = block("record")
            val completed = CompletableDeferred<Unit>()
            val oldRead =
                async {
                    cache.readMany(listOf(stored.cid)) {
                        completed.await()
                        Result.success(mapOf(stored.cid to stored))
                    }
                }
            runCurrent()
            cache.clear()
            completed.complete(Unit)
            assertContentEquals(stored.bytes, oldRead.await().getOrThrow()[0]!!.bytes)
            assertEquals(listOf(null), cache.readMany(listOf(stored.cid)) { Result.success(emptyMap()) }.getOrThrow())
        }
}
