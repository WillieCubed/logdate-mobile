package app.logdate.server.routes.docs

import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.syncError
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.JournalChange
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.ktor.http.HttpStatusCode

/** Response helpers and examples shared by the sync documentation objects. */
internal fun ResponsesConfig.syncUnauthorized() = bearerUnauthorized(ErrorEnvelope.SYNC)

internal fun ResponsesConfig.syncServerError() = syncError(HttpStatusCode.InternalServerError, SyncExamples.serverMisconfigured)

// Indented to match the descriptions it is spliced into, so `trimIndent()` strips evenly.
internal const val PATCH_RULES =
    """
        Omitted text fields keep their values (`content`, `mediaUri`, `caption` and `location` on entries; `title`
        and `description` on journals). `deviceId`, and `durationMs` on entries, are always written, so omitting
        them resets them to `unknown` and `0`: resend them. To ask for conflict detection, send
        `"versionConstraint": { "type": "known", "serverVersion": <the version you last saw> }`; if another device
        has written since, the response is `409 CONFLICT` and nothing changes. Leave `versionConstraint` out (or send
        `{ "type": "none" }`) for last-write-wins. Patching an ID that does not exist creates it.
    """

internal fun RequestConfig.contentId(what: String) {
    pathParameter<String>("contentId") {
        description = "The entry's ID, chosen by the client (the apps use ULIDs). $what"
        example("Example") { value = SyncExamples.CONTENT_ID }
    }
}

internal fun RequestConfig.journalId(what: String) {
    pathParameter<String>("journalId") {
        description = "The journal's ID, chosen by the client. $what"
        example("Example") { value = SyncExamples.JOURNAL_ID }
    }
}

internal val contentChange =
    ContentChange(
        id = SyncExamples.CONTENT_ID,
        type = "TEXT",
        content = SyncExamples.NOTE_TEXT,
        createdAt = SyncExamples.CREATED_AT,
        lastUpdated = SyncExamples.CREATED_AT,
        serverVersion = SyncExamples.SERVER_VERSION,
    )

internal val journalChange =
    JournalChange(
        id = SyncExamples.JOURNAL_ID,
        title = "Morning walks",
        description = "One entry per walk, fog permitting.",
        createdAt = SyncExamples.CREATED_AT,
        lastUpdated = SyncExamples.CREATED_AT,
        serverVersion = SyncExamples.SERVER_VERSION,
    )
