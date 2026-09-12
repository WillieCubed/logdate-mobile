package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

private const val EPOCH_MS = "Milliseconds since the Unix epoch, UTC."
private const val DEVICE_ID = "Which device made the change. Any stable string; the apps use a per-install ID. Defaults to `unknown`."
private const val SERVER_VERSION_ASSIGNED =
    "The version the server assigned to this write. Versions only ever increase per account; use them as `since` cursors."
private const val SYNC_VERSION_IGNORED = "Ignored. The server assigns versions itself; kept for older clients."
private const val CURSOR_LAST_TIMESTAMP =
    "The highest version in this page, or the `since` you sent if the page is empty. Send it as the next `since`. " +
        "Despite the name it is a version, not a clock time."
private const val HAS_MORE = "`true` when more changes exist beyond this page; call again with `since` = `lastTimestamp`."
private const val IS_DELETED_ALWAYS_FALSE = "Always `false`. Deletions are delivered in the `deletions` list, not here."
private const val ENCRYPTED_BY_APPS = "The LogDate apps send this encrypted; the server never reads it."

/** Prose for the contents, journals, associations, drafts, media, backups and sync-status schemas. */
internal object SyncSchemaDocs {
    val docs: Map<String, SchemaDoc> =
        mapOf(
            // ---- Envelopes ----------------------------------------------------------------------
            "SimpleErrorResponse" to
                SchemaDoc(
                    "The error envelope used by every sync endpoint (contents, journals, associations, drafts, media, backups, sync status).",
                    mapOf(
                        "code" to "Stable, upper-snake-case code to branch on, such as `CONFLICT`. Never localized.",
                        "message" to "A sentence for logs and developers. May change between releases; do not parse it.",
                        "details" to
                            "Extra machine-readable context for some codes, for example `retryAfterSeconds` on `RATE_LIMIT_EXCEEDED`. Often empty.",
                        "timestamp" to "When the error was produced. ISO-8601, UTC.",
                    ),
                ),
            "SimpleSuccessResponse_SyncStatusSnapshot" to
                SchemaDoc(
                    "The generic success envelope around a sync-status payload.",
                    mapOf(
                        "data" to "The payload.",
                        "message" to "Always `Success`.",
                        "timestamp" to "When the response was produced. ISO-8601, UTC.",
                    ),
                ),
            "SyncStatusSnapshot" to
                SchemaDoc(
                    "Totals for one account's synced data.",
                    mapOf(
                        "contentCount" to "Entries the server holds, excluding deleted ones.",
                        "journalCount" to "Journals the server holds, excluding deleted ones.",
                        "associationCount" to "Entry-to-journal links the server holds, excluding removed ones.",
                        "lastTimestamp" to
                            "The newest version the server has assigned for this account. If it is higher than your stored cursor, there is something to fetch.",
                    ),
                ),
            // ---- Versioning ---------------------------------------------------------------------
            "VersionConstraint" to
                SchemaDoc(
                    "What a `PATCH` expects the server's current version to be. Send `known` to detect conflicts, `none` (or omit) for last-write-wins.",
                ),
            "VersionConstraint.Known" to
                SchemaDoc(
                    "Ask the server to reject the update if it holds a newer version than the one you last saw.",
                    mapOf(
                        "type" to "Always `known`.",
                        "serverVersion" to
                            "The `serverVersion` you last received for this record. If the server's is higher, the update answers `409 CONFLICT`.",
                    ),
                ),
            "VersionConstraint.None" to
                SchemaDoc("No check: the update always applies, even over newer changes.", mapOf("type" to "Always `none`.")),
            // ---- Contents -------------------------------------------------------------------------
            "ContentUploadRequest" to
                SchemaDoc(
                    "A whole entry, as sent to **Create or replace an entry**.",
                    mapOf(
                        "id" to "The entry's ID. Must equal the `contentId` in the path.",
                        "type" to "What kind of entry: `TEXT`, `IMAGE`, `VIDEO` or `AUDIO`.",
                        "content" to "The entry's text, or `null` for entries without text. $ENCRYPTED_BY_APPS",
                        "mediaUri" to "Reference to the entry's media (usually a `downloadUrl` from **Upload a media file**), or `null`.",
                        "durationMs" to "Length of an audio or video entry in milliseconds; `0` otherwise.",
                        "createdAt" to "When the entry was written on the device. $EPOCH_MS",
                        "lastUpdated" to "When the entry was last edited on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                        "caption" to "Caption for an image or video, or `null`. $ENCRYPTED_BY_APPS",
                        "location" to "Where the entry was written, or `null`. $ENCRYPTED_BY_APPS",
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
                        "durationMs" to "New duration in milliseconds. Note: defaults to `0` when omitted.",
                        "lastUpdated" to "When the edit was made on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                        "versionConstraint" to "Optional conflict check; see `VersionConstraint`.",
                        "caption" to "New caption, or omit to keep the current one. $ENCRYPTED_BY_APPS",
                        "location" to "New location, or omit to keep the current one. $ENCRYPTED_BY_APPS",
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
                        "type" to "`TEXT`, `IMAGE`, `VIDEO` or `AUDIO`.",
                        "content" to "The entry's text, or `null`. $ENCRYPTED_BY_APPS",
                        "mediaUri" to "Reference to the entry's media, or `null`.",
                        "durationMs" to "Length of an audio or video entry in milliseconds; `0` otherwise.",
                        "createdAt" to "When the entry was written on the device. $EPOCH_MS",
                        "lastUpdated" to "The `lastUpdated` the client sent on its last write, stored as given. $EPOCH_MS",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "isDeleted" to IS_DELETED_ALWAYS_FALSE,
                        "caption" to "Caption for an image or video, or `null`. $ENCRYPTED_BY_APPS",
                        "location" to "Where the entry was written, or `null`. $ENCRYPTED_BY_APPS",
                    ),
                ),
            "ContentDeletion" to
                SchemaDoc(
                    "A tombstone: an entry that was deleted.",
                    mapOf("id" to "The deleted entry's ID.", "deletedAt" to "When it was deleted. $EPOCH_MS"),
                ),
            // ---- Journals -------------------------------------------------------------------------
            "JournalUploadRequest" to
                SchemaDoc(
                    "A whole journal, as sent to **Create or replace a journal**.",
                    mapOf(
                        "id" to "The journal's ID. Must equal the `journalId` in the path.",
                        "title" to "The journal's name.",
                        "description" to "A longer description; may be empty.",
                        "createdAt" to "When the journal was created on the device. $EPOCH_MS",
                        "lastUpdated" to "When it was last edited on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                    ),
                ),
            "JournalUploadResponse" to
                SchemaDoc(
                    "Confirmation that a journal was saved.",
                    mapOf(
                        "id" to "The journal's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "uploadedAt" to "When the server accepted the write. $EPOCH_MS",
                    ),
                ),
            "JournalUpdateRequest" to
                SchemaDoc(
                    "The fields of a journal to change. Omitted fields keep their values.",
                    mapOf(
                        "title" to "New name, or omit to keep the current one.",
                        "description" to "New description, or omit to keep the current one.",
                        "lastUpdated" to "When the edit was made on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                        "versionConstraint" to "Optional conflict check; see `VersionConstraint`.",
                    ),
                ),
            "JournalUpdateResponse" to
                SchemaDoc(
                    "Confirmation that a journal was updated.",
                    mapOf(
                        "id" to "The journal's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "updatedAt" to "When the server applied the update. $EPOCH_MS",
                    ),
                ),
            "JournalChangesResponse" to
                SchemaDoc(
                    "One page of the journal change feed.",
                    mapOf(
                        "changes" to "Journals created or updated after `since`, oldest version first.",
                        "deletions" to "Journals deleted after `since`.",
                        "lastTimestamp" to CURSOR_LAST_TIMESTAMP,
                        "hasMore" to HAS_MORE,
                    ),
                ),
            "JournalChange" to
                SchemaDoc(
                    "A journal as the server holds it.",
                    mapOf(
                        "id" to "The journal's ID.",
                        "title" to "The journal's name.",
                        "description" to "Its description; may be empty.",
                        "createdAt" to "When it was created on the device. $EPOCH_MS",
                        "lastUpdated" to "The `lastUpdated` the client sent on its last write, stored as given. $EPOCH_MS",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "isDeleted" to IS_DELETED_ALWAYS_FALSE,
                    ),
                ),
            "JournalDeletion" to
                SchemaDoc(
                    "A tombstone: a journal that was deleted.",
                    mapOf("id" to "The deleted journal's ID.", "deletedAt" to "When it was deleted. $EPOCH_MS"),
                ),
            // ---- Associations ---------------------------------------------------------------------
            "AssociationUploadRequest" to SchemaDoc("Links to create or refresh in bulk.", mapOf("associations" to "The links.")),
            "Association" to
                SchemaDoc(
                    "A link between one entry and one journal. The pair `(journalId, contentId)` is its identity.",
                    mapOf(
                        "journalId" to "The journal's ID.",
                        "contentId" to "The entry's ID.",
                        "createdAt" to "When the link was made on the device. $EPOCH_MS",
                        "syncVersion" to SYNC_VERSION_IGNORED,
                        "deviceId" to DEVICE_ID,
                    ),
                ),
            "AssociationLinkUpsertRequest" to
                SchemaDoc(
                    "Details for a single link whose journal and entry are named in the path.",
                    mapOf("createdAt" to "When the link was made on the device. $EPOCH_MS", "deviceId" to DEVICE_ID),
                ),
            "AssociationUploadResponse" to
                SchemaDoc(
                    "Confirmation that links were saved.",
                    mapOf(
                        "uploadedCount" to "How many links were in the request.",
                        "uploadedAt" to "When the server accepted them. $EPOCH_MS",
                    ),
                ),
            "AssociationChangesResponse" to
                SchemaDoc(
                    "One page of the link change feed.",
                    mapOf(
                        "changes" to "Links created or refreshed after `since`, oldest version first.",
                        "deletions" to "Links removed after `since`.",
                        "lastTimestamp" to CURSOR_LAST_TIMESTAMP,
                        "hasMore" to HAS_MORE,
                    ),
                ),
            "AssociationChange" to
                SchemaDoc(
                    "A link as the server holds it.",
                    mapOf(
                        "journalId" to "The journal's ID.",
                        "contentId" to "The entry's ID.",
                        "createdAt" to "When the link was made on the device. $EPOCH_MS",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "isDeleted" to IS_DELETED_ALWAYS_FALSE,
                    ),
                ),
            "AssociationDeletion" to
                SchemaDoc(
                    "A tombstone: a link that was removed.",
                    mapOf(
                        "journalId" to "The journal's ID.",
                        "contentId" to "The entry's ID.",
                        "deletedAt" to "When the link was removed. $EPOCH_MS",
                    ),
                ),
            "AssociationDeleteRequest" to
                SchemaDoc("Links to remove in bulk.", mapOf("associations" to "The links, by journal and entry ID.")),
            "AssociationDeleteItem" to
                SchemaDoc("One link to remove.", mapOf("journalId" to "The journal's ID.", "contentId" to "The entry's ID.")),
            // ---- Drafts ---------------------------------------------------------------------------
            "DraftUploadRequest" to
                SchemaDoc(
                    "An in-progress entry, as sent to **Save a draft**.",
                    mapOf(
                        "id" to "The draft's ID. Should equal the `draftId` in the path.",
                        "content" to "The draft's text so far.",
                        "blockTypes" to "The kinds of block in the draft, in order, such as `TEXT` or `IMAGE`.",
                        "journalIds" to "Journals the finished entry will belong to.",
                        "createdAt" to "When the draft was started on the device. $EPOCH_MS",
                        "lastUpdated" to "When it was last edited on the device. $EPOCH_MS",
                        "deviceId" to DEVICE_ID,
                    ),
                ),
            "DraftUploadResponse" to
                SchemaDoc(
                    "Confirmation that a draft was saved.",
                    mapOf(
                        "id" to "The draft's ID.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "uploadedAt" to "When the server accepted the write. $EPOCH_MS",
                    ),
                ),
            "DraftChangesResponse" to
                SchemaDoc(
                    "Drafts changed since a cursor. Unlike the other feeds there is no `hasMore` or `lastTimestamp`; use the highest `serverVersion` in `drafts` as your next `since`.",
                    mapOf(
                        "drafts" to "Drafts created or updated after `since`. Deleted drafts are omitted, not flagged.",
                        "cursor" to "Reserved; currently always absent.",
                    ),
                ),
            "DraftChange" to
                SchemaDoc(
                    "A draft as the server holds it.",
                    mapOf(
                        "id" to "The draft's ID.",
                        "content" to "The draft's text so far.",
                        "blockTypes" to "The kinds of block in the draft, in order.",
                        "journalIds" to "Journals the finished entry will belong to.",
                        "createdAt" to "When the draft was started on the device. $EPOCH_MS",
                        "lastUpdated" to "When it was last edited on the device. $EPOCH_MS",
                        "deviceId" to "Which device last saved it.",
                        "serverVersion" to SERVER_VERSION_ASSIGNED,
                        "is_deleted" to "Always `false` today; deleted drafts are omitted from the feed rather than flagged.",
                    ),
                ),
            // ---- Media ----------------------------------------------------------------------------
            "MediaUploadResponse" to
                SchemaDoc(
                    "Confirmation that a media file was stored.",
                    mapOf(
                        "contentId" to "The entry the file belongs to.",
                        "mediaId" to "The file's ID, derived from the account, entry and file name.",
                        "downloadUrl" to
                            "Where to fetch the bytes: a short-lived signed storage URL or this API's download endpoint. Treat as opaque.",
                        "uploadedAt" to "When the server stored the file. $EPOCH_MS",
                    ),
                ),
            "MediaMetadataResponse" to
                SchemaDoc(
                    "What the server knows about a media file.",
                    mapOf(
                        "contentId" to "The entry the file belongs to.",
                        "mediaId" to "The file's ID.",
                        "fileName" to "The original file name given at upload.",
                        "mimeType" to "The media type given at upload, such as `image/jpeg`.",
                        "sizeBytes" to "The file's size in bytes.",
                        "downloadUrl" to "Where to fetch the bytes. Treat as opaque; it may be short-lived.",
                        "uploadedAt" to "When the server stored the file. $EPOCH_MS",
                    ),
                ),
            // ---- Backups ----------------------------------------------------------------------------
            "BackupUploadResponse" to
                SchemaDoc(
                    "Confirmation that a backup was stored.",
                    mapOf(
                        "id" to "The backup's ID, a UUID.",
                        "createdAt" to "When the server stored it. $EPOCH_MS",
                        "sizeBytes" to "Size of the archive in bytes, as counted against the quota.",
                    ),
                ),
            "BackupInfoResponse" to
                SchemaDoc(
                    "One backup's record.",
                    mapOf(
                        "id" to "The backup's ID, a UUID.",
                        "deviceId" to "Which device made it.",
                        "manifest" to "The JSON manifest given at upload, verbatim.",
                        "createdAt" to "When the server stored it. $EPOCH_MS",
                        "sizeBytes" to "Size of the archive in bytes.",
                        "downloadUrl" to "Where to fetch the archive. Treat as opaque; it may be short-lived.",
                    ),
                ),
            "BackupListResponse" to SchemaDoc("The account's backups.", mapOf("backups" to "One record per backup.")),
        )
}
