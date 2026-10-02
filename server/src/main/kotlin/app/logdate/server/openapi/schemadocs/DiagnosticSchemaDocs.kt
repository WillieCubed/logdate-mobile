package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

private const val RANDOM_ID = "A random correlation ID, or `null`. It links related events and never identifies a person or record."

/** Prose for the diagnostic report schemas. */
internal object DiagnosticSchemaDocs {
    val docs: Map<String, SchemaDoc> =
        mapOf(
            "DiagnosticReportReference" to
                SchemaDoc("Which report the server stored.", mapOf("reportId" to "The report's ID, as the client chose it.")),
            "DiagnosticReportListing" to
                SchemaDoc("The account's retained reports.", mapOf("reports" to "One item per retained report, newest first.")),
            "DiagnosticReportItem" to
                SchemaDoc(
                    "One retained report.",
                    mapOf(
                        "reportId" to "The report's ID.",
                        "createdAt" to "When the server accepted it. Milliseconds since the Unix epoch, UTC.",
                    ),
                ),
            "SyncDiagnosticReport" to
                SchemaDoc(
                    "A sync diagnostic report. Only fixed codes, counters and random IDs: no content, names, paths or credentials.",
                    mapOf(
                        "reportId" to "A random UUID the client picks. Sending the same report again with it is harmless.",
                        "schemaVersion" to "The report format version. Currently `1`.",
                        "events" to "Sync events, oldest first.",
                        "droppedEvents" to "How many older events the device discarded to stay within its retention limit.",
                    ),
                ),
            "SyncDiagnosticEvent" to
                SchemaDoc(
                    "One step of a sync, backup or restore attempt.",
                    mapOf(
                        "phase" to "Which part of sync this happened in.",
                        "outcome" to "What happened.",
                        "reason" to "Why, when the outcome was not a success.",
                        "action" to "What the person or app can do next.",
                        "elapsedMs" to "Milliseconds since the start of the sync run.",
                        "runId" to RANDOM_ID,
                        "operationId" to "$RANDOM_ID Stays the same across retries of one queued change.",
                        "attemptId" to "$RANDOM_ID New for every attempt.",
                        "requestId" to "$RANDOM_ID Also sent to the server, which echoes it, so both sides can be matched up.",
                        "recordAlias" to "A per-report alias for the record involved, or `null`. Aliases are remapped in every report.",
                        "attemptCount" to "How many attempts this operation has had.",
                        "pendingCount" to "How many changes were still queued.",
                        "durationMs" to "How long this step took.",
                        "bytes" to "Bytes transferred in this step.",
                        "httpStatus" to "The HTTP status the server answered with, or `null` if no response arrived.",
                        "cursorAdvanced" to "Whether the download cursor moved forward.",
                        "retryable" to "Whether the app will retry on its own.",
                        "schemaVersion" to "The event format version. Currently `1`.",
                        "code" to "A finer classification of the event.",
                        "context" to "App, platform and network context, or `null`.",
                        "frames" to "Up to eight coarse stack frames for a failure.",
                        "route" to "Which API endpoint a request went to, as a fixed category.",
                    ),
                ),
            "DiagnosticContext" to
                SchemaDoc(
                    "Coarse context for an event. Versions are numbers only, so they cannot carry hostnames or device IDs.",
                    mapOf(
                        "appBuild" to "The app's build number, or `null`.",
                        "appVersion" to "The app's version, one number per dotted part.",
                        "serverBuild" to "The server's source revision in lowercase hex, or `null`.",
                        "platform" to "Which platform the event came from.",
                        "osVersion" to "The operating system version, one number per dotted part.",
                        "protocols" to "Sync protocol features the device supports.",
                        "network" to "Whether the device was online.",
                        "scheduler" to "Whether the background scheduler was running the sync.",
                    ),
                ),
            "DiagnosticFrame" to
                SchemaDoc(
                    "A coarse stack frame: a component and line, never a class, method, file or exception name.",
                    mapOf("component" to "Which part of the app the frame is in.", "line" to "The source line, or `null`."),
                ),
            "DiagnosticPhase" to
                SchemaDoc(
                    "Which part of sync an event belongs to.",
                    enumValues =
                        mapOf(
                            "SCHEDULING" to "Deciding when to sync.",
                            "IDENTITY" to "Preparing the account's encryption keys.",
                            "UPLOAD" to "Sending local changes.",
                            "FETCH" to "Reading changes from the server.",
                            "PERSIST" to "Saving downloaded changes to the device.",
                            "APPLY" to "Applying downloaded changes to journals and entries.",
                            "MEDIA" to "Uploading or downloading photos, video and audio.",
                            "ARCHIVE" to "Uploading or downloading a backup archive.",
                            "RESTORE" to "Restoring from a backup.",
                            "RECOVERY" to "Recovering missing media or keys.",
                        ),
                ),
            "DiagnosticOutcome" to
                SchemaDoc(
                    "What happened at a step.",
                    enumValues =
                        mapOf(
                            "QUEUED" to "Work was queued.",
                            "STARTED" to "Work started.",
                            "SUCCEEDED" to "Work finished.",
                            "FAILED" to "Work failed.",
                            "RETRY_SCHEDULED" to "Work failed and a retry is scheduled.",
                            "INTERRUPTED" to "No result was recorded, for example because the app was closed. Not proof of an error.",
                            "CONFLICT" to "Another device changed the same record.",
                            "PAUSED" to "Work is waiting, for example for a network connection.",
                        ),
                ),
            "DiagnosticReason" to
                SchemaDoc(
                    "Why a step did not succeed.",
                    enumValues =
                        mapOf(
                            "NONE" to "No failure.",
                            "OFFLINE" to "The device had no connection.",
                            "SIGN_IN_REQUIRED" to "The session expired.",
                            "SERVER_UNAVAILABLE" to "The server did not answer or answered with a server error.",
                            "RATE_LIMITED" to "The server asked the app to slow down.",
                            "QUOTA_EXCEEDED" to "The account is out of storage.",
                            "LOCAL_STORAGE" to "The device is out of space or could not write.",
                            "MISSING_MEDIA" to "A media file was missing.",
                            "CORRUPT_PAYLOAD" to "Data could not be decrypted or parsed.",
                            "KEY_RECOVERY_REQUIRED" to "The device lacks the account's encryption key.",
                            "INCOMPATIBLE_SERVER" to "The server lacks a feature the app needs.",
                            "PERSISTENCE_FAILED" to "Saving to the device's database failed.",
                            "UNSUPPORTED_FORMAT" to "The data uses a format this app version does not understand.",
                            "CONFLICT" to "Another device changed the same record.",
                            "UNKNOWN" to "The cause was not classified.",
                        ),
                ),
            "DiagnosticAction" to
                SchemaDoc(
                    "The next step that could resolve a problem.",
                    enumValues =
                        mapOf(
                            "NONE" to "Nothing to do.",
                            "RETRY" to "Try again; queued changes are kept.",
                            "CONNECT" to "Connect to the internet, then retry.",
                            "SIGN_IN" to "Sign in again to the same account and server.",
                            "FREE_SPACE" to "Free up space on the device.",
                            "RECOVER_KEY" to "Recover the account's encryption key.",
                            "UPDATE_SERVER" to "The server needs an update.",
                            "UPDATE_APP" to "The app needs an update.",
                            "REVIEW_CONFLICT" to "Review the conflicting versions in the app.",
                            "CONTACT_SUPPORT" to "Contact support, optionally with this report.",
                        ),
                ),
            "DiagnosticCode" to
                SchemaDoc(
                    "A finer classification of an event.",
                    enumValues =
                        mapOf(
                            "PHASE_TRANSITION" to "Sync moved to another phase.",
                            "WORK_QUEUED" to "Work was queued.",
                            "ATTEMPT_STARTED" to "An attempt started.",
                            "REQUEST_COMPLETED" to "A server request finished.",
                            "PAGE_PERSISTED" to "A downloaded page was saved to the device.",
                            "RECORD_APPLIED" to "A downloaded record was applied.",
                            "RETRY_SCHEDULED" to "A retry was scheduled.",
                            "INTERRUPTED" to "An attempt ended without a result.",
                            "CONFLICT" to "A conflict was found.",
                            "COMPLETED" to "Work finished.",
                            "FAILED" to "Work failed.",
                            "USER_RETRY" to "The person asked to retry.",
                        ),
                ),
            "DiagnosticRoute" to
                SchemaDoc(
                    "The API endpoint a request went to, as a fixed category. The actual path, IDs and query are never kept.",
                    enumValues =
                        mapOf(
                            "UNKNOWN" to "Any other path.",
                            "NOTES" to "`/api/v1/contents`",
                            "NOTE" to "`/api/v1/contents/{id}`",
                            "NOTE_CHANGES" to "`/api/v1/contents/changes`",
                            "JOURNALS" to "`/api/v1/journals`",
                            "JOURNAL" to "`/api/v1/journals/{id}`",
                            "JOURNAL_CHANGES" to "`/api/v1/journals/changes`",
                            "DRAFT" to "`/api/v1/drafts/{id}`",
                            "DRAFT_CHANGES" to "`/api/v1/drafts/changes`",
                            "ASSOCIATIONS" to "`/api/v1/associations`",
                            "ASSOCIATION_CHANGES" to "`/api/v1/associations/changes`",
                            "MEDIA" to "`/api/v1/media`",
                            "MEDIA_ITEM" to "`/api/v1/media/{id}`",
                            "MEDIA_BINARY" to "`/api/v1/media/{id}/binary`",
                            "BACKUPS" to "`/api/v1/backups`",
                            "BACKUP" to "`/api/v1/backups/{id}`",
                            "BACKUP_BINARY" to "`/api/v1/backups/{id}/binary`",
                            "DIAGNOSTIC_REPORTS" to "`/api/v1/diagnostics/reports`",
                            "DIAGNOSTIC_REPORT" to "`/api/v1/diagnostics/reports/{id}`",
                        ),
                ),
            "DiagnosticPlatform" to
                SchemaDoc(
                    "Which platform an event came from.",
                    enumValues =
                        mapOf(
                            "UNKNOWN" to "Not recorded.",
                            "ANDROID" to "The Android app.",
                            "IOS" to "The iOS app.",
                            "DESKTOP" to "The desktop app.",
                            "SERVER" to "The server.",
                        ),
                ),
            "DiagnosticProtocol" to
                SchemaDoc(
                    "A sync protocol feature.",
                    enumValues =
                        mapOf(
                            "SYNC_V1" to "Entry, journal and association sync.",
                            "RICH_DRAFTS_V1" to "Drafts with encrypted rich blocks.",
                            "MEDIA_V2" to "Current media upload and download.",
                            "DIAGNOSTICS_V1" to "Diagnostic reports.",
                        ),
                ),
            "DiagnosticNetworkState" to
                SchemaDoc(
                    "Whether the device was online.",
                    enumValues = mapOf("UNKNOWN" to "Not recorded.", "OFFLINE" to "No connection.", "ONLINE" to "Connected."),
                ),
            "DiagnosticSchedulerState" to
                SchemaDoc(
                    "Whether the background scheduler was running sync.",
                    enumValues =
                        mapOf("UNKNOWN" to "Not recorded.", "RUNNING" to "Sync was running.", "WAITING" to "Sync was waiting to run."),
                ),
            "DiagnosticComponent" to
                SchemaDoc(
                    "A part of the app, used in place of class and file names.",
                    enumValues =
                        mapOf(
                            "SYNC" to "Sync scheduling and records.",
                            "MEDIA" to "Media transfer.",
                            "ARCHIVE" to "Backup archives.",
                            "STORAGE" to "On-device storage.",
                            "NETWORK" to "Networking.",
                            "SERVER" to "The server.",
                        ),
                ),
        )
}
