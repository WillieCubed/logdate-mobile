package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.sinceAndLimit
import app.logdate.server.routes.sync.DRAFT_SYNC_PAGE_SIZE
import app.logdate.shared.model.sync.DraftChange
import app.logdate.shared.model.sync.DraftChangesResponse
import app.logdate.shared.model.sync.DraftUploadRequest
import app.logdate.shared.model.sync.DraftUploadResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig

/** Documentation for the draft endpoints in `SyncCollectionRoutes.kt`. */
internal object SyncDraftDocs {
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
