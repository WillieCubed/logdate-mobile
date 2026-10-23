package app.logdate.server

import app.logdate.server.logdate.InMemoryLogDateBlobStorage
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logging.SpaceMigrationNotices
import app.logdate.server.logging.safeServerMessage
import app.logdate.server.migration.MigrationFixture
import app.logdate.server.migration.legacyCount
import app.logdate.server.migration.switchedAccount
import app.logdate.server.space.InMemorySpaceStore
import app.logdate.server.space.SpacePurgeResult
import app.logdate.server.space.SpaceStore
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.LegacyRecordKinds
import app.logdate.shared.model.sync.SpaceMigrationPhase
import io.github.aakira.napier.LogLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Deleting moved journals' legacy copies can be paused on its own, while the rest of maintenance keeps running. */
class SyncMaintenanceOldDataPurgeTest {
    @Test
    fun `the old data purge runs unless it is set to anything but true`() {
        assertTrue(oldDataPurgeEnabled { null })
        assertTrue(oldDataPurgeEnabled { "true" })
        assertFalse(oldDataPurgeEnabled { "false" })
        assertFalse(oldDataPurgeEnabled { "flase" }, "A mistyped value keeps the data")
    }

    @Test
    fun `maintenance deletes a legacy copy 30 days after its switch by default`() =
        runBlocking {
            val fixture = MigrationFixture()
            val account = fixture.switchedAccount()
            fixture.clock.advance(31.days)

            val job = maintenance(fixture, readEnv = { null }, swept = CompletableDeferred())
            try {
                withTimeout(5_000) {
                    while (fixture.service.status(account).phase != SpaceMigrationPhase.CLEANED) delay(25)
                }
                assertEquals(0, fixture.legacyCount(account, LegacyRecordKinds.ENTRIES))
            } finally {
                job.cancel()
                job.join()
            }
        }

    @Test
    fun `pausing the old data purge keeps legacy copies while the space purge still runs`() =
        runBlocking {
            val fixture = MigrationFixture()
            val account = fixture.switchedAccount()
            fixture.clock.advance(31.days)
            val swept = CompletableDeferred<Unit>()

            val job = maintenance(fixture, readEnv = { name -> if (name == OLD_DATA_PURGE_ENABLED) "false" else null }, swept = swept)
            try {
                withTimeout(5_000) { swept.await() }
                // The cleanup would run right after the space purge in the same pass.
                delay(500)
                assertEquals(SpaceMigrationPhase.SWITCHED, fixture.service.status(account).phase)
                assertEquals(1, fixture.legacyCount(account, LegacyRecordKinds.ENTRIES))
            } finally {
                job.cancel()
                job.join()
            }
        }

    @Test
    fun `a paused old data purge logs its own code`() {
        assertEquals(
            "SERVER_LEGACY_COPY_CLEANUP_PAUSED",
            safeServerMessage(LogLevel.WARNING, tag = null, message = SpaceMigrationNotices.CLEANUP_PAUSED),
        )
    }

    private fun maintenance(
        fixture: MigrationFixture,
        readEnv: (String) -> String?,
        swept: CompletableDeferred<Unit>,
    ): Job {
        val spaces =
            object : SpaceStore by InMemorySpaceStore() {
                override suspend fun purge(
                    tombstonesBefore: Instant,
                    unreferencedBlobsBefore: Instant,
                    deleteObject: (storagePath: String) -> Unit,
                ): SpacePurgeResult {
                    swept.complete(Unit)
                    return SpacePurgeResult(blobsDeleted = 0, blobsFailed = 0)
                }
            }
        return requireNotNull(
            startSyncMaintenance(
                collectionsRepository = InMemorySyncRepository().asLogDateCollectionsRepository(),
                metrics = SyncMetricsRegistry(),
                readEnv = readEnv,
                spaceStore = spaces,
                blobStorage = InMemoryLogDateBlobStorage(),
                legacyCleanup = fixture.cleanup,
            ),
        )
    }
}
