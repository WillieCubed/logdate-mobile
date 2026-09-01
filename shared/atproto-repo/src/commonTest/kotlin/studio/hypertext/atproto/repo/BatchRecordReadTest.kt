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

/**
 * A page of the change feed reads one record at a time, and opening the repo costs a pass over
 * every block it holds. Reading 25 records that way re-reads the whole repo 25 times, which is
 * why `GET /api/v1/contents?since=0&limit=25` timed out on a journal of a few hundred entries
 * while the same request for five journals answered instantly.
 */
class BatchRecordReadTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.entry")

    /** Counts how many times the engine asks the store for the repo's blocks. */
    private class CountingBlockStore : RepoBlockStore by InMemoryRepoBlockStore() {
        private val delegate = InMemoryRepoBlockStore()
        var listBlocksCalls = 0
            private set

        override suspend fun readHead(repo: AtprotoDid) = delegate.readHead(repo)

        override suspend fun writeHead(head: RepoHead) = delegate.writeHead(head)

        override suspend fun compareAndSwapHead(
            head: RepoHead,
            expectedRevision: Long?,
        ) = delegate.compareAndSwapHead(head, expectedRevision)

        override suspend fun readBlock(cid: Cid) = delegate.readBlock(cid)

        override suspend fun writeBlock(
            repo: AtprotoDid,
            block: RepoBlock,
        ) = delegate.writeBlock(repo, block)

        override suspend fun clearRepo(repo: AtprotoDid) = delegate.clearRepo(repo)

        override suspend fun listBlocks(repo: AtprotoDid): Result<List<RepoBlock>> {
            listBlocksCalls++
            return delegate.listBlocks(repo)
        }

        override suspend fun appendCommit(
            repo: AtprotoDid,
            commit: SignedRepoCommit,
        ) = delegate.appendCommit(repo, commit)

        override suspend fun listCommits(repo: AtprotoDid) = delegate.listCommits(repo)
    }

    private fun entry(text: String) =
        buildJsonObject {
            put("\$type", collection.toString())
            put("text", text)
        }

    @Test
    fun `reading a page of records opens the repo once rather than once per record`() =
        runSuspend {
            val blockStore = CountingBlockStore()
            val engine = DefaultRepoEngine(blockStore)
            val keys = (1..25).map { "entry-$it" }
            keys.forEach { key ->
                engine
                    .putRecord(RepoRecordId(repo, collection, RecordKey.require(key)), entry(key))
                    .getOrThrow()
            }

            val before = blockStore.listBlocksCalls
            val records =
                engine
                    .getRecords(keys.map { RepoRecordId(repo, collection, RecordKey.require(it)) })
                    .getOrThrow()

            assertEquals(25, records.size)
            assertEquals(25, records.count { it != null })
            assertEquals(1, blockStore.listBlocksCalls - before)
        }

    @Test
    fun `reading a page returns a null in place of a record that is not there`() =
        runSuspend {
            val engine = DefaultRepoEngine(CountingBlockStore())
            engine
                .putRecord(RepoRecordId(repo, collection, RecordKey.require("present")), entry("here"))
                .getOrThrow()

            val records =
                engine
                    .getRecords(
                        listOf(
                            RepoRecordId(repo, collection, RecordKey.require("present")),
                            RepoRecordId(repo, collection, RecordKey.require("absent")),
                        ),
                    ).getOrThrow()

            assertEquals(2, records.size)
            assertEquals(null, records[1])
        }
}

private fun runSuspend(block: suspend () -> Unit) {
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result -> result.getOrThrow() },
    )
}
