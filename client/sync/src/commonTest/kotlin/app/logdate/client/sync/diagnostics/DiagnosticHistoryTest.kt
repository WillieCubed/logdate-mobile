package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiagnosticHistoryTest {
    private class Storage : DiagnosticStorage {
        var data: String? = null

        override suspend fun read() = data

        override suspend fun write(value: String) {
            data = value
        }

        override suspend fun clear() {
            data = null
        }
    }

    @Test
    fun `failed user deletion preserves history and returns a sanitized failure`() =
        runTest {
            val storage = Storage()
            val brokenDelete =
                object : DiagnosticStorage by storage {
                    override suspend fun clear(): Unit = error("private-path-marker")
                }
            val history = DiagnosticHistory(brokenDelete, { 1L })
            history.append(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.SUCCEEDED))
            val failure = kotlin.test.assertFailsWith<IllegalStateException> { history.clear() }
            assertEquals("DIAGNOSTIC_HISTORY_CLEAR_FAILED", failure.message)
            assertEquals(null, failure.cause)
            assertEquals(1, history.report().events.size)
            assertEquals(1, DiagnosticHistory(storage, { 2L }).report().events.size)
        }

    @Test
    fun `history survives restart expires and remaps record aliases per export`() =
        runTest {
            val storage = Storage()
            var now = 1000L
            val event =
                SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED, recordAlias = "00000000-0000-4000-8000-000000000001")
            DiagnosticHistory(storage, { now }).append(event)
            val restarted = DiagnosticHistory(storage, { now })
            val first = restarted.report()
            val second = restarted.report()
            assertEquals(1, first.events.size)
            assertNotEquals(first.events.single().recordAlias, event.recordAlias)
            assertNotEquals(first.events.single().recordAlias, second.events.single().recordAlias)
            now += 7 * 24 * 60 * 60 * 1000L + 1
            assertTrue(restarted.report().events.isEmpty())
            assertTrue(DiagnosticHistory(storage, { 1000L }).report().events.isEmpty(), "Expired events must be removed from storage")
        }

    @Test
    fun `corrupt storage and recording failures do not escape into sync`() =
        runTest {
            val broken =
                object : DiagnosticStorage {
                    override suspend fun read(): String = "not-json-private-value"

                    override suspend fun write(value: String): Unit = error("private-path")

                    override suspend fun clear(): Unit = error("private-path")
                }
            val history = DiagnosticHistory(broken, { 1L })
            history.append(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED))
            assertTrue(history.report().droppedEvents > 0)
            kotlin.test.assertFailsWith<IllegalStateException> { history.clear() }
        }

    @Test
    fun `bounded history evicts oldest events and reports dropped counts`() =
        runTest {
            val history = DiagnosticHistory(Storage(), { 1L }, maxBytes = 1024)
            repeat(30) { history.append(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED, attemptCount = it)) }
            val report = history.report()
            assertTrue(report.events.size < 30)
            assertEquals(29, report.events.last().attemptCount)
            assertTrue(report.droppedEvents > 0)
        }

    @Test
    fun `restart classifies an unfinished attempt without inventing a failure cause`() =
        runTest {
            val storage = Storage()
            val id = "00000000-0000-4000-8000-000000000001"
            DiagnosticHistory(storage, { 1L }).append(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED, attemptId = id))
            val report = DiagnosticHistory(storage, { 2L }).report()
            assertEquals(DiagnosticOutcome.INTERRUPTED, report.events.last().outcome)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticReason.NONE, report.events.last().reason)
            assertEquals(id, report.events.last().attemptId)
        }

    @Test
    fun `startup interruption markers respect byte cap across consecutive restarts`() =
        runTest {
            val storage = Storage()
            val history = DiagnosticHistory(storage, { 1L }, maxBytes = 1024)
            repeat(15) {
                history.append(
                    SyncDiagnosticEvent(
                        DiagnosticPhase.FETCH,
                        DiagnosticOutcome.STARTED,
                        attemptId =
                            kotlin.uuid.Uuid
                                .random()
                                .toString(),
                    ),
                )
            }
            val first = DiagnosticHistory(storage, { 2L }, maxBytes = 1024).report()
            assertTrue(first.events.isNotEmpty())
            assertTrue(storage.data!!.encodeToByteArray().size <= 1024)
            val second = DiagnosticHistory(storage, { 3L }, maxBytes = 1024).report()
            assertTrue(second.events.isNotEmpty(), "Restart must not discard valid history")
            assertTrue(storage.data!!.encodeToByteArray().size <= 1024)
            assertTrue(second.droppedEvents >= first.droppedEvents)
        }

    @Test
    fun `normal mode coalesces repeated progress but keeps terminal and new attempt`() =
        runTest {
            val history = DiagnosticHistory(Storage(), { 1L })
            val first = "00000000-0000-4000-8000-000000000001"
            val second = "00000000-0000-4000-8000-000000000002"
            val started = SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED, attemptId = first)

            history.append(started)
            history.append(started.copy(pendingCount = 3))
            history.append(started.copy(outcome = DiagnosticOutcome.SUCCEEDED))
            history.append(started.copy(attemptId = second))

            val events = history.report().events
            assertEquals(3, events.size)
            assertEquals(3, events[0].pendingCount)
            assertEquals(DiagnosticOutcome.SUCCEEDED, events[1].outcome)
            assertEquals(second, events[2].attemptId)
        }

    @Test
    fun `verbose mode retains repeated allowlisted progress`() =
        runTest {
            val history = DiagnosticHistory(Storage(), { 1L })
            val started =
                SyncDiagnosticEvent(
                    DiagnosticPhase.FETCH,
                    DiagnosticOutcome.STARTED,
                    attemptId = "00000000-0000-4000-8000-000000000001",
                )

            history.append(started, verbose = true)
            history.append(started.copy(pendingCount = 3), verbose = true)

            assertEquals(2, history.report().events.size)
        }
}
