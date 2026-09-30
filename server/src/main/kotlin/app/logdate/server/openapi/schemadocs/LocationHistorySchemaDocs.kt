package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

internal object LocationHistorySchemaDocs {
    private val recordFields =
        mapOf(
            "id" to
                "Opaque record ID, unique within the account; 1 to 128 characters. Keep all sensitive data in the payload.",
            "recordType" to "One of observation, activity, place, correction, memory-link or manual.",
            "payload" to
                "Opaque client-encrypted LDSE2 ciphertext for a live record; null for a tombstone. Live values contain 7 to 65536 characters.",
            "payloadSchemaVersion" to "Positive encrypted-payload schema version. Current clients use 1.",
            "deviceId" to "Opaque ID of the device that made this edit; 1 to 128 characters.",
            "deviceVersion" to
                "Positive version identifying an immutable edit from this device. Increase it for each edit; retries retain it.",
            "deleted" to "True for a permanent tombstone. Deleted records cannot be revived with the same ID.",
        )

    val docs =
        mapOf(
            "LocationHistoryUpload" to
                SchemaDoc(
                    "One encrypted record or tombstone submitted in an atomic history batch.",
                    recordFields +
                        (
                            "expectedServerVersion" to
                                "Last observed version, or 0 for a new ID. Mismatches reject the whole batch with 409."
                        ),
                ),
            "LocationHistoryRecord" to
                SchemaDoc(
                    "The latest encrypted value or permanent tombstone held by the server for an account and record ID.",
                    recordFields +
                        (
                            "serverVersion" to
                                "Increasing account-scoped server version; not a timestamp."
                        ),
                ),
            "LocationHistoryBatchRequest" to
                SchemaDoc(
                    "An atomic request containing 1 to 100 unique record IDs and no more than 7,000,000 bytes of JSON.",
                    mapOf(
                        "records" to
                            "Encrypted records or tombstones. Invalid or conflicting records reject the whole batch.",
                    ),
                ),
            "LocationHistoryBatchResponse" to
                SchemaDoc(
                    "The accepted batch, including assigned server versions or the original versions for idempotent retries.",
                    mapOf("records" to "One accepted record for each ID in the request."),
                ),
            "LocationHistoryChangesResponse" to
                SchemaDoc(
                    "A page from this account's encrypted location history feed, including tombstones.",
                    mapOf(
                        "records" to "Latest records with serverVersion greater than since, in ascending version order.",
                        "nextCursor" to
                            "Highest page version, or since when empty. Persist after decrypting and applying the whole page.",
                        "hasMore" to "True when another page is available after nextCursor.",
                    ),
                ),
        )
}
