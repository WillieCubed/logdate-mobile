package app.logdate.server.logdate

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupRetentionPolicyTest {
    @Test
    fun `keeps the latest N per device and purges the rest`() {
        val policy = BackupRetentionPolicy(keepPerDevice = 2, maxAgeMillis = Long.MAX_VALUE)
        val now = 1_000_000L
        val backups =
            listOf(
                backup("d1", createdAt = 5L),
                backup("d1", createdAt = 4L),
                backup("d1", createdAt = 3L),
                backup("d1", createdAt = 2L),
                backup("d2", createdAt = 10L),
                backup("d2", createdAt = 9L),
            )

        val purged = policy.backupsToPurge(backups, now)

        assertEquals(2, purged.size)
        assertTrue(purged.all { it.deviceId == "d1" && it.createdAt <= 3L })
    }

    @Test
    fun `purges anything past the age cutoff regardless of keep-per-device`() {
        val maxAge = 10L
        val policy = BackupRetentionPolicy(keepPerDevice = 10, maxAgeMillis = maxAge)
        val now = 100L // cutoff = now - maxAge = 90; anything createdAt < 90 is expired.
        val backups =
            listOf(
                backup("d1", createdAt = 95L),
                backup("d1", createdAt = 90L),
                backup("d1", createdAt = 80L),
                backup("d1", createdAt = 50L),
            )

        val purged = policy.backupsToPurge(backups, now)

        assertEquals(2, purged.size)
        assertEquals(listOf(50L, 80L), purged.map { it.createdAt }.sorted())
    }

    @Test
    fun `handles an empty list`() {
        val policy = BackupRetentionPolicy.DEFAULT
        assertEquals(emptyList(), policy.backupsToPurge(emptyList(), nowMillis = 0L))
    }

    @Test
    fun `keepPerDevice of zero deletes everything for that device`() {
        val policy = BackupRetentionPolicy(keepPerDevice = 0, maxAgeMillis = Long.MAX_VALUE)
        val backups = listOf(backup("d1", createdAt = 5L), backup("d1", createdAt = 3L))
        assertEquals(2, policy.backupsToPurge(backups, nowMillis = 100L).size)
    }

    @Test
    fun `default policy keeps 5 per device and 90 days`() {
        assertEquals(5, BackupRetentionPolicy.DEFAULT.keepPerDevice)
        assertEquals(90L * 24 * 60 * 60 * 1000, BackupRetentionPolicy.DEFAULT.maxAgeMillis)
    }

    private fun backup(deviceId: String, createdAt: Long): LogDateBackup =
        LogDateBackup(
            id = UUID.randomUUID(),
            userId = ACCOUNT,
            deviceId = deviceId,
            manifest = "{}",
            storagePath = "some/path",
            createdAt = createdAt,
            sizeBytes = 100L,
        )

    companion object {
        private val ACCOUNT = UUID.fromString("44444444-4444-4444-4444-444444444444")
    }
}
