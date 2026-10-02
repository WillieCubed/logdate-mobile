package app.logdate.client.sync.cloud

import app.logdate.client.sync.diagnostics.DiagnosticCorrelation
import app.logdate.client.sync.diagnostics.SuppressDiagnosticReporting
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.DiagnosticRoute
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.diagnosticRequestPhase
import app.logdate.shared.model.diagnostics.diagnosticRoute
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Transport diagnostics accept only the shared allowlist, never URL or response content. */
internal class SafeCloudTransport(
    private val client: HttpClient,
    private val record: (SyncDiagnosticEvent) -> Unit = {},
    private val source: suspend () -> app.logdate.client.sync.diagnostics.DiagnosticSource? = { null },
    private val scopedRecord: (SyncDiagnosticEvent, app.logdate.client.sync.diagnostics.DiagnosticSource?) -> Unit = { _, _ -> },
) {
    suspend fun get(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = request(url, HttpMethod.Get, block)

    suspend fun post(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = request(url, HttpMethod.Post, block)

    suspend fun put(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = request(url, HttpMethod.Put, block)

    suspend fun patch(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = request(url, HttpMethod.Patch, block)

    suspend fun delete(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = request(url, HttpMethod.Delete, block)

    private suspend fun request(
        url: String,
        method: HttpMethod,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        observe(route(url), method == HttpMethod.Get) { requestId, received ->
            client
                .request(url) {
                    this.method = method
                    block()
                    headers.remove("X-Request-ID")
                    header("X-Request-ID", requestId)
                }.also(received)
        }

    suspend fun <T> streamGet(
        url: String,
        block: HttpRequestBuilder.() -> Unit,
        consume: suspend (HttpResponse) -> T,
    ): T =
        observe(route(url), true) { requestId, received ->
            client
                .prepareGet(url) {
                    block()
                    headers.remove("X-Request-ID")
                    header("X-Request-ID", requestId)
                }.execute { response ->
                    val result = consume(response)
                    received(response)
                    result
                }
        }

    private fun route(url: String): DiagnosticRoute =
        runCatching { diagnosticRoute(Url(url).encodedPath) }.getOrDefault(DiagnosticRoute.UNKNOWN)

    private suspend fun <T> observe(
        route: DiagnosticRoute,
        read: Boolean,
        dispatch: suspend (String, (HttpResponse) -> Unit) -> T,
    ): T {
        val phase = diagnosticRequestPhase(route, read)
        val suppressed = currentCoroutineContext()[SuppressDiagnosticReporting.Key] != null
        val captured = if (suppressed) null else captureSource()

        fun publish(event: SyncDiagnosticEvent) {
            if (!suppressed) emit(event, captured)
        }
        val started = TimeSource.Monotonic.markNow()
        val correlation = currentCoroutineContext()[DiagnosticCorrelation]
        val event =
            SyncDiagnosticEvent(
                phase,
                DiagnosticOutcome.STARTED,
                route = route,
                runId = correlation?.runId,
                operationId = correlation?.operationId,
                requestId = Uuid.random().toString(),
                attemptId = Uuid.random().toString(),
            )
        publish(event)
        try {
            return dispatch(requireNotNull(event.requestId)) { response ->
                publish(responseEvent(event, response, started))
            }
        } catch (cancelled: CancellationException) {
            publish(event.copy(outcome = DiagnosticOutcome.INTERRUPTED))
            throw cancelled
        } catch (failure: Exception) {
            publish(
                event.copy(
                    outcome = DiagnosticOutcome.FAILED,
                    reason = DiagnosticReason.UNKNOWN,
                    action = DiagnosticAction.RETRY,
                    retryable = true,
                    durationMs = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0),
                ),
            )
            throw failure
        }
    }

    private suspend fun captureSource(): app.logdate.client.sync.diagnostics.DiagnosticSource? =
        try {
            source()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private fun responseEvent(
        event: SyncDiagnosticEvent,
        response: HttpResponse,
        started: TimeMark,
    ): SyncDiagnosticEvent {
        val reason = responseReason(response.status.value)
        return event.copy(
            outcome = if (reason == DiagnosticReason.NONE) DiagnosticOutcome.SUCCEEDED else DiagnosticOutcome.FAILED,
            reason = reason,
            action =
                when (reason) {
                    DiagnosticReason.NONE -> DiagnosticAction.NONE
                    DiagnosticReason.SIGN_IN_REQUIRED -> DiagnosticAction.SIGN_IN
                    DiagnosticReason.CONFLICT -> DiagnosticAction.REVIEW_CONFLICT
                    else -> DiagnosticAction.RETRY
                },
            httpStatus = response.status.value,
            retryable = reason == DiagnosticReason.SERVER_UNAVAILABLE || reason == DiagnosticReason.RATE_LIMITED,
            durationMs = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0),
        )
    }

    private fun responseReason(status: Int): DiagnosticReason =
        when (status) {
            in 200..299 -> DiagnosticReason.NONE
            401, 403 -> DiagnosticReason.SIGN_IN_REQUIRED
            409 -> DiagnosticReason.CONFLICT
            429 -> DiagnosticReason.RATE_LIMITED
            in 500..599 -> DiagnosticReason.SERVER_UNAVAILABLE
            else -> DiagnosticReason.UNKNOWN
        }

    private fun emit(
        event: SyncDiagnosticEvent,
        source: app.logdate.client.sync.diagnostics.DiagnosticSource?,
    ) {
        runCatching { record(event) }
        runCatching { scopedRecord(event, source) }
    }
}
