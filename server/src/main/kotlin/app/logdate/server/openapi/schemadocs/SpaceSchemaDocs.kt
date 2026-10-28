package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

private const val SPACE_FIELD = "Your personal space's URI: `at://{your DID}/space/app.logdate.space.personal/self`."
private const val RKEY_FIELD =
    "The sealed record's key. It's derived from the inner record's key with a secret, so the server can't link them."
private const val SEQ_FIELD = "The sequence number of this write. Each write in the space gets the next one."

/** Prose for the `app.logdate.space.*` schemas. */
internal object SpaceSchemaDocs {
    val docs: Map<String, SchemaDoc> =
        mapOf(
            "SpacePutRecordInput" to
                SchemaDoc(
                    "Body of `app.logdate.space.putRecord`.",
                    mapOf(
                        "space" to SPACE_FIELD,
                        "rkey" to RKEY_FIELD,
                        "record" to "An `app.logdate.crypto.sealed` envelope (see `Sealed`). Fields it doesn't define are refused.",
                        "blobs" to "CIDs of the space blobs the record refers to, at most 64. Each must already be uploaded.",
                        "swapCid" to "Only write if the current record's CID equals this; otherwise `InvalidSwap`.",
                        "createOnly" to "Only write if no live record has this key; otherwise `InvalidSwap`.",
                    ),
                ),
            "SpaceDeleteRecordInput" to
                SchemaDoc(
                    "Body of `app.logdate.space.deleteRecord`.",
                    mapOf(
                        "space" to SPACE_FIELD,
                        "rkey" to RKEY_FIELD,
                        "swapCid" to "Only delete if the current record's CID equals this; otherwise `InvalidSwap`.",
                    ),
                ),
            "PutRecordOutput" to
                SchemaDoc(
                    "What `app.logdate.space.putRecord` stored.",
                    mapOf(
                        "cid" to "The stored record's CID. Send it as `swapCid` to change the record only if nobody else has.",
                        "seq" to SEQ_FIELD,
                    ),
                ),
            "DeleteRecordOutput" to
                SchemaDoc(
                    "What `app.logdate.space.deleteRecord` did.",
                    mapOf("seq" to "The deletion's sequence number, or the latest one when there was nothing to delete."),
                ),
            "GetRecordOutput" to
                SchemaDoc(
                    "One sealed record from a personal space.",
                    mapOf(
                        "cid" to "The record's CID.",
                        "seq" to "The sequence number of the record's latest write.",
                        "value" to "The sealed envelope.",
                    ),
                ),
            "ListRepoOpsOutput" to
                SchemaDoc(
                    "A page of changes to a personal space, oldest first.",
                    mapOf(
                        "cursor" to "Send this as `since` to get the next page.",
                        "hasMore" to "Whether there are more changes after this page.",
                        "ops" to "The latest state of each key that changed after `since`.",
                    ),
                ),
            "ListRepoOpsChange" to
                SchemaDoc(
                    "The latest state of one key: a stored record, or a deletion with no `cid` or `value`.",
                    mapOf(
                        "cid" to "The record's CID; absent for a deletion.",
                        "deleted" to "Whether the record was deleted.",
                        "rkey" to "The sealed record's key.",
                        "seq" to "The sequence number of this change.",
                        "value" to "The sealed envelope; absent for a deletion.",
                    ),
                ),
            "BlobUploadReceiptLookup" to
                SchemaDoc(
                    "Metadata-only acknowledgement lookup for an opaque upload identity in your own space.",
                    mapOf(
                        "supported" to "True when this server implements durable upload identities; never inferred from a missing blob.",
                        "receipt" to "Committed blob metadata, absent when this identity has not committed. No payload is returned.",
                    ),
                ),
            "BlobUploadReceipt" to
                SchemaDoc(
                    "The immutable encrypted blob already committed for this upload identity.",
                    mapOf(
                        "cid" to "Content identifier of the stored ciphertext; keep it in the sealed record.",
                        "sizeBytes" to "Exact encrypted payload size, checked before adopting the acknowledgement.",
                    ),
                ),
            "UploadBlobOutput" to
                SchemaDoc(
                    "What `app.logdate.space.uploadBlob` stored.",
                    mapOf(
                        "blob" to "The blob reference to keep inside a sealed record.",
                    ),
                ),
            "Sealed" to
                SchemaDoc(
                    "An `app.logdate.crypto.sealed` envelope: a record encrypted on the device, padded to a size bucket so its " +
                        "size doesn't reveal what kind of record it is.",
                    mapOf(
                        "\$type" to "Always `app.logdate.crypto.sealed`.",
                        "ciphertext" to "The encrypted, padded record, as `{\"\$bytes\": \"<base64>\"}`.",
                        "keyId" to "Which of the owner's content keys encrypted it.",
                        "nonce" to "The 12-byte nonce, as `{\"\$bytes\": \"<base64>\"}`.",
                        "version" to "The envelope format version; `1`.",
                    ),
                ),
            "Blob" to
                SchemaDoc(
                    "A reference to an uploaded file: `{\"\$type\": \"blob\", \"ref\": {\"\$link\": \"<cid>\"}, " +
                        "\"mimeType\", \"size\"}`. In a personal space the file is ciphertext.",
                ),
            "Bytes" to SchemaDoc("Raw bytes, written as `{\"\$bytes\": \"<base64>\"}`."),
        )
}
