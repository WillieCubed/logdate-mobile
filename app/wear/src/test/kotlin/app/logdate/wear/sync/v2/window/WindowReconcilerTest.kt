package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WindowReconcilerTest {
    private val f = WindowFixture()
    private var response = f.response()
    private var records = listOf(f.item())
    private var supported = true
    private var answer: (() -> Result<ByteArray>)? = null
    private var read: () -> Unit = {}
    private val requested = mutableListOf<String>()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val reconciler =
        WindowReconciler(
            f.peers,
            { supported },
            { peer, path, bytes ->
                assertEquals(f.peer, peer)
                assertEquals(
                    Decoded.Supported(WindowResyncRequest(context = peer)),
                    WearJson.decode(bytes, WearProtocol.WINDOW_SCHEMA, WindowResyncRequest.serializer()),
                )
                requested += path
                answer?.invoke() ?: Result.success(WearJson.encodeToBytes(WindowResyncResponse.serializer(), response))
            },
            WindowItemSource { peer ->
                assertEquals(f.peer, peer)
                read()
                records
            },
            f.applier,
            changes,
        )

    @Test fun `a resync requests the exact linked phone and applies the complete window`() =
        runTest {
            assertEquals(WindowApplied(1, 0), reconciler.resync())
            assertEquals(listOf(WearPaths.WINDOW_RESYNC), requested)
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `unsupported and unlinked watches make no request`() =
        runTest {
            supported = false
            assertNull(reconciler.resync())
            supported = true
            f.current = null
            assertNull(reconciler.resync())
            assertTrue(requested.isEmpty())
        }

    @Test fun `a failed request or unreadable response does not prune`() =
        runTest {
            f.seed()
            answer = { Result.failure(IllegalStateException("phone unavailable")) }
            assertNull(reconciler.resync())
            answer = { Result.success("broken".encodeToByteArray()) }
            assertNull(reconciler.resync())
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `a failed data query never authorizes removals`() =
        runTest {
            f.seed()
            response = f.response(emptyList(), 2)
            read = { error("Data Layer unavailable") }
            assertNull(reconciler.resync())
            assertEquals(f.note, f.stored[f.note.uid])
        }

    @Test fun `an old exchange response cannot establish ownership`() =
        runTest {
            response = response.copy(context = f.peer.copy(phoneNode = "old"))
            assertNull(reconciler.resync())
            assertTrue(f.stored.isEmpty())
        }

    @Test fun `a link change during the request or data query stops reconciliation`() =
        runTest {
            answer = {
                f.current = null
                Result.success(WearJson.encodeToBytes(WindowResyncResponse.serializer(), response))
            }
            assertNull(reconciler.resync())
            f.current = f.peer
            answer = null
            read = { f.current = null }
            assertNull(reconciler.resync())
            assertTrue(f.stored.isEmpty())
        }

    @Test fun `a complete snapshot removes only previously projected records`() =
        runTest {
            assertNotNull(reconciler.resync())
            response = f.response(emptyList(), 2)
            records = emptyList()
            assertEquals(WindowApplied(0, 1), reconciler.resync())
            assertTrue(f.stored.isEmpty())
        }

    @Test fun `cancellation is not converted into a failed sync`() =
        runTest {
            answer = { throw CancellationException("stopped") }
            assertFailsWith<CancellationException> { reconciler.resync() }
        }

    @Test fun `startup and link changes reconcile after debounce without a reachable peer fallback`() =
        runTest {
            reconciler.start(backgroundScope)
            runCurrent()
            advanceTimeBy(2_001)
            runCurrent()
            assertEquals(f.note, f.stored[f.note.uid])
            f.current = null
            changes.emit(Unit)
            advanceTimeBy(2_001)
            runCurrent()
            assertEquals(1, requested.size)
            f.current = f.peer
            response = f.response(emptyList(), 2)
            records = emptyList()
            changes.emit(Unit)
            advanceTimeBy(2_001)
            runCurrent()
            assertEquals(2, requested.size)
            assertTrue(f.stored.isEmpty())
        }
}
