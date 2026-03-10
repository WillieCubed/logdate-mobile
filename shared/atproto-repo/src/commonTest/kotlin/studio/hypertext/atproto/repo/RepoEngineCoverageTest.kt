package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class RepoEngineCoverageTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.entry")
    private val fixedClock = FixedClock(Instant.parse("2026-03-08T00:00:00Z"))

    @Test
    fun `cid helpers parse values and encode low level primitives`() {
        val cid = Cid.sha256(codec = DAG_CBOR_CODEC, bytes = "hello".encodeToByteArray())

        assertEquals(cid, Cid.parse(" $cid ").getOrThrow())
        assertTrue(Cid.parse("z-not-base32").isFailure)
        assertEquals(cid.toString(), cid.value)
        assertContentEquals(byteArrayOf(0x00), encodeVarint(0))
        assertContentEquals(byteArrayOf(0x7f), encodeVarint(127))
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), encodeVarint(128))
        assertEquals("", encodeBase32(byteArrayOf()))
        assertTrue(encodeBase32("hello".encodeToByteArray()).isNotBlank())
    }

    @Test
    fun `dag cbor codec round trips supported values and rejects invalid payloads`() {
        val element =
            buildJsonObject {
                put("string", "hello")
                put("true", true)
                put("false", false)
                put("small", 7)
                put("negative", -9)
                put("byte", 200)
                put("short", 70_000)
                put("long", 5_000_000_000L)
                put(
                    "array",
                    buildJsonArray {
                        add(JsonPrimitive("item"))
                        add(JsonPrimitive(false))
                        add(JsonNull)
                    },
                )
            }

        val decoded = DagCborCodec.decode(DagCborCodec.encode(element))

        assertEquals(element, decoded)
        assertEquals(
            JsonPrimitive(5_000_000_000L),
            DagCborCodec.decode(DagCborCodec.encode(JsonPrimitive(5_000_000_000L))),
        )
        assertEquals(JsonPrimitive(-1), DagCborCodec.decode(DagCborCodec.encode(JsonPrimitive(-1))))
        assertFails { DagCborCodec.encode(JsonPrimitive(1.5)) }
        assertFails { DagCborCodec.decode(byteArrayOf(0x1c)) }
        assertFails { DagCborCodec.decode(byteArrayOf(0x40)) }
        assertFails { DagCborCodec.decode(byteArrayOf(0x61)) }
        assertFails { DagCborCodec.decode(byteArrayOf(0xff.toByte())) }
    }

    @Test
    fun `car codec round trips exports with commits and blocks`() {
        val root = Cid.sha256(codec = DAG_CBOR_CODEC, bytes = "root".encodeToByteArray())
        val prev = Cid.sha256(codec = DAG_CBOR_CODEC, bytes = "prev".encodeToByteArray())
        val commitCid = Cid.sha256(codec = DAG_CBOR_CODEC, bytes = "commit".encodeToByteArray())
        val head = RepoHead(repo = repo, root = root, commitCid = commitCid, revision = 2L)
        val commit =
            RepoCommit(
                repo = repo,
                root = root,
                prev = prev,
                revision = 2L,
                createdAtEpochMillis = 1_741_391_200_000L,
                recordCount = 3,
            )
        val export =
            RepoExport(
                repo = repo,
                head = head,
                commits = listOf(SignedRepoCommit(cid = commitCid, commit = commit, signature = "sig-1")),
                blocks =
                    listOf(
                        RepoBlock(cid = root, bytes = "root".encodeToByteArray()),
                        RepoBlock(cid = commitCid, bytes = "commit".encodeToByteArray()),
                    ),
            )

        val restored = CarCodec.read(CarCodec.write(export))

        assertEquals(export.repo, restored.repo)
        assertEquals(export.head, restored.head)
        assertEquals(export.commits, restored.commits)
        assertEquals(export.blocks.map { it.cid }, restored.blocks.map { it.cid })
        assertContentEquals(export.blocks.first().bytes, restored.blocks.first().bytes)
    }

    @Test
    fun `tree root cid and repo commit defaults remain deterministic`() {
        val recordCid = Cid.sha256(codec = DAG_CBOR_CODEC, bytes = "entry".encodeToByteArray())
        val tree = MerkleSearchTree.empty().put(collection = collection, recordKey = RecordKey.require("entry-1"), cid = recordCid)
        val rootCid = tree.rootCid()
        val commit =
            RepoCommit(
                repo = repo,
                root = rootCid,
                revision = 1L,
                createdAtEpochMillis = 1_741_391_200_000L,
                recordCount = 1,
            )

        assertEquals(Cid.sha256(DAG_CBOR_CODEC, DagCborCodec.encode(tree.toJsonElement())), rootCid)
        assertNull(commit.prev)
        assertEquals(1, commit.recordCount)
    }

    @Test
    fun `repo engine keeps identical record keys isolated by collection`() {
        val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), fixedClock)
        val alternateCollection = Nsid.require("studio.hypertext.logdate.journal")
        val sharedRecordKey = RecordKey.require("shared-key")

        runSuspend {
            engine
                .putRecord(
                    recordId = RepoRecordId(repo = repo, collection = collection, recordKey = sharedRecordKey),
                    value =
                        buildJsonObject {
                            put("\$type", collection.toString())
                            put("text", "content")
                        },
                ).getOrThrow()
        }
        runSuspend {
            engine
                .putRecord(
                    recordId = RepoRecordId(repo = repo, collection = alternateCollection, recordKey = sharedRecordKey),
                    value =
                        buildJsonObject {
                            put("\$type", alternateCollection.toString())
                            put("title", "journal")
                        },
                ).getOrThrow()
        }

        val content = runSuspend { engine.getRecord(RepoRecordId(repo, collection, sharedRecordKey)).getOrThrow() }
        val journal = runSuspend { engine.getRecord(RepoRecordId(repo, alternateCollection, sharedRecordKey)).getOrThrow() }
        val listedContent = runSuspend { engine.listRecords(repo, collection).getOrThrow() }
        val listedJournal = runSuspend { engine.listRecords(repo, alternateCollection).getOrThrow() }

        assertEquals(
            "content",
            content
                ?.value
                ?.get("text")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "journal",
            journal
                ?.value
                ?.get("title")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(listOf(collection), listedContent.records.map { it.uri.collection!! })
        assertEquals(listOf(alternateCollection), listedJournal.records.map { it.uri.collection!! })
    }

    @Test
    fun `repo engine covers list delete export and swap validation branches`() {
        val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), fixedClock)
        val alpha =
            buildJsonObject {
                put("\$type", collection.toString())
                put("text", "alpha")
            }
        val beta =
            buildJsonObject {
                put("\$type", collection.toString())
                put("text", "beta")
            }
        val gamma =
            buildJsonObject {
                put("\$type", collection.toString())
                put("text", "gamma")
            }

        val generated =
            runSuspend {
                val result =
                    engine.createRecord(
                        repo = repo,
                        collection = collection,
                        value = alpha,
                        recordKey = null,
                    )
                result.getOrThrow()
            }
        val betaWrite =
            runSuspend {
                val result =
                    engine.putRecord(
                        recordId = RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require("entry-2")),
                        value = beta,
                    )
                result.getOrThrow()
            }
        val gammaWrite =
            runSuspend {
                val result =
                    engine.putRecord(
                        recordId = RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require("entry-3")),
                        value = gamma,
                    )
                result.getOrThrow()
            }

        val firstPage = runSuspend { engine.listRecords(repo, collection, limit = 2, cursor = null, reverse = false).getOrThrow() }
        val secondPage =
            runSuspend {
                val result =
                    engine.listRecords(
                        repo = repo,
                        collection = collection,
                        limit = 2,
                        cursor = firstPage.cursor,
                        reverse = false,
                    )
                result.getOrThrow()
            }
        val reversePage = runSuspend { engine.listRecords(repo, collection, limit = 2, cursor = null, reverse = true).getOrThrow() }
        val reverseCursorPage =
            runSuspend {
                val result =
                    engine.listRecords(
                        repo = repo,
                        collection = collection,
                        limit = 2,
                        cursor = "entry-3",
                        reverse = true,
                    )
                result.getOrThrow()
            }
        val invalidPut =
            runSuspend {
                engine.putRecord(
                    recordId = RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require("entry-2")),
                    value = beta,
                    swapRecord = "cid-does-not-match",
                )
            }
        val missingDelete =
            runSuspend {
                val result =
                    engine.deleteRecord(
                        RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require("missing")),
                    )
                result.getOrThrow()
            }
        val invalidDelete =
            runSuspend {
                engine.deleteRecord(
                    RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require("entry-2")),
                    swapRecord = "cid-does-not-match",
                )
            }
        val deleted =
            runSuspend {
                val result =
                    engine.deleteRecord(
                        RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require("entry-2")),
                        swapRecord = betaWrite.cid,
                    )
                result.getOrThrow()
            }
        val remaining = runSuspend { engine.getRecord(RepoRecordId(repo, collection, RecordKey.require("entry-2"))).getOrThrow() }
        val commits = runSuspend { engine.listCommits(repo, limit = 0).getOrThrow() }
        val exported = runSuspend { engine.export(repo).getOrThrow() }
        val unknownExport = runSuspend { engine.export(AtprotoDid.require("did:web:missing.logdate.app")).exceptionOrNull() }

        assertTrue(generated.uri.toString().contains("/record-"))
        assertEquals(listOf(betaWrite.cid, gammaWrite.cid), firstPage.records.mapNotNull { it.cid })
        assertEquals(listOf(generated.cid), secondPage.records.mapNotNull { it.cid })
        assertEquals(listOf(generated.cid, gammaWrite.cid), reversePage.records.mapNotNull { it.cid })
        assertEquals(listOf(betaWrite.cid), reverseCursorPage.records.mapNotNull { it.cid })
        val invalidPutSwap = assertIs<InvalidSwapException>(invalidPut.exceptionOrNull())
        assertEquals(betaWrite.cid, invalidPutSwap.expectedCid)
        assertEquals("cid-does-not-match", invalidPutSwap.providedCid)
        assertFalseOrMissing(missingDelete)
        val invalidSwap = assertIs<InvalidSwapException>(invalidDelete.exceptionOrNull())
        assertEquals(betaWrite.cid, invalidSwap.expectedCid)
        assertEquals("cid-does-not-match", invalidSwap.providedCid)
        assertTrue(deleted)
        assertNull(remaining)
        assertEquals(1, commits.size)
        assertEquals(repo, exported.repo)
        assertTrue(unknownExport is IllegalArgumentException)
    }

    private fun assertFalseOrMissing(value: Boolean) {
        assertEquals(false, value)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return outcome!!.getOrThrow()
    }

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
