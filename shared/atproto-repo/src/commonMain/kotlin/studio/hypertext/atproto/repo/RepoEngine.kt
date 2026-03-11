package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonElement
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
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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
     * Exports [repo] into a CAR archive payload.
     */
    public suspend fun export(
        repo: AtprotoDid,
        since: Tid? = null,
    ): Result<RepoExport>

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
     * Signs [payload] for [commit] and returns the detached signature string.
     */
    public suspend fun sign(
        commit: RepoCommit,
        payload: ByteArray,
    ): String

    /**
     * Verifies [signature] for [commit] and [payload].
     */
    public suspend fun verify(
        commit: RepoCommit,
        payload: ByteArray,
        signature: String,
    ): Boolean
}

/**
 * Deterministic digest-based commit signer used by default.
 */
public object DigestRepoCommitSigner : RepoCommitSigner {
    override suspend fun sign(
        commit: RepoCommit,
        payload: ByteArray,
    ): String = Cid.sha256(DAG_CBOR_CODEC, payload).toString()

    override suspend fun verify(
        commit: RepoCommit,
        payload: ByteArray,
        signature: String,
    ): Boolean = sign(commit, payload) == signature
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
            persistSnapshot(recordId.repo, updatedTree, snapshot.head)
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

    override suspend fun export(
        repo: AtprotoDid,
        since: Tid?,
    ): Result<RepoExport> =
        runCatching {
            val snapshot = loadSnapshot(repo)
            val head = requireNotNull(snapshot.head) { "Unknown repo: $repo" }
            val commits = blockStore.listCommits(repo).getOrThrow()
            val exportCommits =
                commits.filter { commit ->
                    since == null || commit.commit.revision > since.toLong()
                }
            val currentBlocks = reachableBlocks(repo, snapshot.tree, commits.lastOrNull())
            val previousBlockIds =
                if (since == null) {
                    emptySet()
                } else {
                    val previousCommit = commits.lastOrNull { commit -> commit.commit.revision == since.toLong() }
                    previousCommit?.let { commit ->
                        val previousTree = MerkleSearchTree.fromBlocks(commit.commit.root) { cid -> blockStore.readBlock(cid).getOrThrow() }
                        reachableBlocks(repo, previousTree, commit).mapTo(linkedSetOf(), RepoBlock::cid)
                    } ?: emptySet()
                }
            RepoExport(
                repo = repo,
                head = head,
                commits = exportCommits,
                blocks = currentBlocks.filterNot { block -> block.cid in previousBlockIds },
            )
        }

    override suspend fun import(export: RepoExport): Result<RepoHead> =
        runCatching {
            export.blocks.forEach { block ->
                blockStore.writeBlock(export.repo, block).getOrThrow()
            }
            export.commits.forEach { commit ->
                val commitBytes = encodeCommitPayload(commit.commit)
                require(signer.verify(commit.commit, commitBytes, commit.signature)) { "Invalid commit signature for ${commit.cid}" }
                blockStore.appendCommit(export.repo, commit).getOrThrow()
            }
            blockStore.writeHead(export.head).getOrThrow()
            export.head
        }

    private suspend fun loadSnapshot(repo: AtprotoDid): RepoSnapshot {
        val head = blockStore.readHead(repo).getOrThrow() ?: return RepoSnapshot(head = null, tree = MerkleSearchTree.empty())
        val tree = MerkleSearchTree.fromBlocks(head.root) { cid -> blockStore.readBlock(cid).getOrThrow() }
        return RepoSnapshot(head = head, tree = tree)
    }

    private suspend fun persistSnapshot(
        repo: AtprotoDid,
        tree: MerkleSearchTree,
        previousHead: RepoHead?,
    ): RepoHead {
        val graph = tree.toBlockGraph()
        graph.blocks.forEach { block ->
            blockStore.writeBlock(repo, block).getOrThrow()
        }
        val rootCid = graph.root

        val commit =
            RepoCommit(
                repo = repo,
                root = rootCid,
                prev = previousHead?.commitCid,
                revision = (previousHead?.revision ?: 0L) + 1L,
                createdAtEpochMillis = clock.now().toEpochMilliseconds(),
                recordCount = tree.entries().size,
            )
        val unsignedCommitBytes = encodeCommitPayload(commit)
        val signature = signer.sign(commit, unsignedCommitBytes)
        val signedCommitBytes = encodeSignedCommitPayload(commit, signature)
        val commitCid = Cid.sha256(DAG_CBOR_CODEC, signedCommitBytes)
        blockStore.writeBlock(repo, RepoBlock(commitCid, signedCommitBytes)).getOrThrow()

        val signedCommit =
            SignedRepoCommit(
                cid = commitCid,
                commit = commit,
                signature = signature,
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

    private data class RepoSnapshot(
        val head: RepoHead?,
        val tree: MerkleSearchTree,
    )

    private suspend fun reachableBlocks(
        repo: AtprotoDid,
        tree: MerkleSearchTree,
        headCommit: SignedRepoCommit?,
    ): List<RepoBlock> {
        val blocks = linkedMapOf<Cid, RepoBlock>()
        headCommit?.let { commit ->
            val commitBlock = requireNotNull(blockStore.readBlock(commit.cid).getOrThrow()) { "Missing commit block ${commit.cid}" }
            blocks[commitBlock.cid] = commitBlock
        }
        val graph = tree.toBlockGraph()
        graph.blocks.forEach { block ->
            blocks[block.cid] = block
        }
        tree.entries().forEach { entry ->
            val leafBlock = requireNotNull(blockStore.readBlock(entry.cid).getOrThrow()) { "Missing record block ${entry.cid}" }
            blocks[leafBlock.cid] = leafBlock
        }
        return blocks.values.toList()
    }

    private companion object {
        private const val MAX_PAGE_SIZE: Int = 100
    }
}

/**
 * Serializes and deserializes repo exports into CAR v1 archives.
 */
@OptIn(ExperimentalEncodingApi::class)
public object CarCodec {
    /**
     * Writes [export] into CAR bytes.
     */
    public fun write(export: RepoExport): ByteArray {
        val blockMap = LinkedHashMap<Cid, RepoBlock>()
        export.blocks.forEach { block ->
            blockMap[block.cid] = block
        }
        export.commits.forEach { commit ->
            blockMap[commit.cid] =
                RepoBlock(
                    cid = commit.cid,
                    bytes = encodeSignedCommitPayload(commit.commit, commit.signature),
                )
        }
        return writeCar(roots = listOf(export.head.commitCid), blocks = blockMap.values.toList())
    }

    /**
     * Reads [bytes] into a repo export.
     */
    public fun read(bytes: ByteArray): RepoExport {
        val archive = readCar(bytes)
        val commits = archive.blocks.mapNotNull(::decodeSignedCommitBlock).sortedBy { it.commit.revision }
        val headCommit = requireNotNull(commits.lastOrNull()) { "CAR archive did not contain a repo commit block" }
        val head =
            RepoHead(
                repo = headCommit.commit.repo,
                root = headCommit.commit.root,
                commitCid = archive.roots.singleOrNull() ?: headCommit.cid,
                revision = headCommit.commit.revision,
            )
        return RepoExport(
            repo = headCommit.commit.repo,
            head = head,
            commits = commits,
            blocks = archive.blocks,
        )
    }

    /**
     * Writes a raw CAR v1 archive for [roots] and [blocks].
     */
    public fun writeCar(
        roots: List<Cid>,
        blocks: List<RepoBlock>,
    ): ByteArray {
        val headerBytes =
            DagCborCodec.encode(
                buildJsonObject {
                    put("version", 1)
                    put(
                        "roots",
                        buildJsonArray {
                            roots.forEach { add(DagCborCodec.link(it)) }
                        },
                    )
                },
            )
        return joinByteArrays(
            buildList {
                add(writeSection(headerBytes))
                blocks.forEach { block ->
                    add(writeSection(block.cid.toBytes() + block.bytes))
                }
            },
        )
    }

    /**
     * Reads a CAR v1 archive.
     */
    public fun readCar(bytes: ByteArray): CarArchive {
        var cursor = 0
        val headerSection = readSection(bytes, cursor)
        cursor = headerSection.nextOffset
        val header = DagCborCodec.decode(headerSection.sectionBytes).jsonObject
        require(header.getValue("version").jsonPrimitive.int == 1) { "Unsupported CAR version" }
        val roots =
            header.getValue("roots").jsonArray.map { encodedRoot ->
                requireNotNull(DagCborCodec.linkOrNull(encodedRoot)) { "CAR roots must be CID links" }
            }

        val blocks = mutableListOf<RepoBlock>()
        while (cursor < bytes.size) {
            val section = readSection(bytes, cursor)
            cursor = section.nextOffset
            val decoded = decodeCid(section.sectionBytes)
            blocks += RepoBlock(cid = decoded.cid, bytes = decoded.blockBytes)
        }
        return CarArchive(roots = roots, blocks = blocks)
    }

    private fun decodeSignedCommitBlock(block: RepoBlock): SignedRepoCommit? =
        runCatching {
            val encoded = DagCborCodec.decode(block.bytes).jsonObject
            val signature =
                encoded["sig"]?.let(::decodeCommitSignature)
                    ?: encoded["sig"]?.jsonPrimitive?.contentOrNull
                    ?: return null
            val did = encoded["did"]?.jsonPrimitive?.content ?: encoded["repo"]?.jsonPrimitive?.content
            val data =
                DagCborCodec.linkOrNull(encoded["data"])?.toString()
                    ?: encoded["data"]?.jsonPrimitive?.contentOrNull
                    ?: DagCborCodec.linkOrNull(encoded["root"])?.toString()
                    ?: encoded["root"]?.jsonPrimitive?.contentOrNull
            val rev =
                encoded["rev"]
                    ?.jsonPrimitive
                    ?.content
                    ?.let(Tid::require)
                    ?.toLong()
                    ?: encoded["revision"]?.jsonPrimitive?.long
            val commit =
                RepoCommit(
                    repo = AtprotoDid.require(requireNotNull(did) { "Signed repo commit is missing did/repo" }),
                    root = Cid.require(requireNotNull(data) { "Signed repo commit is missing data/root" }),
                    prev =
                        DagCborCodec.linkOrNull(encoded["prev"])
                            ?: encoded["prev"]?.jsonPrimitive?.contentOrNull?.let(Cid::require),
                    revision = requireNotNull(rev) { "Signed repo commit is missing rev/revision" },
                    createdAtEpochMillis = encoded["createdAtEpochMillis"]?.jsonPrimitive?.long ?: 0L,
                    recordCount = encoded["recordCount"]?.jsonPrimitive?.int ?: 0,
                )
            SignedRepoCommit(cid = block.cid, commit = commit, signature = signature)
        }.getOrNull()

    private fun writeSection(sectionBytes: ByteArray): ByteArray = encodeVarintLong(sectionBytes.size.toLong()) + sectionBytes

    private fun readSection(
        bytes: ByteArray,
        offset: Int,
    ): CarSection {
        val (sectionLength, lengthBytes) = decodeVarintLong(bytes, offset)
        val start = offset + lengthBytes
        val end = start + sectionLength.toInt()
        require(end <= bytes.size) { "Unexpected end of CAR input" }
        return CarSection(
            sectionBytes = bytes.copyOfRange(start, end),
            nextOffset = end,
        )
    }

    private fun decodeCid(sectionBytes: ByteArray): DecodedCid {
        val (_, versionLength) = decodeVarintLong(sectionBytes, 0)
        val (_, codecLength) = decodeVarintLong(sectionBytes, versionLength)
        val digestCodeOffset = versionLength + codecLength
        val (_, digestCodeLength) = decodeVarintLong(sectionBytes, digestCodeOffset)
        val (digestSize, digestSizeLength) = decodeVarintLong(sectionBytes, digestCodeOffset + digestCodeLength)
        val cidLength = digestCodeOffset + digestCodeLength + digestSizeLength + digestSize.toInt()
        require(cidLength <= sectionBytes.size) { "Invalid CID length inside CAR block" }
        return DecodedCid(
            cid = Cid.fromBytes(sectionBytes.copyOfRange(0, cidLength)),
            blockBytes = sectionBytes.copyOfRange(cidLength, sectionBytes.size),
        )
    }
}

private fun encodeCommitPayload(commit: RepoCommit): ByteArray =
    DagCborCodec.encode(
        buildJsonObject {
            put("version", REPO_COMMIT_VERSION)
            put("did", commit.repo.toString())
            put("data", DagCborCodec.link(commit.root))
            commit.prev?.let { put("prev", DagCborCodec.link(it)) }
            put("rev", Tid.fromLong(commit.revision).toString())
        },
    )

private fun encodeSignedCommitPayload(
    commit: RepoCommit,
    signature: String,
): ByteArray =
    DagCborCodec.encode(
        buildJsonObject {
            put("version", REPO_COMMIT_VERSION)
            put("did", commit.repo.toString())
            put("data", DagCborCodec.link(commit.root))
            commit.prev?.let { put("prev", DagCborCodec.link(it)) }
            put("rev", Tid.fromLong(commit.revision).toString())
            put("sig", DagCborCodec.bytes(encodeCommitSignature(signature)))
        },
    )

private const val REPO_COMMIT_VERSION: Int = 3

/**
 * Parsed CAR archive payload.
 */
public data class CarArchive(
    val roots: List<Cid>,
    val blocks: List<RepoBlock>,
)

private data class CarSection(
    val sectionBytes: ByteArray,
    val nextOffset: Int,
)

private data class DecodedCid(
    val cid: Cid,
    val blockBytes: ByteArray,
)

private fun encodeCommitSignature(signature: String): ByteArray =
    if (Cid.parse(signature).isSuccess) {
        signature.encodeToByteArray()
    } else {
        runCatching { Base64.UrlSafe.decode(signature.padBase64Url()) }.getOrElse { signature.encodeToByteArray() }
    }

private fun decodeCommitSignature(element: JsonElement): String {
    val signatureBytes = DagCborCodec.bytesOrNull(element) ?: error("Commit signature must be bytes")
    val asciiSignature = signatureBytes.decodeToString()
    return if (Cid.parse(asciiSignature).isSuccess) {
        asciiSignature
    } else {
        Base64.UrlSafe.encode(signatureBytes).trimEnd('=')
    }
}

private fun joinByteArrays(chunks: List<ByteArray>): ByteArray {
    val totalSize = chunks.sumOf(ByteArray::size)
    val output = ByteArray(totalSize)
    var cursor = 0
    chunks.forEach { chunk ->
        chunk.copyInto(output, destinationOffset = cursor)
        cursor += chunk.size
    }
    return output
}

private fun String.padBase64Url(): String = this + "=".repeat((4 - length % 4) % 4)

private fun encodeVarintLong(value: Long): ByteArray {
    require(value >= 0L) { "Varints must be non-negative" }
    var remaining = value
    val bytes = mutableListOf<Byte>()
    do {
        var next = (remaining and 0x7f).toInt()
        remaining = remaining ushr 7
        if (remaining != 0L) {
            next = next or 0x80
        }
        bytes += next.toByte()
    } while (remaining != 0L)
    return bytes.toByteArray()
}

private fun decodeVarintLong(
    bytes: ByteArray,
    offset: Int,
): Pair<Long, Int> {
    var value = 0L
    var shift = 0
    var index = offset
    while (index < bytes.size) {
        val next = bytes[index].toInt() and 0xff
        value = value or ((next and 0x7f).toLong() shl shift)
        index += 1
        if ((next and 0x80) == 0) {
            return value to (index - offset)
        }
        shift += 7
    }
    error("Unexpected end of varint input")
}

/**
 * Raised when compare-and-swap metadata does not match the current record state.
 */
public class InvalidSwapException(
    public val expectedCid: String?,
    public val providedCid: String,
) : RepoException("Invalid swapRecord")
