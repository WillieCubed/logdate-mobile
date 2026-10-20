package app.logdate.client.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncTransactionCompletionTest {
    @Test
    fun `cleanup waits for the outermost commit and retains return values`() =
        runTest {
            val manager = CompletionTransaction()
            val result =
                manager.withTransaction {
                    manager.afterCommit { manager.events += "cleanup" }
                    manager.withTransaction {
                        manager.afterCommit { manager.events += "nested-cleanup" }
                    }
                    assertFalse(manager.events.any { it.contains("cleanup") })
                    42
                }
            assertEquals(42, result)
            assertEquals(listOf("begin", "begin", "commit", "commit", "cleanup", "nested-cleanup"), manager.events)
        }

    @Test
    fun `rollback discards cleanup registered by nested work`() =
        runTest {
            val manager = CompletionTransaction()
            assertFailsWith<IllegalStateException> {
                manager.withTransaction {
                    manager.withTransaction { manager.afterCommit { manager.events += "cleanup" } }
                    error("Rollback fixture")
                }
            }
            assertFalse("cleanup" in manager.events)
            manager.afterCommit { manager.events += "outside" }
            assertEquals("outside", manager.events.last())
        }

    @Test
    fun `caught inner rollback does not acknowledge its cleanup when outer work commits`() =
        runTest {
            val manager = CompletionTransaction()
            manager.withTransaction {
                manager.afterCommit { manager.events += "outer-cleanup" }
                assertFailsWith<IllegalStateException> {
                    manager.withTransaction {
                        manager.afterCommit { manager.events += "rolled-back-cleanup" }
                        error("Nested rollback fixture")
                    }
                }
            }
            assertEquals(listOf("begin", "begin", "rollback", "commit", "outer-cleanup"), manager.events)
        }

    @Test
    fun `a failed database commit leaves cleanup unacknowledged`() =
        runTest {
            val manager = CompletionTransaction(failCommit = true)
            assertFailsWith<IllegalStateException> {
                manager.withTransaction { manager.afterCommit { manager.events += "cleanup" } }
            }
            assertEquals(listOf("begin", "rollback"), manager.events)
        }

    @Test
    fun `cancellation before commit preserves pending cleanup`() =
        runTest {
            val manager = CompletionTransaction()
            assertFailsWith<CancellationException> {
                manager.withTransaction {
                    manager.afterCommit { manager.events += "cleanup" }
                    throw CancellationException("Cancelled fixture")
                }
            }
            assertFalse("cleanup" in manager.events)
        }

    @Test
    fun `a different nested database does not release outer cleanup early`() =
        runTest {
            val outer = CompletionTransaction()
            val inner = CompletionTransaction()
            outer.withTransaction {
                inner.withTransaction {
                    outer.afterCommit { outer.events += "outer-cleanup" }
                    inner.afterCommit { inner.events += "inner-cleanup" }
                }
                assertFalse("outer-cleanup" in outer.events)
                assertEquals(listOf("begin", "commit", "inner-cleanup"), inner.events)
            }
            assertEquals(listOf("begin", "commit", "outer-cleanup"), outer.events)
        }

    @Test
    fun `child coroutine cleanup is deferred until its transaction commits`() =
        runTest {
            val manager = CompletionTransaction()
            manager.withTransaction {
                coroutineScope { launch { manager.afterCommit { manager.events += "child-cleanup" } }.join() }
                assertFalse("child-cleanup" in manager.events)
            }
            assertEquals(listOf("begin", "commit", "child-cleanup"), manager.events)
        }

    @Test
    fun `one failed cleanup does not skip other committed work`() =
        runTest {
            val manager = CompletionTransaction()
            manager.withTransaction {
                manager.afterCommit { error("Private storage failure fixture") }
                manager.afterCommit { manager.events += "second-cleanup" }
            }
            assertEquals(listOf("begin", "commit", "second-cleanup"), manager.events)
        }

    @Test
    fun `cleanup outside transactions runs immediately`() =
        runTest {
            val manager = CompletionTransaction()
            var called = false
            manager.afterCommit { called = true }
            assertTrue(called)
        }
}

private class CompletionTransaction(
    private val failCommit: Boolean = false,
) : SyncTransactionManager {
    val events = mutableListOf<String>()

    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        withSyncTransactionCompletion(this, transaction = { operation ->
            events += "begin"
            try {
                val result = operation()
                check(!failCommit) { "Database commit failure fixture" }
                events += "commit"
                result
            } catch (failure: Throwable) {
                events += "rollback"
                throw failure
            }
        }, block = block)
}
