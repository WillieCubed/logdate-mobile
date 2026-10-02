@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.diagnostics

import app.logdate.server.crypto.EncryptionKey
import app.logdate.server.crypto.EncryptionKeyring
import app.logdate.server.database.AccountsTable
import app.logdate.server.database.DatabaseConfig
import app.logdate.server.database.DiagnosticReportUploadsTable
import app.logdate.server.database.DiagnosticReportsTable
import app.logdate.server.database.PostgreSQLDiagnosticReportStore
import app.logdate.server.database.toJavaUUID
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Runs only against an explicitly supplied disposable local PostgreSQL fixture. */
class DiagnosticReportPostgresTest {
    @Test
    fun `migrations persistence concurrent quotas and account deletion hold in PostgreSQL`() =
        runTest {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("127.0.0.1", "localhost")) { "A loopback test database is required" }
            val previous = TransactionManager.defaultDatabase

            fun open(): HikariDataSource {
                val dataSource = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
                try {
                    TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(dataSource, autoMigrate = true)
                    return dataSource
                } catch (failure: Exception) {
                    dataSource.close()
                    throw failure
                }
            }
            var source = open()
            val owner = Uuid.random()
            val other = Uuid.random()
            val key = EncryptionKey("disposable-test-key", ByteArray(32) { (it + 1).toByte() })
            val keyring =
                object : EncryptionKeyring {
                    override fun getActiveKey() = key

                    override fun getKey(keyId: String) = key.takeIf { it.keyId == keyId }
                }
            var time = 1_000_000L

            fun service() = DiagnosticReportService(PostgreSQLDiagnosticReportStore(), keyring) { time }
            try {
                transaction {
                    AccountsTable.insert {
                        it[id] = owner.toJavaUUID()
                        it[username] = "diag" + owner.toString().replace("-", "").take(20)
                        it[displayName] = "Disposable diagnostic fixture"
                        it[createdAt] = Instant.fromEpochMilliseconds(1_000_000L)
                    }
                }
                val report = SyncDiagnosticReport(Uuid.random().toString())
                val wire = DiagnosticReportCodec.encode(report)
                assertEquals(DiagnosticSaveResult.CREATED, service().create(owner, wire))
                assertNull(service().read(other, Uuid.parse(report.reportId)))
                transaction {
                    val bytes =
                        DiagnosticReportsTable
                            .selectAll()
                            .where { DiagnosticReportsTable.accountId eq owner.toJavaUUID() }
                            .single()[DiagnosticReportsTable.encryptedPayload]
                    assertFalse(bytes.contentEquals(wire.encodeToByteArray()))
                    val fingerprint =
                        DiagnosticReportUploadsTable
                            .selectAll()
                            .where { DiagnosticReportUploadsTable.accountId eq owner.toJavaUUID() }
                            .single()[DiagnosticReportUploadsTable.encryptedFingerprint]
                    assertFalse(
                        requireNotNull(
                            fingerprint,
                        ).contentEquals(
                            java.security.MessageDigest
                                .getInstance("SHA-256")
                                .digest(wire.encodeToByteArray()),
                        ),
                    )
                }
                source.close()
                source = open()
                assertEquals(report, service().read(owner, Uuid.parse(report.reportId)))
                assertEquals(DiagnosticSaveResult.EXISTING, service().create(owner, wire))
                val results =
                    coroutineScope {
                        List(25) {
                            async(Dispatchers.IO) {
                                service().create(owner, DiagnosticReportCodec.encode(SyncDiagnosticReport(Uuid.random().toString())))
                            }
                        }.awaitAll()
                    }
                assertEquals(19, results.count { it == DiagnosticSaveResult.CREATED })
                assertEquals(6, results.count { it == DiagnosticSaveResult.DAILY_LIMIT })
                repeat(5) {
                    time += DiagnosticReportService.DAY_MS
                    repeat(20) {
                        assertEquals(
                            DiagnosticSaveResult.CREATED,
                            service().create(owner, DiagnosticReportCodec.encode(SyncDiagnosticReport(Uuid.random().toString()))),
                        )
                    }
                }
                assertEquals(100, service().list(owner).size)
                assertNull(service().read(owner, Uuid.parse(report.reportId)))
                assertEquals(DiagnosticSaveResult.EXISTING, service().create(owner, wire))
                assertEquals(
                    DiagnosticSaveResult.CONFLICT,
                    service().create(owner, DiagnosticReportCodec.encode(report.copy(droppedEvents = 1))),
                )
                kotlin.test.assertTrue(service().delete(owner, Uuid.parse(report.reportId)))
                assertEquals(DiagnosticSaveResult.CONFLICT, service().create(owner, wire))
                service().deleteAll(owner)
                assertEquals(DiagnosticSaveResult.CONFLICT, service().create(owner, wire))
                transaction {
                    DiagnosticReportUploadsTable
                        .selectAll()
                        .where { DiagnosticReportUploadsTable.accountId eq owner.toJavaUUID() }
                        .forEach { assertNull(it[DiagnosticReportUploadsTable.encryptedFingerprint]) }
                }
                assertEquals(
                    DiagnosticSaveResult.DAILY_LIMIT,
                    service().create(owner, DiagnosticReportCodec.encode(SyncDiagnosticReport(Uuid.random().toString()))),
                )
                transaction {
                    AccountsTable.deleteWhere { id eq owner.toJavaUUID() }
                    assertEquals(
                        0L,
                        DiagnosticReportsTable.selectAll().where { DiagnosticReportsTable.accountId eq owner.toJavaUUID() }.count(),
                    )
                    assertEquals(
                        0L,
                        DiagnosticReportUploadsTable
                            .selectAll()
                            .where {
                                DiagnosticReportUploadsTable.accountId eq
                                    owner.toJavaUUID()
                            }.count(),
                    )
                }
            } finally {
                transaction { AccountsTable.deleteWhere { id eq owner.toJavaUUID() } }
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
