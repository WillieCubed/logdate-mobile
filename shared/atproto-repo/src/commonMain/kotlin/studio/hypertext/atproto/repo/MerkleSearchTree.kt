package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey

/**
 * Deterministic sorted record index used as the repo root snapshot.
 */
public data class MerkleSearchTreeEntry(
    val collection: Nsid,
    val recordKey: RecordKey,
    val cid: Cid,
)

/**
 * Small deterministic MST-backed record index used by the standalone repo runtime.
 */
public class MerkleSearchTree private constructor(
    private val entriesByKey: Map<MerkleSearchTreeKey, Cid>,
) {
    /**
     * Returns the CID for [collection] and [recordKey], if present.
     */
    public fun get(
        collection: Nsid,
        recordKey: RecordKey,
    ): Cid? = entriesByKey[MerkleSearchTreeKey(collection = collection, recordKey = recordKey)]

    /**
     * Returns a new tree with [collection] and [recordKey] mapped to [cid].
     */
    public fun put(
        collection: Nsid,
        recordKey: RecordKey,
        cid: Cid,
    ): MerkleSearchTree =
        MerkleSearchTree(
            entriesByKey + (MerkleSearchTreeKey(collection = collection, recordKey = recordKey) to cid),
        )

    /**
     * Returns a new tree without [collection] and [recordKey].
     */
    public fun remove(
        collection: Nsid,
        recordKey: RecordKey,
    ): MerkleSearchTree = MerkleSearchTree(entriesByKey - MerkleSearchTreeKey(collection = collection, recordKey = recordKey))

    /**
     * Returns the entries in deterministic collection and key order.
     */
    public fun entries(collection: Nsid? = null): List<MerkleSearchTreeEntry> =
        entriesByKey.entries
            .asSequence()
            .filter { collection == null || it.key.collection == collection }
            .sortedWith(
                compareBy<Map.Entry<MerkleSearchTreeKey, Cid>>(
                    { it.key.collection.toString() },
                    { it.key.recordKey.toString() },
                ),
            ).map { (key, cid) ->
                MerkleSearchTreeEntry(collection = key.collection, recordKey = key.recordKey, cid = cid)
            }.toList()

    /**
     * Returns the deterministic root CID for the current tree.
     */
    public fun rootCid(): Cid = toBlockGraph().root

    /**
     * Encodes the flattened record map to a deterministic JSON element.
     *
     * This is a compatibility view for tests and debugging. Repo persistence now
     * uses real MST node blocks instead of this flattened shape.
     */
    public fun toJsonElement(): JsonArray =
        buildJsonArray {
            entries().forEach { entry ->
                add(
                    buildJsonObject {
                        put("c", entry.collection.toString())
                        put("k", entry.recordKey.toString())
                        put("v", entry.cid.toString())
                    },
                )
            }
        }

    internal fun toBlockGraph(): MstBlockGraph {
        val leaves =
            entries().map { entry ->
                val key = entry.storageKey()
                MstLeaf(
                    key = key,
                    value = entry.cid,
                    layer = leadingZerosOnHash(key),
                )
            }
        val rootNode = buildRootNode(leaves)
        val blocksByCid = linkedMapOf<Cid, RepoBlock>()
        val root = serializeNode(rootNode, blocksByCid)
        return MstBlockGraph(root = root, blocks = blocksByCid.values.toList())
    }

    public companion object {
        /**
         * Returns an empty tree.
         */
        public fun empty(): MerkleSearchTree = MerkleSearchTree(emptyMap())

        /**
         * Decodes a flattened compatibility view from [element].
         */
        public fun fromJsonElement(element: JsonArray): MerkleSearchTree {
            val entries =
                element.associate { encodedEntry ->
                    val entry = encodedEntry.jsonObject
                    MerkleSearchTreeKey(
                        collection = Nsid.require(entry.getValue("c").jsonPrimitive.content),
                        recordKey = RecordKey.require(entry.getValue("k").jsonPrimitive.content),
                    ) to Cid.require(entry.getValue("v").jsonPrimitive.content)
                }
            return MerkleSearchTree(entries)
        }

        internal suspend fun fromBlocks(
            root: Cid,
            readBlock: suspend (Cid) -> RepoBlock?,
        ): MerkleSearchTree {
            val entries = linkedMapOf<MerkleSearchTreeKey, Cid>()
            collectEntries(root, readBlock, entries)
            return MerkleSearchTree(entries)
        }

        private suspend fun collectEntries(
            nodeCid: Cid,
            readBlock: suspend (Cid) -> RepoBlock?,
            entries: MutableMap<MerkleSearchTreeKey, Cid>,
        ) {
            val nodeBlock = requireNotNull(readBlock(nodeCid)) { "Missing MST block $nodeCid" }
            val node = DagCborCodec.decode(nodeBlock.bytes).jsonObject
            DagCborCodec.linkOrNull(node["l"])?.let { left ->
                collectEntries(left, readBlock, entries)
            }

            var lastKey = ""
            node.getValue("e").jsonArray.forEach { encodedEntry ->
                val entry = encodedEntry.jsonObject
                val prefixLength = entry.getValue("p").jsonPrimitive.int
                val suffix =
                    requireNotNull(DagCborCodec.bytesOrNull(entry["k"])) { "MST key suffix must be bytes" }
                        .decodeToString()
                val key = lastKey.take(prefixLength) + suffix
                val value = requireNotNull(DagCborCodec.linkOrNull(entry["v"])) { "MST leaf value must be a CID link" }
                entries[parseStorageKey(key)] = value
                DagCborCodec.linkOrNull(entry["t"])?.let { subtree ->
                    collectEntries(subtree, readBlock, entries)
                }
                lastKey = key
            }
        }

        private fun parseStorageKey(key: String): MerkleSearchTreeKey {
            val parts = key.split('/', limit = 2)
            require(parts.size == 2) { "Invalid MST storage key: $key" }
            return MerkleSearchTreeKey(
                collection = Nsid.require(parts[0]),
                recordKey = RecordKey.require(parts[1]),
            )
        }

        private fun buildRootNode(leaves: List<MstLeaf>): MstNode {
            if (leaves.isEmpty()) {
                return MstNode(layer = 0, entries = emptyList())
            }
            val rootLayer = leaves.maxOf(MstLeaf::layer)
            return buildNode(leaves, rootLayer)
        }

        private fun buildNode(
            leaves: List<MstLeaf>,
            layer: Int,
        ): MstNode {
            if (leaves.isEmpty()) {
                return MstNode(layer = layer, entries = emptyList())
            }
            val entries = mutableListOf<MstNodeEntry>()
            var index = 0
            while (index < leaves.size) {
                val segmentStart = index
                while (index < leaves.size && leaves[index].layer < layer) {
                    index += 1
                }
                if (segmentStart < index) {
                    entries += MstSubtree(buildChildSubtree(leaves.subList(segmentStart, index), layer))
                }
                if (index < leaves.size) {
                    val leaf = leaves[index]
                    require(leaf.layer == layer) { "MST layer mismatch for key ${leaf.key}" }
                    entries += MstLeafEntry(key = leaf.key, value = leaf.value)
                    index += 1
                }
            }
            return MstNode(layer = layer, entries = entries)
        }

        private fun buildChildSubtree(
            leaves: List<MstLeaf>,
            parentLayer: Int,
        ): MstNode {
            val childLayer = parentLayer - 1
            val segmentLayer = leaves.maxOf(MstLeaf::layer)
            var node = buildNode(leaves, segmentLayer)
            while (node.layer < childLayer) {
                node = MstNode(layer = node.layer + 1, entries = listOf(MstSubtree(node)))
            }
            return node
        }

        private fun serializeNode(
            node: MstNode,
            blocksByCid: MutableMap<Cid, RepoBlock>,
        ): Cid {
            val entries = node.entries
            var leftCid: Cid? = null
            var index = 0
            if (entries.firstOrNull() is MstSubtree) {
                leftCid = serializeNode((entries.first() as MstSubtree).node, blocksByCid)
                index = 1
            }

            var lastKey = ""
            val encodedEntries =
                buildJsonArray {
                    while (index < entries.size) {
                        val leaf = entries[index] as? MstLeafEntry ?: error("MST nodes cannot contain adjacent subtrees")
                        val rightCid =
                            (entries.getOrNull(index + 1) as? MstSubtree)
                                ?.let { subtree -> serializeNode(subtree.node, blocksByCid) }
                        val prefixLength = countPrefixLength(lastKey, leaf.key)
                        val suffix = leaf.key.drop(prefixLength).encodeToByteArray()
                        add(
                            buildJsonObject {
                                put("p", prefixLength)
                                put("k", DagCborCodec.bytes(suffix))
                                put("v", DagCborCodec.link(leaf.value))
                                put("t", rightCid?.let(DagCborCodec::link) ?: JsonNull)
                            },
                        )
                        lastKey = leaf.key
                        index += if (rightCid != null) 2 else 1
                    }
                }

            val encodedNode =
                buildJsonObject {
                    put("l", leftCid?.let(DagCborCodec::link) ?: JsonNull)
                    put("e", encodedEntries)
                }
            val bytes = DagCborCodec.encode(encodedNode)
            val cid = Cid.sha256(DAG_CBOR_CODEC, bytes)
            blocksByCid[cid] = RepoBlock(cid = cid, bytes = bytes)
            return cid
        }

        private fun countPrefixLength(
            previous: String,
            current: String,
        ): Int {
            val maxPrefix = minOf(previous.length, current.length)
            for (index in 0 until maxPrefix) {
                if (previous[index] != current[index]) {
                    return index
                }
            }
            return maxPrefix
        }

        private fun leadingZerosOnHash(key: String): Int {
            val hash =
                key
                    .encodeToByteArray()
                    .toByteString()
                    .sha256()
                    .toByteArray()
            var leadingZeros = 0
            hash.forEach { byte ->
                val unsignedByte = byte.toInt() and 0xff
                if (unsignedByte < 64) {
                    leadingZeros += 1
                } else {
                    return leadingZeros
                }
                if (unsignedByte < 16) {
                    leadingZeros += 1
                } else {
                    return leadingZeros
                }
                if (unsignedByte < 4) {
                    leadingZeros += 1
                } else {
                    return leadingZeros
                }
                if (unsignedByte == 0) {
                    leadingZeros += 1
                } else {
                    return leadingZeros
                }
            }
            return leadingZeros
        }
    }
}

internal data class MerkleSearchTreeKey(
    val collection: Nsid,
    val recordKey: RecordKey,
)

internal data class MstBlockGraph(
    val root: Cid,
    val blocks: List<RepoBlock>,
)

private data class MstLeaf(
    val key: String,
    val value: Cid,
    val layer: Int,
)

private data class MstNode(
    val layer: Int,
    val entries: List<MstNodeEntry>,
)

private sealed interface MstNodeEntry

private data class MstSubtree(
    val node: MstNode,
) : MstNodeEntry

private data class MstLeafEntry(
    val key: String,
    val value: Cid,
) : MstNodeEntry

private fun MerkleSearchTreeEntry.storageKey(): String = "$collection/$recordKey"
