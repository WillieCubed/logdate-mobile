package app.logdate.server

import app.logdate.server.config.profileAwareBoolEnv
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncPurgeResult
import app.logdate.server.sync.SyncRepository
import io.ktor.client.request.get
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationLifecycleBehaviorTest {
    @AfterTest
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `main can boot and stop immediately in non-wait mode`() {
        val previousWait = System.getProperty("LOGDATE_SERVER_WAIT")
        val previousPort = System.getProperty("PORT")
        val previousHost = System.getProperty("HOST")
        try {
            System.setProperty("LOGDATE_SERVER_WAIT", "false")
            System.setProperty("PORT", "0")
            System.setProperty("HOST", "127.0.0.1")
            main()
        } finally {
            if (previousWait ==
                null
            ) {
                System.clearProperty("LOGDATE_SERVER_WAIT")
            } else {
                System.setProperty("LOGDATE_SERVER_WAIT", previousWait)
            }
            if (previousPort == null) System.clearProperty("PORT") else System.setProperty("PORT", previousPort)
            if (previousHost == null) System.clearProperty("HOST") else System.setProperty("HOST", previousHost)
        }
    }

    @Test
    fun `profileAwareBoolEnv supports true yes and one and falls back to default`() {
        fun call(
            value: String?,
            default: Boolean,
        ): Boolean =
            profileAwareBoolEnv(
                name = "X",
                productionDefault = default,
                devDefault = default,
                readEnv = { name -> if (name == "X") value else null },
            )
        assertTrue(call("true", default = false))
        assertTrue(call("YES", default = false))
        assertTrue(call("1", default = false))
        assertFalse(call("no", default = true))
        assertTrue(call(null, default = true))
        assertFalse(call(null, default = false))
    }

    @Test
    fun `sync maintenance returns null when disabled`() =
        testApplication {
            lateinit var app: Application
            application { app = this }
            client.get("/")
            val metrics = SyncMetricsRegistry()
            val repository = InMemorySyncRepository()

            val job =
                startSyncMaintenance(
                    syncRepository = repository,
                    metrics = metrics,
                    readEnv = { name -> if (name == "SYNC_TOMBSTONE_PURGE_ENABLED") "false" else null },
                )
            assertNull(job)
        }

    @Test
    fun `sync maintenance runs purge and records success metrics`() =
        testApplication {
            lateinit var app: Application
            application { app = this }
            client.get("/")
            val metrics = SyncMetricsRegistry()
            val started = CompletableDeferred<Unit>()

            val repository =
                object : SyncRepository by InMemorySyncRepository() {
                    override fun purgeTombstonesOlderThan(olderThan: Long): SyncPurgeResult {
                        started.complete(Unit)
                        return SyncPurgeResult(1, 2, 3, 4, olderThan)
                    }
                }

            val job =
                startSyncMaintenance(
                    syncRepository = repository,
                    metrics = metrics,
                    readEnv = { name ->
                        when (name) {
                            "SYNC_TOMBSTONE_PURGE_ENABLED" -> "true"
                            "SYNC_TOMBSTONE_RETENTION_DAYS" -> "2"
                            "SYNC_TOMBSTONE_PURGE_INTERVAL_MINUTES" -> "1"
                            else -> null
                        }
                    },
                )
            assertNotNull(job)

            runBlocking {
                withTimeout(2_000) { started.await() }
                withTimeout(2_000) {
                    while (metrics.snapshot().operations.none { it.name == "maintenance.tombstone_purge" }) {
                        delay(10)
                    }
                }
            }
            runBlocking {
                job.cancel()
                job.join()
            }

            val op = metrics.snapshot().operations.first { it.name == "maintenance.tombstone_purge" }
            assertTrue(op.successCount >= 1)
        }

    @Test
    fun `sync maintenance records failed purge attempts`() =
        testApplication {
            lateinit var app: Application
            application { app = this }
            client.get("/")
            val metrics = SyncMetricsRegistry()
            val started = CompletableDeferred<Unit>()

            val repository =
                object : SyncRepository by InMemorySyncRepository() {
                    override fun purgeTombstonesOlderThan(olderThan: Long): SyncPurgeResult {
                        started.complete(Unit)
                        throw IllegalStateException("expected-failure")
                    }
                }

            val job =
                startSyncMaintenance(
                    syncRepository = repository,
                    metrics = metrics,
                    readEnv = { name ->
                        when (name) {
                            "SYNC_TOMBSTONE_PURGE_ENABLED" -> "true"
                            "SYNC_TOMBSTONE_RETENTION_DAYS" -> "2"
                            "SYNC_TOMBSTONE_PURGE_INTERVAL_MINUTES" -> "1"
                            else -> null
                        }
                    },
                )
            assertNotNull(job)

            runBlocking {
                withTimeout(2_000) { started.await() }
                withTimeout(2_000) {
                    while (metrics.snapshot().operations.none { it.name == "maintenance.tombstone_purge" }) {
                        delay(10)
                    }
                }
            }
            runBlocking {
                job.cancel()
                job.join()
            }

            val op = metrics.snapshot().operations.first { it.name == "maintenance.tombstone_purge" }
            assertTrue(op.errorCount >= 1)
        }

    @Test
    fun `sync maintenance can be cancelled immediately`() =
        testApplication {
            lateinit var app: Application
            application { app = this }
            client.get("/")
            val metrics = SyncMetricsRegistry()
            val repository = InMemorySyncRepository()

            val job =
                startSyncMaintenance(
                    syncRepository = repository,
                    metrics = metrics,
                    readEnv = { name ->
                        when (name) {
                            "SYNC_TOMBSTONE_PURGE_ENABLED" -> "true"
                            "SYNC_TOMBSTONE_PURGE_INTERVAL_MINUTES" -> "1"
                            else -> null
                        }
                    },
                )
            assertNotNull(job)

            runBlocking {
                job.cancel()
                job.join()
            }
        }
}
