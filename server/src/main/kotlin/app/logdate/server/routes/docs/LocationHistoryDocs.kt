package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.syncError
import app.logdate.shared.model.sync.LocationHistoryBatchRequest
import app.logdate.shared.model.sync.LocationHistoryBatchResponse
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal object LocationHistoryDocs {
    private val deletedRecord = LocationHistoryRecord("place:opaque-id", "place", null, 1, "device-id", 2, 8, true)

    val upload: RouteConfig.() -> Unit = {
        bearerOperation(
            "uploadLocationHistory",
            ApiTags.LOCATION_HISTORY,
            "Save encrypted location history",
            """
            Atomically saves 1 to 100 records for the authenticated account. Encrypt coordinates, place names,
            corrections and memory links on the client; only synchronization metadata and opaque ciphertext reach
            the server. A live payload must start with `LDSE2:` and contain 7 to 65536 characters. The complete
            request body must not exceed 7,000,000 bytes. IDs and device IDs must contain 1 to 128 characters.

            Send `expectedServerVersion` as the last observed version, or 0 for a new record. A conflict rejects
            the entire batch with 409; download changes before retrying. Repeating an accepted immutable device
            version is idempotent even if its encrypted payload uses a new nonce. Deletions use `deleted: true`
            and a null payload. Tombstones are retained indefinitely and cannot be replaced by live records.
            """,
        )
        request {
            jsonBody(
                LocationHistoryBatchRequest(
                    listOf(
                        LocationHistoryUpload(
                            id = "place:opaque-id",
                            recordType = "place",
                            payload = null,
                            deviceId = "device-id",
                            deviceVersion = 2,
                            expectedServerVersion = 7,
                            deleted = true,
                        ),
                    ),
                ),
                "An atomic batch of encrypted records or permanent tombstones; IDs must be unique within the batch.",
            )
        }
        response {
            ok("The accepted records with their account-scoped server versions.", LocationHistoryBatchResponse(listOf(deletedRecord)))
            syncUnauthorized()
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase("VALIDATION_ERROR", "The JSON or record fields are invalid.", "Invalid location history batch"),
            )
            syncError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "VERSION_CONFLICT",
                    "Download current versions and reconcile the batch before retrying.",
                    "Pull changes before retrying this batch",
                ),
            )
            syncError(
                HttpStatusCode.PayloadTooLarge,
                ErrorCase("VALIDATION_ERROR", "The request exceeds 7,000,000 bytes. Send a smaller batch.", "Batch is too large"),
            )
        }
    }

    val changes: RouteConfig.() -> Unit = {
        bearerOperation(
            "listLocationHistoryChanges",
            ApiTags.LOCATION_HISTORY,
            "Page encrypted location history",
            """
            Returns the latest version of each changed record, including permanent deletion tombstones, for the
            authenticated account. Records are ordered by increasing account-scoped `serverVersion`. This cursor
            is independent of note and journal sync cursors and is not a timestamp.

            Start with `since=0`, decrypt and commit the complete page locally, then persist `nextCursor` and pass
            it as the next `since`. Keep requesting pages while `hasMore` is true. If interrupted, repeat the last
            uncommitted page; do not advance the cursor past records that could not be decrypted.
            """,
        )
        request {
            queryParameter<Long>("since") {
                description =
                    "Exclusive nonnegative server version; defaults to 0. Negative or nonnumeric values return 400 VALIDATION_ERROR."
                required = false
                example("First page") { value = 0L }
            }
            queryParameter<Int>("limit") {
                description =
                    "Maximum records, including tombstones, in the page. Defaults to 100; values outside 1 through 100 or nonnumeric values return 400 VALIDATION_ERROR."
                required = false
                example("Default") { value = 100 }
            }
        }
        response {
            ok("One page of ciphertext and tombstones.", LocationHistoryChangesResponse(listOf(deletedRecord), 8, false))
            syncUnauthorized()
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase("VALIDATION_ERROR", "Correct the since or limit query parameter.", "Invalid page parameters"),
            )
        }
    }
}
