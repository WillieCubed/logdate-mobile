package app.logdate.shared.model.diagnostics

import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticPhase { SCHEDULING, IDENTITY, UPLOAD, FETCH, PERSIST, APPLY, MEDIA, ARCHIVE, RESTORE, RECOVERY }

@Serializable
enum class DiagnosticOutcome { QUEUED, STARTED, SUCCEEDED, FAILED, RETRY_SCHEDULED, INTERRUPTED, CONFLICT, PAUSED }

@Serializable
enum class DiagnosticReason {
    NONE,
    OFFLINE,
    SIGN_IN_REQUIRED,
    SERVER_UNAVAILABLE,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    LOCAL_STORAGE,
    MISSING_MEDIA,
    CORRUPT_PAYLOAD,
    KEY_RECOVERY_REQUIRED,
    INCOMPATIBLE_SERVER,
    PERSISTENCE_FAILED,
    UNSUPPORTED_FORMAT,
    CONFLICT,
    UNKNOWN,
}

@Serializable
enum class DiagnosticAction {
    NONE,
    RETRY,
    CONNECT,
    SIGN_IN,
    FREE_SPACE,
    RECOVER_KEY,
    UPDATE_SERVER,
    UPDATE_APP,
    REVIEW_CONFLICT,
    CONTACT_SUPPORT,
}

/** Only finite classifications, counters and random correlation references cross this boundary. */
@Serializable
data class SyncDiagnosticEvent(
    val phase: DiagnosticPhase,
    val outcome: DiagnosticOutcome,
    val reason: DiagnosticReason = DiagnosticReason.NONE,
    val action: DiagnosticAction = DiagnosticAction.NONE,
    val elapsedMs: Long = 0,
    val runId: String? = null,
    val operationId: String? = null,
    val attemptId: String? = null,
    val requestId: String? = null,
    val recordAlias: String? = null,
    val attemptCount: Int = 0,
    val pendingCount: Int = 0,
    val durationMs: Long = 0,
    val bytes: Long = 0,
    val httpStatus: Int? = null,
    val cursorAdvanced: Boolean = false,
    val retryable: Boolean = false,
    val schemaVersion: Int = 1,
    val code: DiagnosticCode = DiagnosticCode.PHASE_TRANSITION,
    val context: DiagnosticContext? = null,
    val frames: List<DiagnosticFrame> = emptyList(),
    val route: DiagnosticRoute = DiagnosticRoute.UNKNOWN,
)

@Serializable
data class SyncDiagnosticReport(
    val reportId: String,
    val schemaVersion: Int = 1,
    val events: List<SyncDiagnosticEvent> = emptyList(),
    val droppedEvents: Int = 0,
)
