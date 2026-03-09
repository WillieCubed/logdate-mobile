package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.syntax.RecordKey

/**
 * Deterministic sorted record index used as the repo root snapshot.
 */
public data class MerkleSearchTreeEntry(
    val recordKey: RecordKey,
    val cid: Cid,
)

/**
 * Small deterministic MST-like record index used by the standalone repo runtime.
 */
public class MerkleSearchTree private constructor(
    private val entriesByKey: Map<RecordKey, Cid>,
) {
    /**
     * Returns the CID for [recordKey], if present.
     */
    public fun get(recordKey: RecordKey): Cid? = entriesByKey[recordKey]

    /**
     * Returns a new tree with [recordKey] mapped to [cid].
     */
    public fun put(
        recordKey: RecordKey,
        cid: Cid,
    ): MerkleSearchTree = MerkleSearchTree(entriesByKey + (recordKey to cid))

    /**
     * Returns a new tree without [recordKey].
     */
    public fun remove(recordKey: RecordKey): MerkleSearchTree = MerkleSearchTree(entriesByKey - recordKey)

    /**
     * Returns the entries in deterministic key order.
     */
    public fun entries(): List<MerkleSearchTreeEntry> =
        entriesByKey.entries
            .sortedBy { it.key.toString() }
            .map { (key, cid) -> MerkleSearchTreeEntry(recordKey = key, cid = cid) }

    /**
     * Returns the deterministic root CID for the current tree.
     */
    public fun rootCid(): Cid = Cid.sha256(DAG_CBOR_CODEC, DagCborCodec.encode(toJsonElement()))

    /**
     * Encodes the tree to a deterministic JSON element.
     */
    public fun toJsonElement(): JsonArray =
        buildJsonArray {
            entries().forEach { entry ->
                add(
                    buildJsonObject {
                        put("k", entry.recordKey.toString())
                        put("v", entry.cid.toString())
                    },
                )
            }
        }

    public companion object {
        /**
         * Returns an empty tree.
         */
        public fun empty(): MerkleSearchTree = MerkleSearchTree(emptyMap())

        /**
         * Decodes a tree from [element].
         */
        public fun fromJsonElement(element: JsonArray): MerkleSearchTree {
            val entries =
                element.associate { encodedEntry ->
                    val entry = encodedEntry.jsonObject
                    RecordKey.require(entry.getValue("k").jsonPrimitive.content) to Cid.require(entry.getValue("v").jsonPrimitive.content)
                }
            return MerkleSearchTree(entries)
        }
    }
}
