package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

internal object ContentSyncSchemaDocs {
    val entries: Array<Pair<String, SchemaDoc>> =
        arrayOf(
            // ---- Contents -------------------------------------------------------------------------
            "ContentUploadRequest" to
                SchemaDoc(
                    "A whole entry, as sent to **Create or replace an entry**.",
                    mapOf(
                        "id" to "The entry's ID. Must equal the `contentId` in the path.",
                        "type" to "What kind of entry: `TEXT`, `IMAGE`, `VIDEO`, `AUDIO` or `HANDWRITING`.",
                        "content" to "The entry's text, or `null` for entries without text. $ENCRYPTED_BY_APPS",
                        "mediaUri" to "Reference to the entry's media (usually a `downloadUrl` from **Upload a media file**), or `null`.",
                        "durationMs" to "Length of an audio or video entry in milliseconds; `0` otherwise.",
                        "createdAt" to "When the entry was written on the device. $EPOCH_MS",
                        "lastUpdated" to "When the entry was last edited on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                        "photoPresentation" to "Photo layout: `EdgeToEdge` or `Framed`. Omit on updates to preserve the current layout.",
                        "caption" to "Caption for an image or video, or `null`. $ENCRYPTED_BY_APPS",
                        "location" to "Where the entry was written, or `null`. $ENCRYPTED_BY_APPS",
                        "transcript" to
                            "Structured audio transcript encrypted by the app; omitted values survive only while media is unchanged. $ENCRYPTED_BY_APPS",
                        "capturedOn" to "The device that captured the entry, or `null` when not recorded. $ENCRYPTED_BY_APPS",
                        "capturedDuring" to
                            "What the person was doing at capture, or `null` when not recorded. $ENCRYPTED_BY_APPS",
                    ),
                ),
            "ContentUploadResponse" to
                SchemaDoc(
                    "Confirmation that an entry was saved.",
                    mapOf(
                        "id" to "The entry's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "uploadedAt" to "When the server accepted the write. $EPOCH_MS",
                    ),
                ),
            "ContentUpdateRequest" to
                SchemaDoc(
                    "The fields of an entry to change, as sent to **Change part of an entry**. Omitted fields keep their values.",
                    mapOf(
                        "content" to "New text, or omit to keep the current text. $ENCRYPTED_BY_APPS",
                        "mediaUri" to "New media reference, or omit to keep the current one.",
                        "durationMs" to "New duration in milliseconds, or omit to keep the current duration. An explicit `0` is retained.",
                        "lastUpdated" to "When the edit was made on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                        "versionConstraint" to "Optional conflict check; see `VersionConstraint`.",
                        "photoPresentation" to "Photo layout: `EdgeToEdge` or `Framed`. Omit on updates to preserve the current layout.",
                        "caption" to "New caption, or omit to keep the current one. $ENCRYPTED_BY_APPS",
                        "location" to "New location, or omit to keep the current one. $ENCRYPTED_BY_APPS",
                        "transcript" to "Encrypted audio transcript; omit to preserve it while media is unchanged. $ENCRYPTED_BY_APPS",
                        "capturedOn" to "The device that captured the entry; omit it to leave it unchanged. $ENCRYPTED_BY_APPS",
                        "capturedDuring" to "What the person was doing at capture; omit it to leave it unchanged. $ENCRYPTED_BY_APPS",
                    ),
                ),
            "ContentUpdateResponse" to
                SchemaDoc(
                    "Confirmation that an entry was updated.",
                    mapOf(
                        "id" to "The entry's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "updatedAt" to "When the server applied the update. $EPOCH_MS",
                    ),
                ),
            "ContentChangesResponse" to
                SchemaDoc(
                    "One page of the entry change feed.",
                    mapOf(
                        "changes" to "Entries created or updated after `since`, oldest version first.",
                        "deletions" to "Entries deleted after `since`.",
                        "lastTimestamp" to CURSOR_LAST_TIMESTAMP,
                        "hasMore" to HAS_MORE,
                    ),
                ),
            "ContentChange" to
                SchemaDoc(
                    "An entry as the server holds it.",
                    mapOf(
                        "id" to "The entry's ID.",
                        "type" to "`TEXT`, `IMAGE`, `VIDEO`, `AUDIO` or `HANDWRITING`.",
                        "content" to "The entry's text, or `null`. $ENCRYPTED_BY_APPS",
                        "mediaUri" to "Reference to the entry's media, or `null`.",
                        "durationMs" to "Length of an audio or video entry in milliseconds; `0` otherwise.",
                        "createdAt" to "When the entry was written on the device. $EPOCH_MS",
                        "lastUpdated" to "The `lastUpdated` the client sent on its last write, stored as given. $EPOCH_MS",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "isDeleted" to IS_DELETED_ALWAYS_FALSE,
                        "photoPresentation" to "Photo layout: `EdgeToEdge` or `Framed`. Omit on updates to preserve the current layout.",
                        "caption" to "Caption for an image or video, or `null`. $ENCRYPTED_BY_APPS",
                        "location" to "Where the entry was written, or `null`. $ENCRYPTED_BY_APPS",
                        "transcript" to
                            "Structured audio transcript encrypted by the app; omitted values survive only while media is unchanged. $ENCRYPTED_BY_APPS",
                        "capturedOn" to "The device that captured the entry, or `null` when not recorded. $ENCRYPTED_BY_APPS",
                        "capturedDuring" to
                            "What the person was doing at capture, or `null` when not recorded. $ENCRYPTED_BY_APPS",
                    ),
                ),
            "ContentDeletion" to
                SchemaDoc(
                    "A tombstone: an entry that was deleted.",
                    mapOf(
                        "id" to "The deleted entry's ID.",
                        "deletedAt" to "When it was deleted. $EPOCH_MS",
                        "serverVersion" to DELETION_VERSION,
                    ),
                ),
        )
}
