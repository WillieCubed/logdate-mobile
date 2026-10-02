package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UploadDiagnosticInstrumentationTest {
    private val pending =
        PendingUpload(
            Uuid.random().toString(),
            PendingOperation.CREATE,
            scope = UploadScope("owner", "origin"),
            operationId = Uuid.random().toString(),
        )
    private val metadata =
        object : SyncMetadataService by fakeSyncMetadataService() {
            override suspend fun isCurrentOperation(
                entityType: EntityType,
                pending: PendingUpload,
            ) = true

            override suspend fun incrementRetryIfCurrent(
                entityType: EntityType,
                pending: PendingUpload,
            ) = true

            override suspend fun settleIfCurrent(
                entityType: EntityType,
                pending: PendingUpload,
                syncedAt: Instant,
                version: Long,
            ) = true
        }

    @Test
    fun `retry and settlement keep durable operation while each attempt captures its own epoch`() =
        runTest {
            val firstSource = DiagnosticSource(requireNotNull(pending.scope), "epoch-one")
            val secondSource = DiagnosticSource(requireNotNull(pending.scope), "epoch-two")
            var currentSource = firstSource
            val events = mutableListOf<Pair<SyncDiagnosticEvent, DiagnosticSource?>>()
            val schedule = InMemorySyncRetryScheduleStore()

            fun coordinator() =
                SyncRetryCoordinator(
                    schedule,
                    metadata,
                    InMemorySyncDeadLetterStore(),
                    SyncBackoff(),
                    { _, _, _, _, _, _, _ -> },
                    { currentSource },
                    { event, source -> events += event to source },
                )
            val first = coordinator()
            assertEquals(null, first.beginUpload(EntityType.NOTE, pending))
            currentSource = secondSource
            first.handleRetryFailure(EntityType.NOTE, pending, CloudApiException("UNAVAILABLE", "private server address", 503))
            val started = events.single { it.first.phase == DiagnosticPhase.UPLOAD && it.first.outcome == DiagnosticOutcome.STARTED }
            val retry = events.single { it.first.phase == DiagnosticPhase.UPLOAD && it.first.outcome == DiagnosticOutcome.RETRY_SCHEDULED }
            assertTrue(started.second === firstSource)
            assertTrue(retry.second === firstSource, "a consent change during upload reattributed its failure")
            assertEquals(pending.operationId, started.first.operationId)
            assertEquals(pending.operationId, retry.first.operationId)
            assertEquals(started.first.attemptId, retry.first.attemptId)
            assertEquals(DiagnosticReason.SERVER_UNAVAILABLE, retry.first.reason)
            assertEquals(DiagnosticAction.RETRY, retry.first.action)
            assertNotNull(started.first.attemptId)

            val next = coordinator()
            assertEquals(null, next.beginUpload(EntityType.NOTE, pending.copy(retryCount = 1)))
            next.markUploadSettled(EntityType.NOTE, pending.copy(retryCount = 1), Instant.fromEpochMilliseconds(10), 2)
            val success = events.single { it.first.phase == DiagnosticPhase.UPLOAD && it.first.outcome == DiagnosticOutcome.SUCCEEDED }
            assertTrue(success.second === secondSource)
            assertEquals(pending.operationId, success.first.operationId)
            assertNotNull(success.first.attemptId)
            assertTrue(success.first.attemptId != started.first.attemptId)
        }

    @Test
    fun `abandoned upload reports interruption and a failing diagnostic sink cannot alter settlement`() =
        runTest {
            val events = mutableListOf<SyncDiagnosticEvent>()
            val coordinator =
                SyncRetryCoordinator(
                    InMemorySyncRetryScheduleStore(),
                    metadata,
                    InMemorySyncDeadLetterStore(),
                    SyncBackoff(),
                    { _, _, _, _, _, _, _ -> },
                    { DiagnosticSource(requireNotNull(pending.scope), "epoch") },
                    { event, _ -> events += event },
                )
            coordinator.beginUpload(EntityType.NOTE, pending)
            coordinator.abandonAttemptsInFlight()
            val start = events.single { it.outcome == DiagnosticOutcome.STARTED }
            val interrupted = events.single { it.outcome == DiagnosticOutcome.INTERRUPTED }
            assertEquals(start.operationId, interrupted.operationId)
            assertEquals(start.attemptId, interrupted.attemptId)

            val throwing =
                SyncRetryCoordinator(
                    InMemorySyncRetryScheduleStore(),
                    metadata,
                    InMemorySyncDeadLetterStore(),
                    SyncBackoff(),
                    { _, _, _, _, _, _, _ -> },
                    { DiagnosticSource(requireNotNull(pending.scope), "epoch") },
                    { _, _ -> error("diagnostic storage failed") },
                )
            assertEquals(null, throwing.beginUpload(EntityType.NOTE, pending))
            throwing.markUploadSettled(EntityType.NOTE, pending, Instant.fromEpochMilliseconds(10), 2)
        }
}
