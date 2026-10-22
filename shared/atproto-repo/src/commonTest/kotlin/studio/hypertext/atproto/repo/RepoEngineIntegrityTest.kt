package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers what keeps a repository consistent around a commit: the commit history only records
 * commits that became the head, imports check everything before storing anything, and a repository
 * can be signed again without changing its records.
 */
class RepoEngineIntegrityTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.entry")

    @Test
    fun `a commit that loses the head race is not added to the history`() =
        runSuspend {
            val store = RefusingHeadStore()
            val engine = DefaultRepoEngine(store)

            val failure = engine.putRecord(recordId("entry-1"), entry("one")).exceptionOrNull()

            assertIs<RepoHeadConflictException>(failure)
            assertTrue(store.listCommits(repo).getOrThrow().isEmpty())
        }

    @Test
    fun `export uses the head's own commit block`() =
        runSuspend {
            val store = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(store)
            engine.putRecord(recordId("entry-1"), entry("one")).getOrThrow()
            val head = assertNotNull(store.readHead(repo).getOrThrow())
            // A history row can be missing, for example after a failure between moving the head and
            // recording it. The export still has to carry the commit the head names.
            store.clearCommitsOnly(repo)

            val export = engine.export(repo).getOrThrow()

            assertTrue(export.blocks.any { it.cid == head.commitCid })
            val restored = DefaultRepoEngine(InMemoryRepoBlockStore())
            restored.import(export).getOrThrow()
            assertEquals(entry("one"), assertNotNull(restored.getRecord(recordId("entry-1")).getOrThrow()).value)
        }

    @Test
    fun `import signs off the head commit over its stored bytes and skips older commits`() =
        runSuspend {
            val source = InMemoryRepoBlockStore()
            val sourceEngine = DefaultRepoEngine(source)
            sourceEngine.putRecord(recordId("entry-1"), entry("one")).getOrThrow()
            sourceEngine.putRecord(recordId("entry-2"), entry("two")).getOrThrow()
            val export = sourceEngine.export(repo).getOrThrow()
            val headBlock = export.blocks.first { it.cid == export.head.commitCid }
            val checked = mutableListOf<Pair<Cid, ByteArray>>()
            val signer = RecordingSigner { commit, payload -> checked += commit.root to payload }

            DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer).import(export).getOrThrow()

            // Older commits may be signed by a key the account has since rotated away from; only the
            // head has to verify against the current key.
            val (root, payload) = checked.single()
            assertEquals(export.head.root, root)
            val unsigned = DagCborCodec.encode(JsonObject(DagCborCodec.decode(headBlock.bytes).jsonObject - "sig"))
            assertContentEquals(unsigned, payload)
        }

    @Test
    fun `import refuses a bad signature before storing anything`() =
        runSuspend {
            val export = exportOneRecord()
            val target = InMemoryRepoBlockStore()

            val failure = DefaultRepoEngine(target, signer = RecordingSigner(valid = false)).import(export).exceptionOrNull()

            assertIs<IllegalArgumentException>(failure)
            assertTrue(target.listBlocks(repo).getOrThrow().isEmpty())
            assertNull(target.readHead(repo).getOrThrow())
        }

    @Test
    fun `import refuses a block whose bytes do not match its CID`() =
        runSuspend {
            val export = exportOneRecord()
            val tampered =
                export.copy(
                    blocks =
                        export.blocks.map { block ->
                            if (block.cid ==
                                export.head.root
                            ) {
                                block
                            } else {
                                block.copy(bytes = block.bytes + 0)
                            }
                        },
                )
            val target = InMemoryRepoBlockStore()

            val failure = DefaultRepoEngine(target).import(tampered).exceptionOrNull()

            assertIs<IllegalArgumentException>(failure)
            assertTrue(failure.message.orEmpty().contains("does not hash to its CID"), failure.message)
            assertTrue(target.listBlocks(repo).getOrThrow().isEmpty())
        }

    @Test
    fun `import refuses a head that names a commit the export does not carry`() =
        runSuspend {
            val export = exportOneRecord()
            val stranger = Cid.sha256(DAG_CBOR_CODEC, "another commit".encodeToByteArray())

            val failure =
                DefaultRepoEngine(InMemoryRepoBlockStore())
                    .import(export.copy(head = export.head.copy(commitCid = stranger)))
                    .exceptionOrNull()

            assertIs<IllegalArgumentException>(failure)
            assertTrue(failure.message.orEmpty().contains(stranger.toString()), failure.message)
        }

    @Test
    fun `import refuses a commit whose signature differs from its block`() =
        runSuspend {
            val export = exportOneRecord()
            val relabelled = export.copy(commits = export.commits.map { it.copy(signature = "c29tZXRoaW5nIGVsc2U") })

            val failure = DefaultRepoEngine(InMemoryRepoBlockStore()).import(relabelled).exceptionOrNull()

            assertIs<IllegalArgumentException>(failure)
        }

    @Test
    fun `recommit signs the same records again with a later revision`() =
        runSuspend {
            val store = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(store)
            engine.putRecord(recordId("entry-1"), entry("one")).getOrThrow()
            val before = assertNotNull(store.readHead(repo).getOrThrow())

            val after = assertNotNull(engine.recommit(repo).getOrThrow())

            assertEquals(before.root, after.root)
            assertTrue(after.revision > before.revision)
            assertTrue(after.commitCid != before.commitCid)
            assertEquals(after, store.readHead(repo).getOrThrow())
            assertEquals(
                listOf(before.commitCid, after.commitCid),
                store.listCommits(repo).getOrThrow().map(SignedRepoCommit::cid),
            )
        }

    @Test
    fun `recommit leaves a repository without commits alone`() =
        runSuspend {
            val store = InMemoryRepoBlockStore()

            assertNull(DefaultRepoEngine(store).recommit(repo).getOrThrow())
            assertNull(store.readHead(repo).getOrThrow())
        }

    private suspend fun exportOneRecord(): RepoExport {
        val engine = DefaultRepoEngine(InMemoryRepoBlockStore())
        engine.putRecord(recordId("entry-1"), entry("one")).getOrThrow()
        return engine.export(repo).getOrThrow()
    }

    private fun recordId(key: String) = RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require(key))

    private fun entry(text: String) =
        buildJsonObject {
            put("\$type", collection.toString())
            put("text", text)
        }

    private class RecordingSigner(
        private val valid: Boolean = true,
        private val onVerify: (RepoCommit, ByteArray) -> Unit = { _, _ -> },
    ) : RepoCommitSigner {
        constructor(onVerify: (RepoCommit, ByteArray) -> Unit) : this(valid = true, onVerify = onVerify)

        override suspend fun sign(
            commit: RepoCommit,
            payload: ByteArray,
        ): String = DigestRepoCommitSigner.sign(commit, payload)

        override suspend fun verify(
            commit: RepoCommit,
            payload: ByteArray,
            signature: String,
        ): Boolean {
            onVerify(commit, payload)
            return valid
        }
    }

    /** Never lets the head move, as if another writer always got there first. */
    private class RefusingHeadStore(
        private val delegate: InMemoryRepoBlockStore = InMemoryRepoBlockStore(),
    ) : RepoBlockStore by delegate {
        override suspend fun compareAndSwapHead(
            head: RepoHead,
            expectedRevision: Long?,
        ): Result<Boolean> = Result.success(false)
    }

    private suspend fun InMemoryRepoBlockStore.clearCommitsOnly(repo: AtprotoDid) {
        val head = assertNotNull(readHead(repo).getOrThrow())
        val blocks = listBlocks(repo).getOrThrow()
        clearRepo(repo).getOrThrow()
        blocks.forEach { writeBlock(repo, it).getOrThrow() }
        writeHead(head).getOrThrow()
    }

    private fun runSuspend(block: suspend () -> Unit) {
        var outcome: Result<Unit>? = null
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    outcome = result
                }
            },
        )
        assertNotNull(outcome, "The in-memory engine never suspends, so the test body must finish synchronously").getOrThrow()
    }
}
