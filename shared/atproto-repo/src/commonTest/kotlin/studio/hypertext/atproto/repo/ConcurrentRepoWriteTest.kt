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
 * A write reads the whole tree, rebuilds it, and writes the head back. Two writes interleaved on
 * one repo each build a tree missing the other's record, so a head write that does not check what
 * it is replacing silently drops whichever landed first -- the blocks survive, but nothing points
 * at them and the entry is gone from the user's journal.
 */
class ConcurrentRepoWriteTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.entry")

    /**
     * Lets the second write finish while the first is between reading the head and writing it, so
     * the first write is holding a snapshot that is already out of date when it tries to commit.
     */
    private class InterleavingBlockStore : RepoBlockStore by InMemoryRepoBlockStore() {
        private val delegate = InMemoryRepoBlockStore()
        var onBeforeSwap: (suspend () -> Unit)? = null

        override suspend fun readHead(repo: AtprotoDid) = delegate.readHead(repo)

        override suspend fun writeHead(head: RepoHead) = delegate.writeHead(head)

        override suspend fun compareAndSwapHead(
            head: RepoHead,
            expectedRevision: Long?,
        ): Result<Boolean> {
            onBeforeSwap?.let { hook ->
                onBeforeSwap = null
                hook()
            }
            return delegate.compareAndSwapHead(head, expectedRevision)
        }

        override suspend fun readBlock(cid: Cid) = delegate.readBlock(cid)

        override suspend fun writeBlock(
            repo: AtprotoDid,
            block: RepoBlock,
        ) = delegate.writeBlock(repo, block)

        override suspend fun clearRepo(repo: AtprotoDid) = delegate.clearRepo(repo)

        override suspend fun listBlocks(repo: AtprotoDid) = delegate.listBlocks(repo)

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
    fun `a write that lands during another write does not lose either record`() =
        runSuspend {
            val blockStore = InterleavingBlockStore()
            val engine = DefaultRepoEngine(blockStore)

            engine
                .putRecord(
                    RepoRecordId(repo, collection, RecordKey.require("first")),
                    entry("first"),
                ).getOrThrow()

            // While the "second" write is committing, slip a third write in underneath it. The
            // second must notice its snapshot is stale and rebuild from the winner's tree.
            blockStore.onBeforeSwap = {
                engine
                    .putRecord(
                        RepoRecordId(repo, collection, RecordKey.require("third")),
                        entry("third"),
                    ).getOrThrow()
            }

            engine
                .putRecord(
                    RepoRecordId(repo, collection, RecordKey.require("second")),
                    entry("second"),
                ).getOrThrow()

            val head = engine.loadHead(repo).getOrThrow()
            assertNotNull(head, "the repo should have a head")

            val reachable =
                MerkleSearchTree
                    .fromBlocks(head.root) { cid -> blockStore.readBlock(cid).getOrThrow() }
                    .entries()
                    .map { it.recordKey.toString() }
                    .toSet()

            assertEquals(
                setOf("first", "second", "third"),
                reachable,
                "every record written must still be reachable from the head",
            )
        }
}

private fun runSuspend(block: suspend () -> Unit) {
    block.startCoroutine(
        Continuation(EmptyCoroutineContext) { result -> result.getOrThrow() },
    )
}
