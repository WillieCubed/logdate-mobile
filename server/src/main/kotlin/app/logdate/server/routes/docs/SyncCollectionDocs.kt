package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.created
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.sinceAndLimit
import app.logdate.server.routes.sync.AssociationLinkUpsertRequest
import app.logdate.server.routes.sync.DEFAULT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.DRAFT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MAX_SYNC_PAGE_SIZE
import app.logdate.server.routes.syncError
import app.logdate.shared.model.sync.Association
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeleteItem
import app.logdate.shared.model.sync.AssociationDeleteRequest
import app.logdate.shared.model.sync.AssociationDeletion
import app.logdate.shared.model.sync.AssociationUploadRequest
import app.logdate.shared.model.sync.AssociationUploadResponse
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.ContentDeletion
import app.logdate.shared.model.sync.ContentUpdateRequest
import app.logdate.shared.model.sync.ContentUpdateResponse
import app.logdate.shared.model.sync.ContentUploadRequest
import app.logdate.shared.model.sync.ContentUploadResponse
import app.logdate.shared.model.sync.DraftChange
import app.logdate.shared.model.sync.DraftChangesResponse
import app.logdate.shared.model.sync.DraftUploadRequest
import app.logdate.shared.model.sync.DraftUploadResponse
import app.logdate.shared.model.sync.JournalChange
import app.logdate.shared.model.sync.JournalChangesResponse
import app.logdate.shared.model.sync.JournalDeletion
import app.logdate.shared.model.sync.JournalUpdateRequest
import app.logdate.shared.model.sync.JournalUpdateResponse
import app.logdate.shared.model.sync.JournalUploadRequest
import app.logdate.shared.model.sync.JournalUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** Documentation for the contents, journals, associations and drafts endpoints in `SyncCollectionRoutes.kt`. */
internal object SyncCollectionDocs {
    // Indented to match the descriptions it is spliced into, so `trimIndent()` strips evenly.
    private const val PATCH_RULES =
        """
            Only the fields you send change; omitted fields keep their values. To ask for conflict detection, send
            `"versionConstraint": { "type": "known", "serverVersion": <the version you last saw> }`; if another device
            has written since, the response is `409 CONFLICT` and nothing changes. Leave `versionConstraint` out (or send
            `{ "type": "none" }`) for last-write-wins. Patching an ID that does not exist creates it.
        """

    private fun ResponsesConfig.syncUnauthorized() = bearerUnauthorized(ErrorEnvelope.SYNC)

    private fun ResponsesConfig.syncServerError() = syncError(HttpStatusCode.InternalServerError, SyncExamples.serverMisconfigured)

    private fun RequestConfig.contentId(what: String) {
        pathParameter<String>("contentId") {
            description = "The entry's ID, chosen by the client (the apps use ULIDs). $what"
            example("Example") { value = SyncExamples.CONTENT_ID }
        }
    }

    private fun RequestConfig.journalId(what: String) {
        pathParameter<String>("journalId") {
            description = "The journal's ID, chosen by the client. $what"
            example("Example") { value = SyncExamples.JOURNAL_ID }
        }
    }

    private val contentChange =
        ContentChange(
            id = SyncExamples.CONTENT_ID,
            type = "TEXT",
            content = SyncExamples.NOTE_TEXT,
            createdAt = SyncExamples.CREATED_AT,
            lastUpdated = SyncExamples.CREATED_AT,
            serverVersion = SyncExamples.SERVER_VERSION,
        )

    private val journalChange =
        JournalChange(
            id = SyncExamples.JOURNAL_ID,
            title = "Morning walks",
            description = "One entry per walk, fog permitting.",
            createdAt = SyncExamples.CREATED_AT,
            lastUpdated = SyncExamples.CREATED_AT,
            serverVersion = SyncExamples.SERVER_VERSION,
        )

    // ---- Contents ---------------------------------------------------------------------------

    val upsertContent: RouteConfig.() -> Unit = {
        bearerOperation(
            "upsertContent",
            ApiTags.CONTENTS,
            "Create or replace an entry",
            """
            Saves an entry under the ID in the path, creating it if it is new and replacing it wholesale if it
            exists. Sending the same request twice is safe, which makes it the correct call to retry after a
            dropped connection.

            The first save answers `201 Created` with a `Location` header; later saves answer `200 OK`. Either
            way the response carries the `serverVersion` the server assigned, which is what change feeds use to
            order this write relative to others. Whatever `syncVersion` you send is ignored; the server owns
            versions.

            The `id` in the body must equal the `contentId` in the path. The LogDate apps encrypt `content`,
            `caption` and `location` on the device before saving; the server never reads them.
            """,
        )
        request {
            contentId("Must match `id` in the body.")
            jsonBody(
                ContentUploadRequest(
                    id = SyncExamples.CONTENT_ID,
                    type = "TEXT",
                    content = SyncExamples.NOTE_TEXT,
                    mediaUri = null,
                    createdAt = SyncExamples.CREATED_AT,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                ),
                "The whole entry. `type` is one of `TEXT`, `IMAGE`, `VIDEO`, `AUDIO`.",
            )
        }
        response {
            created(
                "The entry did not exist and has been created.",
                ContentUploadResponse(
                    id = SyncExamples.CONTENT_ID,
                    serverVersion = SyncExamples.SERVER_VERSION,
                    uploadedAt = SyncExamples.SERVER_VERSION,
                ),
                "/api/v1/contents/${SyncExamples.CONTENT_ID}",
            )
            ok(
                "The entry existed and has been replaced.",
                ContentUploadResponse(
                    id = SyncExamples.CONTENT_ID,
                    serverVersion = SyncExamples.SERVER_VERSION,
                    uploadedAt = SyncExamples.SERVER_VERSION,
                ),
            )
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "VALIDATION_ERROR",
                    "`id` in the body differs from `contentId` in the path. Make them the same.",
                    "Request body id must match path contentId",
                ),
            )
            syncUnauthorized()
            syncServerError()
        }
    }

    val listContentChanges: RouteConfig.() -> Unit = {
        bearerOperation(
            "listContentChanges",
            ApiTags.CONTENTS,
            "Page entry changes",
            """
            Returns entries that changed after the `since` cursor, plus tombstones for entries deleted after it.
            This is the read half of sync: call it with `since=0` the first time, store the `lastTimestamp`
            returned, and send it as `since` next time to receive only what is new. While `hasMore` is `true`,
            call again with the returned `lastTimestamp` before treating the client as up to date.

            `lastTimestamp` is a server version, not a clock time; see **Concepts** in the overview. Deleted
            entries appear only in `deletions`, never in `changes`.
            """,
        )
        request { sinceAndLimit(DEFAULT_SYNC_PAGE_SIZE, MAX_SYNC_PAGE_SIZE) }
        response {
            ok(
                "A page of changes. Send `lastTimestamp` back as the next `since`.",
                ContentChangesResponse(
                    changes = listOf(contentChange),
                    deletions = listOf(ContentDeletion(id = "01J7Q2W1V2U3T4S5R6Q7P8N9M0", deletedAt = SyncExamples.CREATED_AT - 60_000)),
                    lastTimestamp = SyncExamples.SERVER_VERSION,
                    hasMore = false,
                ),
            )
            syncError(HttpStatusCode.BadRequest, SyncExamples.invalidSince)
            syncUnauthorized()
            syncServerError()
        }
    }

    val getContent: RouteConfig.() -> Unit = {
        bearerOperation(
            "getContent",
            ApiTags.CONTENTS,
            "Get one entry",
            """
            Fetches a single entry by ID, in the same shape the change feed uses. Useful after a `409 CONFLICT`
            to see what the server currently holds before you merge and patch again.
            """,
        )
        request { contentId("") }
        response {
            ok("The entry as the server holds it.", contentChange)
            syncUnauthorized()
            syncError(
                HttpStatusCode.NotFound,
                ErrorCase("NOT_FOUND", "No entry with that ID belongs to this account, or it was deleted.", "Content not found"),
            )
            syncServerError()
        }
    }

    val patchContent: RouteConfig.() -> Unit = {
        bearerOperation(
            "patchContent",
            ApiTags.CONTENTS,
            "Change part of an entry",
            """
            Updates some fields of an entry, with optional conflict detection.

            $PATCH_RULES
            """,
        )
        request {
            contentId("")
            jsonBody(
                ContentUpdateRequest(
                    content = "Walked to the lake before work. Fog on the water, two herons.",
                    lastUpdated = SyncExamples.SERVER_VERSION + 90_000,
                    deviceId = SyncExamples.deviceId,
                    versionConstraint = VersionConstraint.Known(serverVersion = SyncExamples.SERVER_VERSION),
                ),
                "The fields to change and, optionally, the version you last saw.",
            )
        }
        response {
            ok(
                "The entry was updated. `serverVersion` is its new version.",
                ContentUpdateResponse(
                    id = SyncExamples.CONTENT_ID,
                    serverVersion = SyncExamples.SERVER_VERSION + 90_120,
                    updatedAt =
                        SyncExamples.SERVER_VERSION + 90_120,
                ),
            )
            syncUnauthorized()
            syncError(HttpStatusCode.Conflict, SyncExamples.conflict)
            syncServerError()
        }
    }

    val deleteContent: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteContent",
            ApiTags.CONTENTS,
            "Delete an entry",
            """
            Soft-deletes an entry. It disappears from **Get one entry** and appears as a tombstone in the
            `deletions` list of every device's next **Page entry changes**, so all devices learn about it.
            Deleting an entry that is already gone still answers `204`, so retries are safe.

            Media attached to the entry is not removed; delete it separately with **Delete a media file**.
            """,
        )
        request { contentId("") }
        response {
            noContent("The entry is deleted (or already was).")
            syncUnauthorized()
            syncServerError()
        }
    }

    // ---- Journals ---------------------------------------------------------------------------

    val upsertJournal: RouteConfig.() -> Unit = {
        bearerOperation(
            "upsertJournal",
            ApiTags.JOURNALS,
            "Create or replace a journal",
            """
            Saves a journal under the ID in the path, creating it if new and replacing it if it exists. Safe to
            retry. The first save answers `201 Created` with a `Location` header; later saves answer `200 OK`.
            The `id` in the body must equal the `journalId` in the path, and any `syncVersion` you send is
            ignored: the server assigns versions.
            """,
        )
        request {
            journalId("Must match `id` in the body.")
            jsonBody(
                JournalUploadRequest(
                    id = SyncExamples.JOURNAL_ID,
                    title = "Morning walks",
                    description = "One entry per walk, fog permitting.",
                    createdAt = SyncExamples.CREATED_AT,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                ),
                "The whole journal.",
            )
        }
        response {
            created(
                "The journal did not exist and has been created.",
                JournalUploadResponse(
                    id = SyncExamples.JOURNAL_ID,
                    serverVersion = SyncExamples.SERVER_VERSION,
                    uploadedAt = SyncExamples.SERVER_VERSION,
                ),
                "/api/v1/journals/${SyncExamples.JOURNAL_ID}",
            )
            ok(
                "The journal existed and has been replaced.",
                JournalUploadResponse(
                    id = SyncExamples.JOURNAL_ID,
                    serverVersion = SyncExamples.SERVER_VERSION,
                    uploadedAt = SyncExamples.SERVER_VERSION,
                ),
            )
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "VALIDATION_ERROR",
                    "`id` in the body differs from `journalId` in the path. Make them the same.",
                    "Request body id must match path journalId",
                ),
            )
            syncUnauthorized()
            syncServerError()
        }
    }

    val listJournalChanges: RouteConfig.() -> Unit = {
        bearerOperation(
            "listJournalChanges",
            ApiTags.JOURNALS,
            "Page journal changes",
            """
            Returns journals changed after the `since` cursor and tombstones for journals deleted after it.
            Works exactly like **Page entry changes**: start at `0`, send back `lastTimestamp`, keep going while
            `hasMore` is `true`. Journals and entries share one version sequence per account, so the same
            cursor value means the same point in time for both feeds.
            """,
        )
        request { sinceAndLimit(DEFAULT_SYNC_PAGE_SIZE, MAX_SYNC_PAGE_SIZE) }
        response {
            ok(
                "A page of changes. Send `lastTimestamp` back as the next `since`.",
                JournalChangesResponse(
                    changes = listOf(journalChange),
                    deletions = listOf(JournalDeletion(id = "01J7Q2V0U1T2S3R4Q5P6N7M8L9", deletedAt = SyncExamples.CREATED_AT - 120_000)),
                    lastTimestamp = SyncExamples.SERVER_VERSION,
                    hasMore = false,
                ),
            )
            syncError(HttpStatusCode.BadRequest, SyncExamples.invalidSince)
            syncUnauthorized()
            syncServerError()
        }
    }

    val getJournal: RouteConfig.() -> Unit = {
        bearerOperation(
            "getJournal",
            ApiTags.JOURNALS,
            "Get one journal",
            "Fetches a single journal by ID, in the same shape the change feed uses. Useful after a `409 CONFLICT` to see the server's copy.",
        )
        request { journalId("") }
        response {
            ok("The journal as the server holds it.", journalChange)
            syncUnauthorized()
            syncError(
                HttpStatusCode.NotFound,
                ErrorCase("NOT_FOUND", "No journal with that ID belongs to this account, or it was deleted.", "Journal not found"),
            )
            syncServerError()
        }
    }

    val patchJournal: RouteConfig.() -> Unit = {
        bearerOperation(
            "patchJournal",
            ApiTags.JOURNALS,
            "Change part of a journal",
            """
            Updates the title or description of a journal, with optional conflict detection.

            $PATCH_RULES
            """,
        )
        request {
            journalId("")
            jsonBody(
                JournalUpdateRequest(
                    title = "Morning walks (2026)",
                    lastUpdated = SyncExamples.SERVER_VERSION + 90_000,
                    deviceId = SyncExamples.deviceId,
                    versionConstraint = VersionConstraint.Known(serverVersion = SyncExamples.SERVER_VERSION),
                ),
                "The fields to change and, optionally, the version you last saw.",
            )
        }
        response {
            ok(
                "The journal was updated. `serverVersion` is its new version.",
                JournalUpdateResponse(
                    id = SyncExamples.JOURNAL_ID,
                    serverVersion = SyncExamples.SERVER_VERSION + 90_120,
                    updatedAt =
                        SyncExamples.SERVER_VERSION + 90_120,
                ),
            )
            syncUnauthorized()
            syncError(HttpStatusCode.Conflict, SyncExamples.conflict)
            syncServerError()
        }
    }

    val deleteJournal: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteJournal",
            ApiTags.JOURNALS,
            "Delete a journal",
            """
            Soft-deletes a journal; it becomes a tombstone in every device's next **Page journal changes**.
            The entries in it are not touched and their associations are not removed, so an app should delete
            or re-home those itself. Deleting an already-deleted journal still answers `204`.
            """,
        )
        request { journalId("") }
        response {
            noContent("The journal is deleted (or already was).")
            syncUnauthorized()
            syncServerError()
        }
    }

    // ---- Associations -----------------------------------------------------------------------

    val uploadAssociations: RouteConfig.() -> Unit = {
        bearerOperation(
            "uploadAssociations",
            ApiTags.ASSOCIATIONS,
            "Link entries to journals in bulk",
            """
            Creates or refreshes many entry-to-journal links in one request. Each item names a `journalId` and a
            `contentId`; the pair is the link's identity, so sending the same pair again updates it. This
            is what the app calls after a batch of new entries; for a single link there is also
            **Link one entry to a journal**.

            The server does not check that the journal or entry exist, so you can upload links before, after,
            or alongside the things they connect.
            """,
        )
        request {
            jsonBody(
                AssociationUploadRequest(
                    associations =
                        listOf(
                            Association(
                                journalId = SyncExamples.JOURNAL_ID,
                                contentId = SyncExamples.CONTENT_ID,
                                createdAt = SyncExamples.CREATED_AT,
                                deviceId = SyncExamples.deviceId,
                            ),
                        ),
                ),
                "The links to create or refresh.",
            )
        }
        response {
            ok(
                "All links were saved.",
                AssociationUploadResponse(uploadedCount = 1, uploadedAt = SyncExamples.SERVER_VERSION),
            )
            syncUnauthorized()
            syncServerError()
        }
    }

    val listAssociationChanges: RouteConfig.() -> Unit = {
        bearerOperation(
            "listAssociationChanges",
            ApiTags.ASSOCIATIONS,
            "Page link changes",
            """
            Returns links created or refreshed after the `since` cursor, and tombstones for links removed after
            it. Same cursor rules as **Page entry changes**. Because links share the account's version sequence,
            a device that pages entries, journals and links from the same `since` gets a consistent picture.
            """,
        )
        request { sinceAndLimit(DEFAULT_SYNC_PAGE_SIZE, MAX_SYNC_PAGE_SIZE) }
        response {
            ok(
                "A page of changes. Send `lastTimestamp` back as the next `since`.",
                AssociationChangesResponse(
                    changes =
                        listOf(
                            AssociationChange(
                                journalId = SyncExamples.JOURNAL_ID,
                                contentId = SyncExamples.CONTENT_ID,
                                createdAt = SyncExamples.CREATED_AT,
                                serverVersion = SyncExamples.SERVER_VERSION,
                            ),
                        ),
                    deletions =
                        listOf(
                            AssociationDeletion(
                                journalId = SyncExamples.JOURNAL_ID,
                                contentId = "01J7Q2W1V2U3T4S5R6Q7P8N9M0",
                                deletedAt = SyncExamples.CREATED_AT - 60_000,
                            ),
                        ),
                    lastTimestamp = SyncExamples.SERVER_VERSION,
                    hasMore = false,
                ),
            )
            syncError(HttpStatusCode.BadRequest, SyncExamples.invalidSince)
            syncUnauthorized()
            syncServerError()
        }
    }

    val upsertAssociation: RouteConfig.() -> Unit = {
        bearerOperation(
            "upsertAssociation",
            ApiTags.ASSOCIATIONS,
            "Link one entry to a journal",
            """
            Creates or refreshes a single link between the journal and the entry named in the path. Safe to
            repeat. Answers `204` with no body; the link's version appears in the next **Page link changes**.
            """,
        )
        request {
            journalId("")
            contentId("")
            jsonBody(
                AssociationLinkUpsertRequest(createdAt = SyncExamples.CREATED_AT, deviceId = SyncExamples.deviceId),
                "When and where the link was made.",
            )
        }
        response {
            noContent("The link exists.")
            syncUnauthorized()
            syncServerError()
        }
    }

    val deleteAssociation: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteAssociation",
            ApiTags.ASSOCIATIONS,
            "Unlink one entry from a journal",
            """
            Removes the link between the journal and the entry named in the path. Neither the journal nor the
            entry is touched. The removal becomes a tombstone in **Page link changes**. Repeating the call
            answers `204` again.
            """,
        )
        request {
            journalId("")
            contentId("")
        }
        response {
            noContent("The link is gone (or already was).")
            syncUnauthorized()
            syncServerError()
        }
    }

    val deleteAssociations: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteAssociations",
            ApiTags.ASSOCIATIONS,
            "Unlink entries in bulk",
            """
            Removes many links in one request, each named by its `journalId` and `contentId` pair. Pairs that do
            not exist are ignored. Every removal becomes a tombstone in **Page link changes**.
            """,
        )
        request {
            jsonBody(
                AssociationDeleteRequest(
                    associations = listOf(AssociationDeleteItem(journalId = SyncExamples.JOURNAL_ID, contentId = SyncExamples.CONTENT_ID)),
                ),
                "The links to remove.",
            )
        }
        response {
            noContent("The links are gone.")
            syncUnauthorized()
            syncServerError()
        }
    }

    // ---- Drafts -------------------------------------------------------------------------------

    val upsertDraft: RouteConfig.() -> Unit = {
        bearerOperation(
            "upsertDraft",
            ApiTags.DRAFTS,
            "Save a draft",
            """
            Saves an in-progress entry under the ID in the path so another device can continue it. Creating and
            updating are the same call and both answer `200`; unlike entries, drafts never answer `201` or send
            a `Location` header. The server assigns `serverVersion`.

            Once the person saves the finished entry with **Create or replace an entry**, delete the draft.
            """,
        )
        request {
            pathParameter<String>("draftId") {
                description = "The draft's ID, chosen by the client. Reuse it for every save of the same draft."
                example("Example") { value = SyncExamples.DRAFT_ID }
            }
            jsonBody(
                DraftUploadRequest(
                    id = SyncExamples.DRAFT_ID,
                    content = "Ideas for the weekend: lake at dawn, then",
                    blockTypes = listOf("TEXT"),
                    journalIds = listOf(SyncExamples.JOURNAL_ID),
                    createdAt = SyncExamples.CREATED_AT,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                ),
                "The draft's current state.",
            )
        }
        response {
            ok(
                "The draft is saved. `serverVersion` is its new version.",
                DraftUploadResponse(
                    id = SyncExamples.DRAFT_ID,
                    serverVersion = SyncExamples.SERVER_VERSION,
                    uploadedAt = SyncExamples.SERVER_VERSION,
                ),
            )
            syncUnauthorized()
            syncServerError()
        }
    }

    val listDraftChanges: RouteConfig.() -> Unit = {
        bearerOperation(
            "listDraftChanges",
            ApiTags.DRAFTS,
            "Page draft changes",
            """
            Returns drafts created or updated after the `since` cursor. Deleted drafts are not delivered: they
            stop appearing, and `is_deleted` is always `false`. Remove a draft locally once its finished entry
            exists.

            This feed is simpler than the others and behaves a little differently: there is no `hasMore` or
            `lastTimestamp`, so use the highest `serverVersion` in the page as your next `since`; `limit`
            defaults to {{drafts.limit.default}} and is not clamped; and a non-numeric `since` is treated as `0`
            instead of being rejected.
            """,
        )
        request { sinceAndLimit(DRAFT_SYNC_PAGE_SIZE, null) }
        response {
            ok(
                "Drafts changed since the cursor.",
                DraftChangesResponse(
                    drafts =
                        listOf(
                            DraftChange(
                                id = SyncExamples.DRAFT_ID,
                                content = "Ideas for the weekend: lake at dawn, then",
                                blockTypes = listOf("TEXT"),
                                journalIds = listOf(SyncExamples.JOURNAL_ID),
                                createdAt = SyncExamples.CREATED_AT,
                                lastUpdated = SyncExamples.CREATED_AT,
                                deviceId = SyncExamples.deviceId,
                                serverVersion = SyncExamples.SERVER_VERSION,
                            ),
                        ),
                ),
            )
            syncUnauthorized()
            syncServerError()
        }
    }

    val deleteDraft: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteDraft",
            ApiTags.DRAFTS,
            "Delete a draft",
            """
            Soft-deletes a draft. Other devices are not told about the deletion by **Page draft changes**, so
            delete the draft on each device once the finished entry has been saved. Repeating the call answers
            `204` again.
            """,
        )
        request {
            pathParameter<String>("draftId") {
                description = "The draft's ID, as used when it was saved."
                example("Example") { value = SyncExamples.DRAFT_ID }
            }
        }
        response {
            noContent("The draft is deleted (or already was).")
            syncUnauthorized()
            syncServerError()
        }
    }
}
