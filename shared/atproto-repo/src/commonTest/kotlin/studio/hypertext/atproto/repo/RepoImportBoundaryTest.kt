package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoImportBoundaryTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.entry")
    private val record = RepoRecordId(repo, collection, RecordKey.require("entry-1"))

    @Test
    fun `import rejects metadata that differs from the signed block before writing`() =
        runSuspend {
            val source = DefaultRepoEngine(InMemoryRepoBlockStore())
            source.putRecord(record, entry("original")).getOrThrow()
            val export = source.export(repo).getOrThrow()
            val original = export.commits.single()
            val replacement = DefaultRepoEngine(InMemoryRepoBlockStore())
            replacement.putRecord(record, entry("substituted")).getOrThrow()
            val other = replacement.export(repo).getOrThrow()
            val variants =
                listOf(
                    original.commit.copy(root = other.head.root),
                    original.commit.copy(repo = AtprotoDid.require("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa")),
                    original.commit.copy(revision = original.commit.revision + 1),
                    original.commit.copy(prev = other.head.commitCid),
                )

            variants.forEach { metadata ->
                val target = InMemoryRepoBlockStore()
                val forged =
                    export.copy(
                        repo = metadata.repo,
                        head = export.head.copy(repo = metadata.repo, root = metadata.root, revision = metadata.revision),
                        commits = listOf(original.copy(commit = metadata)),
                        blocks = (export.blocks + other.blocks).distinctBy(RepoBlock::cid),
                    )

                assertIs<IllegalArgumentException>(DefaultRepoEngine(target).import(forged).exceptionOrNull())
                assertTrue(target.listBlocks(metadata.repo).getOrThrow().isEmpty())
                assertNull(target.readHead(metadata.repo).getOrThrow())
            }
        }

    @Test
    fun `import rejects unsupported signed commit versions`() =
        runSuspend {
            val source = DefaultRepoEngine(InMemoryRepoBlockStore())
            source.putRecord(record, entry("original")).getOrThrow()
            val export = source.export(repo).getOrThrow()
            val original = export.commits.single()
            val block = export.blocks.first { it.cid == original.cid }
            val unsigned = JsonObject(DagCborCodec.decode(block.bytes).jsonObject - "sig" + ("version" to JsonPrimitive(2)))
            val signature = DigestRepoCommitSigner.sign(original.commit, DagCborCodec.encode(unsigned))
            val bytes = DagCborCodec.encode(JsonObject(unsigned + ("sig" to DagCborCodec.bytes(encodeCommitSignature(signature)))))
            val cid = Cid.sha256(DAG_CBOR_CODEC, bytes)
            val forged =
                export.copy(
                    head = export.head.copy(commitCid = cid),
                    commits = listOf(original.copy(cid = cid, signature = signature)),
                    blocks = export.blocks.filterNot { it.cid == original.cid } + RepoBlock(cid, bytes),
                )
            val target = InMemoryRepoBlockStore()

            assertIs<IllegalArgumentException>(DefaultRepoEngine(target).import(forged).exceptionOrNull())
            assertTrue(target.listBlocks(repo).getOrThrow().isEmpty())
        }

    @Test
    fun `upgraded repositories retain actual legacy signature rows through both import paths`() =
        runSuspend {
            val store = InMemoryRepoBlockStore()
            val tree = MerkleSearchTree.empty().toBlockGraph()
            tree.blocks.forEach { store.writeBlock(repo, it).getOrThrow() }
            val unsigned =
                buildJsonObject {
                    put("version", 3)
                    put("did", repo.toString())
                    put("data", DagCborCodec.link(tree.root))
                    put("rev", Tid.fromLong(1).value)
                }
            val oldSignature = Cid.sha256(DAG_CBOR_CODEC, DagCborCodec.encode(unsigned)).toString()
            val bytes =
                DagCborCodec.encode(
                    buildJsonObject {
                        unsigned.forEach { (key, value) -> put(key, value) }
                        put("sig", DagCborCodec.bytes(oldSignature.encodeToByteArray()))
                    },
                )
            val cid = Cid.sha256(DAG_CBOR_CODEC, bytes)
            store.writeBlock(repo, RepoBlock(cid, bytes)).getOrThrow()
            store
                .appendCommit(
                    repo,
                    SignedRepoCommit(
                        cid,
                        RepoCommit(repo, tree.root, revision = 1, createdAtEpochMillis = 0, recordCount = 0),
                        oldSignature,
                    ),
                ).getOrThrow()
            store.writeHead(RepoHead(repo, tree.root, cid, 1)).getOrThrow()
            val engine = DefaultRepoEngine(store)
            engine.putRecord(record, entry("recovered")).getOrThrow()
            val export = engine.export(repo).getOrThrow()

            listOf(export, CarCodec.read(CarCodec.write(export))).forEach { candidate ->
                val restored = DefaultRepoEngine(InMemoryRepoBlockStore())
                restored.import(candidate).getOrThrow()
                assertEquals(entry("recovered"), assertNotNull(restored.getRecord(record).getOrThrow()).value)
            }
        }

    private fun entry(text: String) =
        buildJsonObject {
            put("\$type", collection.toString())
            put("text", text)
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
        assertNotNull(outcome).getOrThrow()
    }
}
