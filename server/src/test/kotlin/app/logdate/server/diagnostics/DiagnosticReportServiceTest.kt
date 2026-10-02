@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.diagnostics

import app.logdate.server.crypto.EncryptionKey
import app.logdate.server.crypto.EncryptionKeyring
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlinx.coroutines.test.runTest
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DiagnosticReportServiceTest {
    private val alice = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val bob = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val key = EncryptionKey("report-key", ByteArray(32) { it.toByte() })
    private val keyring =
        object : EncryptionKeyring {
            override fun getActiveKey(): EncryptionKey = key

            override fun getKey(keyId: String): EncryptionKey? = key.takeIf { it.keyId == keyId }
        }

    @Test
    fun `reports are encrypted at rest and scoped to their owner`() =
        runTest {
            val store = InMemoryDiagnosticReportStore()
            val service = DiagnosticReportService(store, keyring) { 1_000L }
            val report = report()
            val wire = DiagnosticReportCodec.encode(report)
            val id = Uuid.parse(report.reportId)

            assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, wire))
            val stored = assertNotNull(store.find(alice, id, 1_000L))
            assertFalse(stored.encryptedPayload.contentEquals(wire.encodeToByteArray()))
            assertFalse(stored.encryptedPayload.decodeToString().contains("reportId"))
            assertFalse(stored.encryptedFingerprint.contentEquals(MessageDigest.getInstance("SHA-256").digest(wire.encodeToByteArray())))
            assertEquals(report, service.read(alice, id))
            assertNull(service.read(bob, id))
            assertFalse(service.delete(bob, id))
            assertEquals(report, service.read(alice, id))
            assertTrue(service.delete(alice, id))
            assertNull(service.read(alice, id))
        }

    @Test
    fun `same random report id is idempotent and cannot overwrite first payload`() =
        runTest {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring) { 1_000L }
            val original = report()
            val changed = original.copy(droppedEvents = 17)
            val id = Uuid.parse(original.reportId)

            assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, DiagnosticReportCodec.encode(original)))
            assertEquals(DiagnosticSaveResult.EXISTING, service.create(alice, DiagnosticReportCodec.encode(original)))
            assertEquals(DiagnosticSaveResult.CONFLICT, service.create(alice, DiagnosticReportCodec.encode(changed)))
            assertEquals(original, service.read(alice, id))
            assertEquals(1, service.list(alice).size)
        }

    @Test
    fun `daily limit retention and seven day expiry are enforced`() =
        runTest {
            var time = 10_000L
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring) { time }
            val day = 24 * 60 * 60 * 1_000L
            val first = report()

            assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, DiagnosticReportCodec.encode(first)))
            repeat(19) { assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, DiagnosticReportCodec.encode(report()))) }
            assertEquals(DiagnosticSaveResult.DAILY_LIMIT, service.create(alice, DiagnosticReportCodec.encode(report())))
            assertEquals(20, service.list(alice).size)

            repeat(5) {
                time += day
                repeat(20) { assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, DiagnosticReportCodec.encode(report()))) }
            }
            assertEquals(100, service.list(alice).size)
            assertNull(service.read(alice, Uuid.parse(first.reportId)))
            assertEquals(DiagnosticSaveResult.EXISTING, service.create(alice, DiagnosticReportCodec.encode(first)))
            assertNull(service.read(alice, Uuid.parse(first.reportId)), "An idempotent retry must not restore an evicted body")
            assertEquals(DiagnosticSaveResult.CONFLICT, service.create(alice, DiagnosticReportCodec.encode(first.copy(droppedEvents = 1))))

            time += 8 * day
            assertTrue(service.list(alice).isEmpty())
            service.purgeExpired()
        }

    @Test
    fun `deleting evicted reports removes fingerprints and prevents resurrection`() =
        runTest {
            var time = 10_000L
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring) { time }
            val originals = List(20) { report() }
            originals.forEach { service.create(alice, DiagnosticReportCodec.encode(it)) }
            repeat(5) {
                time += DiagnosticReportService.DAY_MS
                repeat(20) { service.create(alice, DiagnosticReportCodec.encode(report())) }
            }
            val first = originals.first()
            assertNull(service.read(alice, Uuid.parse(first.reportId)))
            assertTrue(service.delete(alice, Uuid.parse(first.reportId)))
            assertFalse(service.delete(alice, Uuid.parse(first.reportId)))
            assertEquals(DiagnosticSaveResult.CONFLICT, service.create(alice, DiagnosticReportCodec.encode(first)))

            assertEquals(100, service.deleteAll(alice))
            originals.forEach {
                assertEquals(DiagnosticSaveResult.CONFLICT, service.create(alice, DiagnosticReportCodec.encode(it)))
            }
            assertTrue(service.list(alice).isEmpty())
        }

    @Test
    fun `fingerprints support retained keys and fail closed when a prior key is missing`() =
        runTest {
            var active = key
            val keys = mutableMapOf(key.keyId to key)
            val rotating =
                object : EncryptionKeyring {
                    override fun getActiveKey() = active

                    override fun getKey(keyId: String) = keys[keyId]
                }
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), rotating) { 1_000L }
            val original = report()
            val payload = DiagnosticReportCodec.encode(original)
            assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, payload))

            active = EncryptionKey("new-key", ByteArray(32) { (it + 1).toByte() })
            keys[active.keyId] = active
            assertEquals(DiagnosticSaveResult.EXISTING, service.create(alice, payload))
            assertEquals(original, service.read(alice, Uuid.parse(original.reportId)))

            keys.remove(key.keyId)
            assertFailsWith<app.logdate.server.crypto.EncryptionException> { service.create(alice, payload) }
            assertEquals(1, service.list(alice).size)
        }

    @Test
    fun `damaged retained fingerprints fail closed without accepting another upload`() =
        runTest {
            val backing = InMemoryDiagnosticReportStore()
            var damage = false
            val store =
                object : DiagnosticReportStore by backing {
                    override suspend fun save(
                        owner: Uuid,
                        report: StoredDiagnosticReport,
                        matchesFingerprint: (ByteArray) -> Boolean,
                    ): DiagnosticSaveResult =
                        backing.save(owner, report) { original ->
                            val candidate = original.copyOf()
                            if (damage) candidate[candidate.lastIndex] = (candidate.last().toInt() xor 1).toByte()
                            matchesFingerprint(candidate)
                        }
                }
            val service = DiagnosticReportService(store, keyring) { 1_000L }
            val original = report()
            val payload = DiagnosticReportCodec.encode(original)
            assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, payload))
            damage = true
            assertFailsWith<java.security.GeneralSecurityException> { service.create(alice, payload) }
            assertEquals(1, service.list(alice).size)
            assertEquals(original, service.read(alice, Uuid.parse(original.reportId)))
            damage = false
            assertEquals(DiagnosticSaveResult.EXISTING, service.create(alice, payload))
        }

    @Test
    fun `invalid and oversized reports never reach storage`() =
        runTest {
            val store = InMemoryDiagnosticReportStore()
            val service = DiagnosticReportService(store, keyring)
            assertFailsWith<IllegalArgumentException> {
                service.create(
                    alice,
                    """{"reportId":"${Uuid.random()}","events":[],"secret":"private"}""",
                )
            }
            assertFailsWith<IllegalArgumentException> { service.create(alice, "x".repeat(DiagnosticReportCodec.MAX_REPORT_BYTES + 1)) }
            assertTrue(store.list(alice, System.currentTimeMillis()).isEmpty())
        }

    @Test
    fun `deleting reports does not reset the daily upload allowance`() =
        runTest {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring) { 1_000L }
            repeat(20) { assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, DiagnosticReportCodec.encode(report()))) }

            assertEquals(20, service.deleteAll(alice))
            assertTrue(service.list(alice).isEmpty())
            assertEquals(DiagnosticSaveResult.DAILY_LIMIT, service.create(alice, DiagnosticReportCodec.encode(report())))
        }

    @Test
    fun `a deleted report id cannot be replayed within its retention window`() =
        runTest {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring) { 1_000L }
            val report = report()
            val id = Uuid.parse(report.reportId)
            val payload = DiagnosticReportCodec.encode(report)

            assertEquals(DiagnosticSaveResult.CREATED, service.create(alice, payload))
            assertTrue(service.delete(alice, id))
            assertEquals(DiagnosticSaveResult.CONFLICT, service.create(alice, payload))
            assertNull(service.read(alice, id))
        }

    @Test
    fun `server requires a random version four report reference`() =
        runTest {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring)
            val predictable = report().copy(reportId = "11111111-1111-1111-1111-111111111111")

            assertFailsWith<IllegalArgumentException> { service.create(alice, DiagnosticReportCodec.encode(predictable)) }
        }

    private fun report() =
        SyncDiagnosticReport(
            reportId = Uuid.random().toString(),
            events = listOf(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED)),
        )
}
