@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.logging

import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.DiagnosticRoute
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.diagnosticRequestPhase
import app.logdate.shared.model.diagnostics.diagnosticRoute
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

private const val REQUEST_ID_HEADER = "X-Request-ID"

private data class RequestAttempt(
    val id: String,
    val startedAt: Long,
    val phase: DiagnosticPhase,
    val route: DiagnosticRoute,
    val failureRecorded: AtomicBoolean = AtomicBoolean(false),
)

private val requestAttempt = AttributeKey<RequestAttempt>("SafeRequestAttempt")
private val diagnosticObserver = AttributeKey<(SyncDiagnosticEvent) -> Unit>("SafeDiagnosticObserver")

private fun recordRequestDiagnostic(
    call: ApplicationCall,
    event: SyncDiagnosticEvent,
) {
    recordServerDiagnostic(event)
    runCatching {
        call.application.attributes
            .getOrNull(diagnosticObserver)
            ?.invoke(event)
    }
}

private val requestDiagnostics =
    createApplicationPlugin("RequestDiagnostics") {
        onCall { call ->
            val supplied = call.request.headers[REQUEST_ID_HEADER]
            val id = supplied?.takeIf(DiagnosticReportCodec::isCorrelationId) ?: Uuid.random().toString()
            val route = diagnosticRoute(call.request.path())
            val phase = diagnosticRequestPhase(route, call.request.httpMethod == HttpMethod.Get)
            call.attributes.put(requestAttempt, RequestAttempt(id, System.nanoTime(), phase, route))
            call.response.headers.append(REQUEST_ID_HEADER, id)
            recordRequestDiagnostic(call, SyncDiagnosticEvent(phase, DiagnosticOutcome.STARTED, requestId = id, route = route))
        }
        on(CallFailed) { call, cause ->
            val attempt = call.attributes.getOrNull(requestAttempt)
            if (attempt != null && attempt.failureRecorded.compareAndSet(false, true)) {
                val interrupted = cause is CancellationException
                recordRequestDiagnostic(
                    call,
                    SyncDiagnosticEvent(
                        phase = attempt.phase,
                        route = attempt.route,
                        outcome = if (interrupted) DiagnosticOutcome.INTERRUPTED else DiagnosticOutcome.FAILED,
                        reason = if (interrupted) DiagnosticReason.NONE else DiagnosticReason.UNKNOWN,
                        action = if (interrupted) DiagnosticAction.RETRY else DiagnosticAction.CONTACT_SUPPORT,
                        requestId = attempt.id,
                        durationMs = ((System.nanoTime() - attempt.startedAt) / 1_000_000).coerceAtLeast(0),
                        httpStatus = call.response.status()?.value,
                        retryable = interrupted,
                    ),
                )
            }
            if (cause is CancellationException) throw cause
            if (!call.response.isCommitted) {
                val status = if (cause is BadRequestException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                val code = if (status == HttpStatusCode.BadRequest) "INVALID_REQUEST" else "INTERNAL_ERROR"
                try {
                    call.respondText(
                        "{\"success\":false,\"error\":{\"code\":\"$code\",\"message\":\"Request could not be completed\"}}",
                        ContentType.Application.Json,
                        status,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw SafeRequestFailure()
                }
            }
            // Ktor rethrows the original exception when a failed response has not been sent.
            if (!call.response.isSent) throw SafeRequestFailure()
        }
        on(ResponseSent) { call ->
            val attempt = call.attributes.getOrNull(requestAttempt) ?: return@on
            if (attempt.failureRecorded.get()) return@on
            val status = call.response.status()?.value ?: return@on
            val reason =
                when (status) {
                    401, 403 -> DiagnosticReason.SIGN_IN_REQUIRED
                    429 -> DiagnosticReason.RATE_LIMITED
                    in 500..599 -> DiagnosticReason.SERVER_UNAVAILABLE
                    in 400..499 -> DiagnosticReason.UNKNOWN
                    else -> DiagnosticReason.NONE
                }
            recordRequestDiagnostic(
                call,
                SyncDiagnosticEvent(
                    phase = attempt.phase,
                    route = attempt.route,
                    outcome = if (status < 400) DiagnosticOutcome.SUCCEEDED else DiagnosticOutcome.FAILED,
                    reason = reason,
                    action =
                        when (reason) {
                            DiagnosticReason.SIGN_IN_REQUIRED -> DiagnosticAction.SIGN_IN
                            DiagnosticReason.SERVER_UNAVAILABLE, DiagnosticReason.RATE_LIMITED -> DiagnosticAction.RETRY
                            else -> DiagnosticAction.NONE
                        },
                    requestId = attempt.id,
                    durationMs = ((System.nanoTime() - attempt.startedAt) / 1_000_000).coerceAtLeast(0),
                    httpStatus = status,
                    retryable = status == 429 || status >= 500,
                ),
            )
        }
    }

private class SafeRequestFailure : RuntimeException("SERVER_REQUEST_FAILED")

internal fun Application.installRequestDiagnostics(onEvent: (SyncDiagnosticEvent) -> Unit = {}) {
    attributes.put(diagnosticObserver, onEvent)
    install(requestDiagnostics)
}
