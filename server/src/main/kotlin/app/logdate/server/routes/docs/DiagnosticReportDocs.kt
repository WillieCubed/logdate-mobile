package app.logdate.server.routes.docs

import app.logdate.server.diagnostics.DiagnosticReportService
import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.created
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.sync.DiagnosticReportItem
import app.logdate.server.routes.sync.DiagnosticReportListing
import app.logdate.server.routes.sync.DiagnosticReportReference
import app.logdate.server.routes.syncError
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** Documentation for `DiagnosticReportRoutes.kt`. */
internal object DiagnosticReportDocs {
    private const val REPORT_ID = "3f6b1c2d-8e4a-4b7c-9d1e-2a3b4c5d6e7f"

    private val report =
        SyncDiagnosticReport(
            reportId = REPORT_ID,
            events =
                listOf(
                    SyncDiagnosticEvent(
                        phase = DiagnosticPhase.UPLOAD,
                        outcome = DiagnosticOutcome.FAILED,
                        reason = DiagnosticReason.OFFLINE,
                        action = DiagnosticAction.CONNECT,
                        attemptCount = 2,
                        pendingCount = 14,
                        retryable = true,
                    ),
                ),
        )

    private val unavailable =
        ErrorCase(
            "DIAGNOSTIC_REPORTS_UNAVAILABLE",
            "This server has not switched diagnostic reports on, so nothing can be sent or read. Check for " +
                "`diagnosticReportsV1` in **Describe this server** before offering reporting.",
            "Diagnostic reports are not enabled on this server",
        )
    private val storageFailed =
        ErrorCase("REPORT_STORAGE_FAILED", "The report store could not be reached. Retry later.", "Diagnostic report unavailable")
    private val notFound =
        ErrorCase(
            "REPORT_NOT_FOUND",
            "No retained report with that ID belongs to this account. Reports expire after seven days.",
            "Report not found",
        )

    private fun RequestConfig.reportId() {
        pathParameter<String>("reportId") {
            description = "The random report ID the client chose when it sent the report."
            example("Example") { value = REPORT_ID }
        }
    }

    private fun ResponsesConfig.unavailableOrFailed() {
        syncError(HttpStatusCode.InternalServerError, storageFailed)
        syncError(HttpStatusCode.ServiceUnavailable, unavailable)
    }

    val uploadReport: RouteConfig.() -> Unit = {
        bearerOperation(
            "uploadDiagnosticReport",
            ApiTags.DIAGNOSTICS,
            "Send a diagnostic report",
            """
            Stores a sync diagnostic report the person chose to share. A report holds only fixed event codes,
            counters and random correlation IDs: never journal content, file names, URLs, credentials or keys.
            The server rejects anything outside that schema.

            The client picks `reportId`, which makes the upload idempotent: sending the same report again answers
            `200` with the same reference, while reusing the ID for different data answers `409`.

            > [!NOTE]
            > Reports are limited to ${DiagnosticReportCodec.MAX_REPORT_BYTES / 1024} KiB each and
            > ${DiagnosticReportService.DAILY_LIMIT} per account per day. The server keeps at most
            > ${DiagnosticReportService.RETAINED_LIMIT} per account, for seven days.
            """,
        )
        request { jsonBody(report, "The report, exactly as the app's diagnostic export encodes it.") }
        response {
            created(
                "The report is stored.",
                DiagnosticReportReference(REPORT_ID),
                "/api/v1/diagnostics/reports/$REPORT_ID",
            )
            ok("This exact report was already stored; nothing changed.", DiagnosticReportReference(REPORT_ID))
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase("INVALID_REPORT", "The body is not a valid diagnostic report.", "Invalid diagnostic report"),
            )
            syncUnauthorized()
            syncError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "REPORT_ID_CONFLICT",
                    "A different report already used this ID. Pick a new random ID.",
                    "Report reference already used",
                ),
            )
            syncError(
                HttpStatusCode.PayloadTooLarge,
                ErrorCase("REPORT_TOO_LARGE", "The report is over the size limit.", "Diagnostic report too large"),
            )
            syncError(
                HttpStatusCode.TooManyRequests,
                ErrorCase(
                    "REPORT_RATE_LIMITED",
                    "This account sent its daily allowance of reports. Deleting reports does not reset it; try tomorrow.",
                    "Daily report limit reached",
                ),
            )
            unavailableOrFailed()
        }
    }

    val listReports: RouteConfig.() -> Unit = {
        bearerOperation(
            "listDiagnosticReports",
            ApiTags.DIAGNOSTICS,
            "List diagnostic reports",
            """
            Lists the reports this account sent that the server still keeps, so a person can see and delete
            what they shared. Expired reports are left out even before cleanup removes them.
            """,
        )
        response {
            ok(
                "The account's retained reports.",
                DiagnosticReportListing(listOf(DiagnosticReportItem(REPORT_ID, SyncExamples.SERVER_VERSION))),
            )
            syncUnauthorized()
            unavailableOrFailed()
        }
    }

    val getReport: RouteConfig.() -> Unit = {
        bearerOperation(
            "getDiagnosticReport",
            ApiTags.DIAGNOSTICS,
            "Read a diagnostic report",
            """
            Returns one retained report exactly as it was sent. Only the account that sent it can read it;
            there is no administrator or cross-account read.
            """,
        )
        request { reportId() }
        response {
            ok("The report.", report)
            syncUnauthorized()
            syncError(HttpStatusCode.NotFound, notFound)
            unavailableOrFailed()
        }
    }

    val deleteReport: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteDiagnosticReport",
            ApiTags.DIAGNOSTICS,
            "Delete a diagnostic report",
            """
            Deletes one report from online storage. Copies in infrastructure backups follow their own retention
            schedule.
            """,
        )
        request { reportId() }
        response {
            noContent("The report is gone.")
            syncUnauthorized()
            syncError(HttpStatusCode.NotFound, notFound)
            unavailableOrFailed()
        }
    }

    val deleteAllReports: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteAllDiagnosticReports",
            ApiTags.DIAGNOSTICS,
            "Delete all diagnostic reports",
            """
            Deletes every report this account sent from online storage. The daily allowance is not reset.
            """,
        )
        response {
            noContent("Every report is gone.")
            syncUnauthorized()
            unavailableOrFailed()
        }
    }
}
