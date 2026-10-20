package app.logdate.client.domain.assistants

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AssistantReadAccessGuardTest {
    private val fixture = AssistantAuthorityFixture

    @Test
    fun `temporary lock pause and account switch invalidate a read even after authority returns`() =
        runTest {
            val interruptions =
                listOf(
                    fixture.device.copy(unlocked = false),
                    fixture.device.copy(paused = true),
                    fixture.device.copy(accountId = fixture.otherId),
                )
            interruptions.forEach { interrupted ->
                var current = fixture.snapshot
                val started = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                val guard = AssistantReadAccessGuard(snapshot = { current }, now = { fixture.now })
                val reading =
                    async {
                        guard.read(fixture.request) {
                            started.complete(Unit)
                            finish.await()
                            "must stay cancelled after resuming"
                        }
                    }
                started.await()
                current = current.copy(device = interrupted.copy(accessEpoch = 1))
                current = current.copy(device = fixture.device.copy(accessEpoch = current.device.accessEpoch))
                finish.complete(Unit)
                assertIs<AssistantReadResult.Denied>(reading.await())

                assertIs<AssistantReadResult.Released<String>>(guard.read(fixture.request) { "a new read may proceed" })
            }
        }

    @Test
    fun `permitted read releases exact text after two live checks`() =
        runTest {
            var checks = 0
            val guard =
                AssistantReadAccessGuard(
                    snapshot = { id ->
                        assertEquals(fixture.grant.id, id)
                        checks++
                        fixture.snapshot
                    },
                    now = { fixture.now },
                )
            val result = guard.read(fixture.request) { "The garden felt peaceful." }

            assertEquals("The garden felt peaceful.", assertIs<AssistantReadResult.Released<String>>(result).value)
            assertEquals(2, checks)
            assertFalse(result.toString().contains("garden"))
        }

    @Test
    fun `initial denial never computes content`() =
        runTest {
            var ran = false
            val guard =
                AssistantReadAccessGuard(
                    snapshot = { fixture.snapshot.copy(device = fixture.device.copy(unlocked = false)) },
                    now = { fixture.now },
                )
            assertIs<AssistantReadResult.Denied>(
                guard.read(fixture.request) {
                    ran = true
                    "private"
                },
            )
            assertFalse(ran)
        }

    @Test
    fun `lock revoke account switch revision and scope changes while suspended suppress result`() =
        runTest {
            val changedSnapshots =
                listOf(
                    fixture.snapshot.copy(device = fixture.device.copy(unlocked = false)),
                    fixture.snapshot.copy(device = fixture.device.copy(enabled = false)),
                    fixture.snapshot.copy(device = fixture.device.copy(paused = true)),
                    fixture.snapshot.copy(device = fixture.device.copy(accountId = fixture.otherId)),
                    fixture.snapshot.copy(grant = fixture.grant.copy(revokedAt = fixture.now)),
                    fixture.snapshot.copy(grant = fixture.grant.copy(revision = 2)),
                    fixture.snapshot.copy(
                        grant = fixture.grant.copy(contentScope = fixture.grant.contentScope.copy(journalIds = emptySet())),
                    ),
                    fixture.snapshot.copy(grant = null),
                )
            changedSnapshots.forEachIndexed { index, changed ->
                var current = fixture.snapshot
                val started = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                val guard = AssistantReadAccessGuard(snapshot = { current }, now = { fixture.now })
                val reading =
                    async {
                        guard.read(fixture.request) {
                            started.complete(Unit)
                            finish.await()
                            "never release this source"
                        }
                    }
                started.await()
                current = changed
                finish.complete(Unit)

                val result = reading.await()
                assertIs<AssistantReadResult.Denied>(result, "change $index")
                assertFalse(result.toString().contains("source"))
            }
        }

    @Test
    fun `security time is reacquired before release`() =
        runTest {
            var time = fixture.now
            val guard = AssistantReadAccessGuard(snapshot = { fixture.snapshot }, now = { time })
            val result =
                guard.read(fixture.request) {
                    time = fixture.request.expiresAt
                    "must not escape"
                }

            assertIs<AssistantReadResult.Denied>(result)
        }

    @Test
    fun `cancellation propagates without success`() =
        runTest {
            val guard = AssistantReadAccessGuard(snapshot = { fixture.snapshot }, now = { fixture.now })
            assertFailsWith<CancellationException> {
                guard.read(fixture.request) { throw CancellationException("cancelled") }
            }
        }
}
