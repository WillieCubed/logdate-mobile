package app.logdate.server.routes

import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.AuthMetricsSnapshot
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncMetricsSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsSnapshotSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `auth metrics snapshot records operations errors and rate limits`() {
        val registry = AuthMetricsRegistry()
        registry.recordOperation("auth.signup.passkey.begin", durationMs = 10, success = true)
        registry.recordOperation("auth.signup.passkey.begin", durationMs = 5, success = false)
        registry.recordError("VALIDATION_ERROR")
        registry.recordRateLimit("auth.signup.passkey.begin")

        val snapshot = registry.snapshot()
        assertTrue(snapshot.generatedAt >= 0L)
        assertEquals(1L, snapshot.errorsByCode["VALIDATION_ERROR"])
        assertEquals(1L, snapshot.rateLimitedByOperation["auth.signup.passkey.begin"])
        assertEquals(1, snapshot.operations.size)

        val encoded = json.encodeToString(AuthMetricsSnapshot.serializer(), snapshot)
        assertTrue(encoded.contains("VALIDATION_ERROR"))
    }

    @Test
    fun `sync metrics snapshot records bytes and conflicts`() {
        val registry = SyncMetricsRegistry()
        registry.recordOperation("sync.media.upload", durationMs = 12, success = true, bytes = 128)
        registry.recordOperation("sync.media.upload", durationMs = 3, success = false, bytes = 0)
        registry.recordConflict()

        val snapshot = registry.snapshot()
        assertTrue(snapshot.generatedAt >= 0L)
        assertEquals(1L, snapshot.conflictCount)
        assertEquals(1, snapshot.operations.size)
        val op = snapshot.operations.first()
        assertEquals("sync.media.upload", op.name)
        assertEquals(1L, op.successCount)
        assertEquals(1L, op.errorCount)
        assertEquals(128L, op.totalBytes)

        val encoded = json.encodeToString(SyncMetricsSnapshot.serializer(), snapshot)
        assertTrue(encoded.contains("sync.media.upload"))
    }
}
