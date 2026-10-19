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
import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import app.logdate.shared.model.sync.MomentDeletion
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUpdateResponse
import app.logdate.shared.model.sync.MomentUploadRequest
import app.logdate.shared.model.sync.MomentUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

private const val MOMENT_ID = "6f0b2d4e-8a1c-4e3f-b5d7-9c2a4e6f8b10"
private const val DELETED_MOMENT_ID = "1c9e7a5b-3d2f-4b6a-8e0c-2f4a6c8e0b1d"
private const val MOMENT_PAYLOAD = "LDSE2:eyJlbnYiOnsiYWxnIjoiQUVTLUdDTSJ9fQ"

private val momentChange =
    MomentChange(
        id = MOMENT_ID,
        capturedAt = SyncExamples.CREATED_AT,
        lastUpdated = SyncExamples.CREATED_AT,
        deviceId = SyncExamples.deviceId,
        payload = MOMENT_PAYLOAD,
        schema = 1,
        serverVersion = SyncExamples.SERVER_VERSION,
    )

private val invalidMoment =
    ErrorCase(
        "INVALID_MOMENT",
        "The ID is not a valid record key, the payload is not one `LDSE2:` encrypted value of at most 64 KiB, or " +
            "`schema` is below 1. `message` says which.",
        "Moment payload must be one LDSE2 encrypted value",
    )

private val momentNotFound = ErrorCase("NOT_FOUND", "No live moment has this ID.", "Moment not found")

private fun RequestConfig.momentId(what: String) {
    pathParameter<String>("momentId") {
        description = "The moment's ID, chosen by the client (the apps use UUIDs). $what".trim()
        example("Example") { value = MOMENT_ID }
    }
}

/** Documentation for `SyncMomentRoutes.kt`. */
internal object SyncMomentDocs {
    val upsertMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "upsertMoment",
            ApiTags.MOMENTS,
            "Create or replace a moment",
            """
            Saves a marked moment under the ID in the path, creating it if new (`201` with a `Location`) and
            replacing it if it exists (`200`). Everything but the times is in `payload`, encrypted on the device;
            the server never reads it. Linked devices may call this.

            Send `?ifAbsent=true` to create only: a live ID answers `412 MOMENT_EXISTS` and a deleted one
            `410 MOMENT_DELETED`, and nothing is written. `If-None-Match: *` also creates only, but may recreate
            a deleted moment.
            """,
        )
        request {
            momentId("Must match `id` in the body.")
            ifAbsentQuery("a live moment answers `412 MOMENT_EXISTS` and a deleted one `410 MOMENT_DELETED`; neither writes anything.")
            jsonBody(
                MomentUploadRequest(
                    id = MOMENT_ID,
                    capturedAt = SyncExamples.CREATED_AT,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                    payload = MOMENT_PAYLOAD,
                ),
                "The whole moment.",
            )
        }
        response {
            val saved =
                MomentUploadResponse(id = MOMENT_ID, serverVersion = SyncExamples.SERVER_VERSION, uploadedAt = SyncExamples.SERVER_VERSION)
            created("The moment did not exist and has been created.", saved, "/api/v1/moments/$MOMENT_ID")
            ok("The moment existed and has been replaced.", saved)
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "VALIDATION_ERROR",
                    "`id` in the body differs from `momentId` in the path.",
                    "Request body id must match path momentId",
                ),
                invalidMoment,
            )
            syncError(
                HttpStatusCode.PreconditionFailed,
                ErrorCase(
                    "MOMENT_EXISTS",
                    "A live moment already has this ID and `If-None-Match: *` or `?ifAbsent=true` was sent. Nothing was " +
                        "written; `details.serverVersion` is the stored version.",
                    "Moment already exists",
                ),
                details = existsDetails,
            )
            syncError(
                HttpStatusCode.Gone,
                ErrorCase(
                    "MOMENT_DELETED",
                    "The moment was deleted and `?ifAbsent=true` was sent. Nothing was written; `details.deletedAt` says when. " +
                        "Drop the local copy instead of uploading it again.",
                    "Moment was deleted",
                ),
                details = deletedDetails,
            )
            syncUnauthorized()
            syncStorageModeMismatch()
            syncServerError()
        }
    }

    val listMomentChanges: RouteConfig.() -> Unit = {
        bearerOperation(
            "listMomentChanges",
            ApiTags.MOMENTS,
            "Page moment changes",
            """
            Returns moments changed after the `since` cursor and tombstones for moments deleted after it. Works
            like **Page entry changes**, with the same cursor: moments share the account's version sequence.
            """,
        )
        request { sinceAndLimit(DEFAULT_SYNC_PAGE_SIZE, MAX_SYNC_PAGE_SIZE) }
        response {
            ok(
                "A page of changes. Send `lastTimestamp` back as the next `since`.",
                MomentChangesResponse(
                    changes = listOf(momentChange),
                    deletions =
                        listOf(
                            MomentDeletion(
                                id = DELETED_MOMENT_ID,
                                deletedAt = SyncExamples.CREATED_AT,
                                serverVersion = SyncExamples.SERVER_VERSION - 1,
                            ),
                        ),
                    lastTimestamp = SyncExamples.SERVER_VERSION,
                ),
            )
            syncError(HttpStatusCode.BadRequest, SyncExamples.invalidSince)
            syncUnauthorized()
            syncStorageModeMismatch()
            syncServerError()
        }
    }

    val getMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "getMoment",
            ApiTags.MOMENTS,
            "Read a moment",
            "Returns one live moment as the server stores it, with its `serverVersion`. A deleted moment answers `404`.",
        )
        request { momentId("") }
        response {
            ok("The moment.", momentChange)
            syncError(HttpStatusCode.NotFound, momentNotFound)
            syncUnauthorized()
            syncStorageModeMismatch()
            syncServerError()
        }
    }

    val patchMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "patchMoment",
            ApiTags.MOMENTS,
            "Update a moment",
            """
            Replaces a moment's payload and schema, and its capture time when sent. To ask for conflict detection,
            send `"versionConstraint": { "type": "known", "serverVersion": <the version you last saw> }`; if
            another device wrote in between, the response is `409 CONFLICT` and nothing changes. Unlike entries,
            patching an ID that does not exist answers `404`.
            """,
        )
        request {
            momentId("")
            jsonBody(
                MomentUpdateRequest(
                    payload = MOMENT_PAYLOAD,
                    lastUpdated = SyncExamples.CREATED_AT,
                    deviceId = SyncExamples.deviceId,
                    versionConstraint = VersionConstraint.Known(SyncExamples.SERVER_VERSION),
                ),
                "The new payload, plus the version you last saw.",
            )
        }
        response {
            ok("The moment was updated.", MomentUpdateResponse(MOMENT_ID, SyncExamples.SERVER_VERSION + 1, SyncExamples.SERVER_VERSION + 1))
            syncError(HttpStatusCode.BadRequest, invalidMoment)
            syncError(HttpStatusCode.NotFound, momentNotFound)
            syncError(HttpStatusCode.Conflict, SyncExamples.conflict)
            syncUnauthorized()
            syncStorageModeMismatch()
            syncServerError()
        }
    }

    val deleteMoment: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteMoment",
            ApiTags.MOMENTS,
            "Delete a moment",
            """
            Deletes a moment and leaves a tombstone, so other devices learn of the deletion from
            **Page moment changes**. Deleting a moment that does not exist also answers `204`.
            """,
        )
        request { momentId("") }
        response {
            noContent("The moment is deleted.")
            syncUnauthorized()
            syncStorageModeMismatch()
            syncServerError()
        }
    }
}
