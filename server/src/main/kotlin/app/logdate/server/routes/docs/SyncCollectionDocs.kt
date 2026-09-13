package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.created
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.sinceAndLimit
import app.logdate.server.routes.sync.DEFAULT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MAX_SYNC_PAGE_SIZE
import app.logdate.server.routes.syncError
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.ContentDeletion
import app.logdate.shared.model.sync.ContentUpdateRequest
import app.logdate.shared.model.sync.ContentUpdateResponse
import app.logdate.shared.model.sync.ContentUploadRequest
import app.logdate.shared.model.sync.ContentUploadResponse
import app.logdate.shared.model.sync.JournalChangesResponse
import app.logdate.shared.model.sync.JournalDeletion
import app.logdate.shared.model.sync.JournalUpdateRequest
import app.logdate.shared.model.sync.JournalUpdateResponse
import app.logdate.shared.model.sync.JournalUploadRequest
import app.logdate.shared.model.sync.JournalUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** Documentation for the contents and journals endpoints in `SyncCollectionRoutes.kt`. */
internal object SyncCollectionDocs {
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
            call again with the returned `lastTimestamp` before treating the client as up to date, and read the
            caveat on `hasMore` in **Concepts**: a full page can leave a lower-versioned record behind.

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
}
