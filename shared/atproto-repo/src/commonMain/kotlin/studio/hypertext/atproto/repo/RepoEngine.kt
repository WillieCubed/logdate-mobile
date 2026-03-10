package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.time.Clock

/**
 * Canonical repo engine built on deterministic blocks and commit history.
 */
public interface RepoEngine : RepoRecordStore {
    /**
     * Loads the current repo head.
     */
    public suspend fun loadHead(repo: AtprotoDid): Result<RepoHead?>

    /**
     * Lists commits for [repo].
     */
    public suspend fun listCommits(
        repo: AtprotoDid,
        limit: Int = DEFAULT_COMMIT_LIMIT,
    ): Result<List<SignedRepoCommit>>

    /**
     * Exports [repo] into a CAR-like archive payload.
     */
    public suspend fun export(repo: AtprotoDid): Result<RepoExport>

    /**
     * Imports [export] and installs it as the current repo state.
     */
    public suspend fun import(export: RepoExport): Result<RepoHead>

    public companion object {
        /**
         * Default commit history page size.
         */
        public const val DEFAULT_COMMIT_LIMIT: Int = 50
    }
}

/**
 * Commit signer used by the standalone repo runtime.
 */
public interface RepoCommitSigner {
    /**
     * Signs [payload] and returns the detached signature string.
     */
    public fun sign(payload: ByteArray): String

    /**
     * Verifies [signature] for [payload].
     */
    public fun verify(
        payload: ByteArray,
        signature: String,
    ): Boolean
}

/**
 * Deterministic digest-based commit signer used by default.
 */
public object DigestRepoCommitSigner : RepoCommitSigner {
    override fun sign(payload: ByteArray): String = Cid.sha256(DAG_CBOR_CODEC, payload).toString()

    override fun verify(
        payload: ByteArray,
        signature: String,
    ): Boolean = sign(payload) == signature
}

/**
 * Default repo engine implementation backed by a [RepoBlockStore].
 */
public class DefaultRepoEngine(
    private val blockStore: RepoBlockStore,
    private val clock: Clock = Clock.System,
    private val signer: RepoCommitSigner = DigestRepoCommitSigner,
) : RepoEngine {
    override suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?> =
        runCatching {
            val snapshot = loadSnapshot(recordId.repo)
            val cid = snapshot.tree.get(recordId.collection, recordId.recordKey) ?: return@runCatching null
            val block = blockStore.readBlock(cid).getOrThrow() ?: return@runCatching null
            RepoRecord(
                uri = recordId.uri,
                cid = cid.toString(),
                value = DagCborCodec.decode(block.bytes).jsonObject,
            )
        }

    override suspend fun listRecords(
        repo: AtprotoDid,
        collection: Nsid,
        limit: Int,
        cursor: String?,
        reverse: Boolean,
    ): Result<RepoListPage> =
        runCatching {
            val snapshot = loadSnapshot(repo)
            val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
            val sortedEntries =
                snapshot.tree
                    .entries(collection)
                    .filter { entry ->
                        cursor == null ||
                            if (reverse) {
                                entry.recordKey.toString() < cursor
                            } else {
                                entry.recordKey.toString() > cursor
                            }
                    }.let { entries ->
                        if (reverse) {
                            entries.sortedByDescending { it.recordKey.toString() }
                        } else {
                            entries.sortedBy { it.recordKey.toString() }
                        }
                    }
            val pageEntries = sortedEntries.take(safeLimit)
            RepoListPage(
                records =
                    pageEntries.map { entry ->
                        RepoRecord(
                            uri =
                                RepoRecordId(
                                    repo = repo,
                                    collection = collection,
                                    recordKey = entry.recordKey,
                                ).uri,
                            cid = entry.cid.toString(),
                            value = DagCborCodec.decode(requireNotNull(blockStore.readBlock(entry.cid).getOrThrow()).bytes).jsonObject,
                        )
                    },
                cursor =
                    pageEntries
                        .lastOrNull()
                        ?.recordKey
                        ?.toString()
                        ?.takeIf { sortedEntries.size > pageEntries.size },
            )
        }

    override suspend fun createRecord(
        repo: AtprotoDid,
        collection: Nsid,
        value: JsonObject,
        recordKey: RecordKey?,
    ): Result<RepoWriteResult> {
        val resolvedRecordKey = recordKey ?: RecordKey.require("record-${clock.now().epochSeconds}")
        return putRecord(
            recordId =
                RepoRecordId(
                    repo = repo,
                    collection = collection,
                    recordKey = resolvedRecordKey,
                ),
            value = value,
        )
    }

    override suspend fun putRecord(
        recordId: RepoRecordId,
        value: JsonObject,
        swapRecord: String?,
    ): Result<RepoWriteResult> =
        runCatching {
            val snapshot = loadSnapshot(recordId.repo)
            val previousCid = snapshot.tree.get(recordId.collection, recordId.recordKey)?.toString()
            if (swapRecord != null && previousCid != swapRecord) {
                throw InvalidSwapException(expectedCid = previousCid, providedCid = swapRecord)
            }

            val recordBytes = DagCborCodec.encode(value)
            val recordCid = Cid.sha256(DAG_CBOR_CODEC, recordBytes)
            blockStore.writeBlock(recordId.repo, RepoBlock(recordCid, recordBytes)).getOrThrow()

            val updatedTree = snapshot.tree.put(recordId.collection, recordId.recordKey, recordCid)
            val head = persistSnapshot(recordId.repo, updatedTree, snapshot.head)
            RepoWriteResult(
                uri = recordId.uri,
                cid = recordCid.toString(),
                validationStatus = RepoValidationStatus.UNKNOWN,
            )
        }

    override suspend fun deleteRecord(
        recordId: RepoRecordId,
        swapRecord: String?,
    ): Result<Boolean> =
        runCatching {
            val snapshot = loadSnapshot(recordId.repo)
            val previousCid = snapshot.tree.get(recordId.collection, recordId.recordKey)?.toString() ?: return@runCatching false
            if (swapRecord != null && previousCid != swapRecord) {
                throw InvalidSwapException(expectedCid = previousCid, providedCid = swapRecord)
            }
            val updatedTree = snapshot.tree.remove(recordId.collection, recordId.recordKey)
            persistSnapshot(recordId.repo, updatedTree, snapshot.head)
            true
        }

    override suspend fun loadHead(repo: AtprotoDid): Result<RepoHead?> = blockStore.readHead(repo)

    override suspend fun listCommits(
        repo: AtprotoDid,
        limit: Int,
    ): Result<List<SignedRepoCommit>> =
        blockStore
            .listCommits(repo)
            .mapCatching { commits -> commits.takeLast(limit.coerceAtLeast(1)).reversed() }

    override suspend fun export(repo: AtprotoDid): Result<RepoExport> =
        runCatching {
            val head = requireNotNull(blockStore.readHead(repo).getOrThrow()) { "Unknown repo: $repo" }
            RepoExport(
                repo = repo,
                head = head,
                commits = blockStore.listCommits(repo).getOrThrow(),
                blocks = blockStore.listBlocks(repo).getOrThrow(),
            )
        }

    override suspend fun import(export: RepoExport): Result<RepoHead> =
        runCatching {
            export.blocks.forEach { block ->
                blockStore.writeBlock(export.repo, block).getOrThrow()
            }
            export.commits.forEach { commit ->
                val commitBytes = encodeCommit(commit.commit)
                require(signer.verify(commitBytes, commit.signature)) { "Invalid commit signature for ${commit.cid}" }
                blockStore.appendCommit(export.repo, commit).getOrThrow()
            }
            blockStore.writeHead(export.head).getOrThrow()
            export.head
        }

    private suspend fun loadSnapshot(repo: AtprotoDid): RepoSnapshot {
        val head = blockStore.readHead(repo).getOrThrow() ?: return RepoSnapshot(head = null, tree = MerkleSearchTree.empty())
        val snapshotBlock = requireNotNull(blockStore.readBlock(head.root).getOrThrow()) { "Missing snapshot block ${head.root}" }
        val tree = MerkleSearchTree.fromJsonElement(DagCborCodec.decode(snapshotBlock.bytes).jsonArray)
        return RepoSnapshot(head = head, tree = tree)
    }

    private suspend fun persistSnapshot(
        repo: AtprotoDid,
        tree: MerkleSearchTree,
        previousHead: RepoHead?,
    ): RepoHead {
        val snapshotBytes = DagCborCodec.encode(tree.toJsonElement())
        val rootCid = Cid.sha256(DAG_CBOR_CODEC, snapshotBytes)
        blockStore.writeBlock(repo, RepoBlock(rootCid, snapshotBytes)).getOrThrow()

        val commit =
            RepoCommit(
                repo = repo,
                root = rootCid,
                prev = previousHead?.commitCid,
                revision = (previousHead?.revision ?: 0L) + 1L,
                createdAtEpochMillis = clock.now().toEpochMilliseconds(),
                recordCount = tree.entries().size,
            )
        val commitBytes = encodeCommit(commit)
        val commitCid = Cid.sha256(DAG_CBOR_CODEC, commitBytes)
        blockStore.writeBlock(repo, RepoBlock(commitCid, commitBytes)).getOrThrow()
        val signedCommit =
            SignedRepoCommit(
                cid = commitCid,
                commit = commit,
                signature = signer.sign(commitBytes),
            )
        blockStore.appendCommit(repo, signedCommit).getOrThrow()

        val head =
            RepoHead(
                repo = repo,
                root = rootCid,
                commitCid = commitCid,
                revision = commit.revision,
            )
        blockStore.writeHead(head).getOrThrow()
        return head
    }

    private fun encodeCommit(commit: RepoCommit): ByteArray =
        DagCborCodec.encode(
            buildJsonObject {
                put("repo", commit.repo.toString())
                put("root", commit.root.toString())
                commit.prev?.let { put("prev", it.toString()) }
                put("revision", commit.revision)
                put("createdAtEpochMillis", commit.createdAtEpochMillis)
                put("recordCount", commit.recordCount)
            },
        )

    private data class RepoSnapshot(
        val head: RepoHead?,
        val tree: MerkleSearchTree,
    )

    private companion object {
        private const val MAX_PAGE_SIZE: Int = 100
    }
}

/**
 * Serializes and deserializes repo exports into a deterministic archive payload.
 */
public object CarCodec {
    private val json = kotlinx.serialization.json.Json { explicitNulls = false }

    /**
     * Writes [export] into bytes.
     */
    public fun write(export: RepoExport): ByteArray {
        val payload =
            buildJsonObject {
                put("repo", export.repo.toString())
                put("root", export.head.root.toString())
                put("commit", export.head.commitCid.toString())
                put("revision", export.head.revision)
                put(
                    "commits",
                    buildJsonArray {
                        export.commits.forEach { commit ->
                            add(
                                buildJsonObject {
                                    put("cid", commit.cid.toString())
                                    put("repo", commit.commit.repo.toString())
                                    put("root", commit.commit.root.toString())
                                    commit.commit.prev?.let { put("prev", it.toString()) }
                                    put("revision", commit.commit.revision)
                                    put("createdAtEpochMillis", commit.commit.createdAtEpochMillis)
                                    put("recordCount", commit.commit.recordCount)
                                    put("signature", commit.signature)
                                },
                            )
                        }
                    },
                )
                put(
                    "blocks",
                    buildJsonArray {
                        export.blocks.forEach { block ->
                            add(
                                buildJsonObject {
                                    put("cid", block.cid.toString())
                                    put("data", block.bytes.toByteString().base64())
                                },
                            )
                        }
                    },
                )
            }
        return json
            .encodeToString(
                kotlinx.serialization.json.JsonObject
                    .serializer(),
                payload,
            ).encodeToByteArray()
    }

    /**
     * Reads [bytes] into a repo export.
     */
    public fun read(bytes: ByteArray): RepoExport {
        val payload = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val repo = AtprotoDid.require(payload.getValue("repo").jsonPrimitive.content)
        val head =
            RepoHead(
                repo = repo,
                root = Cid.require(payload.getValue("root").jsonPrimitive.content),
                commitCid = Cid.require(payload.getValue("commit").jsonPrimitive.content),
                revision = payload.getValue("revision").jsonPrimitive.long,
            )
        val commits =
            payload.getValue("commits").jsonArray.map { encodedCommit ->
                val commit = encodedCommit.jsonObject
                SignedRepoCommit(
                    cid = Cid.require(commit.getValue("cid").jsonPrimitive.content),
                    commit =
                        RepoCommit(
                            repo = AtprotoDid.require(commit.getValue("repo").jsonPrimitive.content),
                            root = Cid.require(commit.getValue("root").jsonPrimitive.content),
                            prev = commit["prev"]?.jsonPrimitive?.contentOrNull?.let(Cid::require),
                            revision = commit.getValue("revision").jsonPrimitive.long,
                            createdAtEpochMillis = commit.getValue("createdAtEpochMillis").jsonPrimitive.long,
                            recordCount = commit.getValue("recordCount").jsonPrimitive.int,
                        ),
                    signature = commit.getValue("signature").jsonPrimitive.content,
                )
            }
        val blocks =
            payload.getValue("blocks").jsonArray.map { encodedBlock ->
                val block = encodedBlock.jsonObject
                RepoBlock(
                    cid = Cid.require(block.getValue("cid").jsonPrimitive.content),
                    bytes =
                        requireNotNull(
                            block
                                .getValue("data")
                                .jsonPrimitive.content
                                .decodeBase64(),
                        ).toByteArray(),
                )
            }
        return RepoExport(
            repo = repo,
            head = head,
            commits = commits,
            blocks = blocks,
        )
    }
}

/**
 * Raised when compare-and-swap metadata does not match the current record state.
 */
public class InvalidSwapException(
    public val expectedCid: String?,
    public val providedCid: String,
) : RepoException("Invalid swapRecord")
