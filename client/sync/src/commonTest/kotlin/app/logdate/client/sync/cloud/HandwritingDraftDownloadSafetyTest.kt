package app.logdate.client.sync.cloud

import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingDraftDownloadSafetyTest {
    @Test
    fun richModalitiesWithoutOriginalBlocksAreQuarantinedWhileTextStillSyncs() =
        runTest {
            val missing = listOf("HANDWRITING", "IMAGE", "VIDEO", "AUDIO", "CAMERA", "FUTURE").map { Uuid.random() to it }
            val textId = Uuid.random()
            val api = FakeCloudApiClient()

            fun change(
                id: Uuid,
                type: String,
            ) = DraftChange(
                id = id.toString(),
                content = "legacy words",
                blockTypes = listOf(type),
                journalIds = emptyList(),
                createdAt = 1,
                lastUpdated = 1,
                deviceId = DeviceId("test-device"),
                serverVersion = 7,
            )
            api.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts = missing.map { (id, type) -> change(id, type) } + change(textId, "TEXT"),
                    ),
                )
            val changes = DefaultCloudDraftDataSource(api).getDraftChanges("token", Instant.fromEpochMilliseconds(0)).getOrThrow()
            assertEquals(listOf(textId), changes.changes.map { it.id }, "Missing rich blocks must not become text or empty drafts")
            assertEquals(
                missing.map { RemoteRecordFailure(it.first.toString(), 7, DiagnosticReason.UNSUPPORTED_FORMAT) },
                changes.failures,
            )
            assertEquals(emptyList(), changes.unreadable)
        }
}
