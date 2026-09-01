package studio.hypertext.atproto.repo

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
                    recordId("link-$i") to buildJsonObject { put("index", i) }
                }

            val results = engine.putRecords(records).getOrThrow()

            assertEquals(5, results.size, "every record should report its own result")
            assertEquals(
                1,
                engine.listCommits(repo).getOrThrow().size,
                "a batch is one write, so it should leave one commit behind",
            )
            records.forEach { (id, _) ->
                assertNotNull(
                    engine.getRecord(id).getOrThrow(),
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
                    (1..3).map { i -> recordId("link-$i") to buildJsonObject { put("index", i) } },
                ).getOrThrow()

            assertNotNull(
                engine.getRecord(recordId("existing")).getOrThrow(),
                "a batch must build on the existing tree, not replace it",
            )
            assertNotNull(engine.getRecord(recordId("link-3")).getOrThrow())
        }
}

private fun runSuspend(block: suspend () -> Unit) {
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result -> result.getOrThrow() },
    )
}
