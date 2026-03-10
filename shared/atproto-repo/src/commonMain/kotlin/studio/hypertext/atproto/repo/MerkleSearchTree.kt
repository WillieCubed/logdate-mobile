package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
 * Small deterministic MST-like record index used by the standalone repo runtime.
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
    public fun rootCid(): Cid = Cid.sha256(DAG_CBOR_CODEC, DagCborCodec.encode(toJsonElement()))

    /**
     * Encodes the tree to a deterministic JSON element.
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
                    MerkleSearchTreeKey(
                        collection = Nsid.require(entry.getValue("c").jsonPrimitive.content),
                        recordKey = RecordKey.require(entry.getValue("k").jsonPrimitive.content),
                    ) to Cid.require(entry.getValue("v").jsonPrimitive.content)
                }
            return MerkleSearchTree(entries)
        }
    }
}

internal data class MerkleSearchTreeKey(
    val collection: Nsid,
    val recordKey: RecordKey,
)
