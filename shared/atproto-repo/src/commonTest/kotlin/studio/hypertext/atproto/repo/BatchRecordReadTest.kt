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

/** A change-feed page reuses one current tree without loading the repo's entire block history. */
class BatchRecordReadTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.entry")

    /** Counts requests to load the repo's entire block history. */
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
    fun `reading and updating a populated repo never loads its entire block history`() =
        runSuspend {
            val blockStore = CountingBlockStore()
            val engine = DefaultRepoEngine(blockStore)
            val recordId = RepoRecordId(repo, collection, RecordKey.require("entry-1"))
            engine.putRecord(recordId, entry("before")).getOrThrow()

            val before = blockStore.listBlocksCalls
            val storedText =
                engine
                    .getRecord(recordId)
                    .getOrThrow()
                    ?.value
                    ?.get("text")
                    ?.toString()
                    ?.trim('"')
            assertEquals("before", storedText)
            engine.putRecord(recordId, entry("after")).getOrThrow()

            assertEquals(before, blockStore.listBlocksCalls)
        }

    @Test
    fun `reading a page of records avoids loading the repo block history`() =
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
            assertEquals(0, blockStore.listBlocksCalls - before)
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
