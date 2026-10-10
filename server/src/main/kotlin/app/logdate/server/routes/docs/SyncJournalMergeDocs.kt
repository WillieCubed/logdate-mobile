package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.syncError
import app.logdate.shared.model.sync.JournalMergeRequest
import app.logdate.shared.model.sync.JournalMergeResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal object SyncJournalMergeDocs {
    val merge: RouteConfig.() -> Unit = {
        bearerOperation(
            "mergeJournals",
            ApiTags.JOURNALS,
            "Merge a journal into another journal",
            """
            Adds all source memberships and the submitted raw content IDs to the destination, then
            deletes the source. Entries and destination metadata remain unchanged. Content IDs may
            refer to entries still held offline. Retry with the same operationId and request after an
            interrupted response; changing that operation's request returns MERGE_CONFLICT.

            Future source membership additions follow the permanent redirect to the surviving
            destination. Source removals are ignored. Saving source metadata returns JOURNAL_MERGED,
            with the surviving destination ID in details.destinationId. Journal deletion changes
            include mergedIntoJournalId so other devices can reconcile the same merge.
            """,
        )
        request {
            pathParameter<String>("journalId") { description = "Source journal ID, including a journal created offline." }
            jsonBody(
                JournalMergeRequest("merge-operation-id", SyncExamples.JOURNAL_ID, listOf(SyncExamples.CONTENT_ID)),
                "Stable operation ID, destination journal ID, and complete locally known source membership IDs.",
            )
        }
        response {
            ok(
                "The merge completed or was already completed.",
                JournalMergeResponse("merge-operation-id", "source-journal-id", SyncExamples.JOURNAL_ID),
            )
            syncError(HttpStatusCode.BadRequest, ErrorCase("VALIDATION_ERROR", "A merge ID is empty.", "Merge IDs must be non-empty"))
            syncError(
                HttpStatusCode.NotFound,
                ErrorCase(
                    "MERGE_DESTINATION_MISSING",
                    "The destination has not been uploaded or has been deleted.",
                    "Merge destination is unavailable",
                ),
            )
            syncError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "MERGE_CONFLICT",
                    "The operation ID was reused, the source was retargeted, or the merge forms a cycle.",
                    "Merge request conflicts with a previous operation",
                ),
            )
            syncUnauthorized()
            syncServerError()
        }
    }
}
