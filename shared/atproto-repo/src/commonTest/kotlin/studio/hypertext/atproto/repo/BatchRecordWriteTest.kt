package studio.hypertext.atproto.repo

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Writing a record reads the whole tree, rebuilds it and writes a new head, so writing a list one
 * record at a time costs that whole cycle per record. The endpoints that accept a list were doing
 * exactly that, which is why sending twenty links cost twenty commits rather than one.
 */
class BatchRecordWriteTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.association")

    private fun recordId(key: String) = RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require(key))

    @Test
    fun `writing a batch of records costs one commit rather than one per record`() =
        runSuspend {
            val blockStore = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(blockStore)
            val records =
                (1..5).map { i ->
                    BatchRecordWrite(recordId("link-$i"), buildJsonObject { put("index", i) })
                }

            val results = engine.putRecords(records).getOrThrow()

            assertEquals(5, results.size, "every record should report its own result")
            assertEquals(
                1,
                engine.listCommits(repo).getOrThrow().size,
                "a batch is one write, so it should leave one commit behind",
            )
            records.forEach { record ->
                assertNotNull(
                    engine.getRecord(record.recordId).getOrThrow(),
                    "every record in the batch must be reachable from the head",
                )
            }
        }

    @Test
    fun `a batch written after earlier records keeps them reachable`() =
        runSuspend {
            val blockStore = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(blockStore)
            engine.putRecord(recordId("existing"), buildJsonObject { put("index", 0) }).getOrThrow()

            engine
                .putRecords(
                    (1..3).map { i -> BatchRecordWrite(recordId("link-$i"), buildJsonObject { put("index", i) }) },
                ).getOrThrow()

            assertNotNull(
                engine.getRecord(recordId("existing")).getOrThrow(),
                "a batch must build on the existing tree, not replace it",
            )
            assertNotNull(engine.getRecord(recordId("link-3")).getOrThrow())
        }

    @Test
    fun `a batch write honors a matching swapRecord for an existing record`() =
        runSuspend {
            val blockStore = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(blockStore)
            val existing = engine.putRecord(recordId("existing"), buildJsonObject { put("index", 0) }).getOrThrow()

            val results =
                engine
                    .putRecords(
                        listOf(
                            BatchRecordWrite(recordId("existing"), buildJsonObject { put("index", 1) }, swapRecord = existing.cid),
                            BatchRecordWrite(recordId("link-1"), buildJsonObject { put("index", 2) }),
                        ),
                    ).getOrThrow()

            assertEquals(2, results.size)
            assertEquals(
                1,
                engine
                    .getRecord(recordId("existing"))
                    .getOrThrow()
                    ?.value
                    ?.get("index")
                    ?.jsonPrimitive
                    ?.int,
                "the CAS-guarded write should have gone through since the swapRecord matched",
            )
            assertNotNull(engine.getRecord(recordId("link-1")).getOrThrow())
        }

    @Test
    fun `a mismatched swapRecord fails the whole batch without partially committing it`() =
        runSuspend {
            val blockStore = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(blockStore)
            engine.putRecord(recordId("existing"), buildJsonObject { put("index", 0) }).getOrThrow()
            val commitsBeforeBatch = engine.listCommits(repo).getOrThrow().size

            assertFailsWith<InvalidSwapException> {
                engine
                    .putRecords(
                        listOf(
                            // This one has no CAS guard and would normally succeed on its own.
                            BatchRecordWrite(recordId("link-1"), buildJsonObject { put("index", 1) }),
                            // A stale swapRecord: the current CID for "existing" isn't "stale-cid".
                            BatchRecordWrite(recordId("existing"), buildJsonObject { put("index", 2) }, swapRecord = "stale-cid"),
                        ),
                    ).getOrThrow()
            }

            assertEquals(
                commitsBeforeBatch,
                engine.listCommits(repo).getOrThrow().size,
                "a failed batch must not leave a commit behind",
            )
            assertNull(engine.getRecord(recordId("link-1")).getOrThrow(), "no record in a failed batch should be reachable, CAS or not")
            assertEquals(
                0,
                engine
                    .getRecord(recordId("existing"))
                    .getOrThrow()
                    ?.value
                    ?.get("index")
                    ?.jsonPrimitive
                    ?.int,
                "the guarded record must keep its pre-batch value",
            )
        }
}

private fun runSuspend(block: suspend () -> Unit) {
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result -> result.getOrThrow() },
    )
}
