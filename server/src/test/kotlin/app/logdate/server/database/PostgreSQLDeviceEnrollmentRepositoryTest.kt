package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.enrollment.DeviceEnrollment
import app.logdate.server.enrollment.EnrollmentStatus
import app.logdate.server.enrollment.SessionIssueResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLDeviceEnrollmentRepositoryTest {
    @Test
    fun `persistent transfer is account bound expiring and consumed once`() =
        withH2Database(AccountsTable, DeviceEnrollmentTable) {
            val owner = Uuid.random().toJavaUUID()
            val other = Uuid.random().toJavaUUID()
            transaction {
                for ((index, id) in listOf(owner, other).withIndex()) {
                    AccountsTable.insert {
                        it[AccountsTable.id] = id
                        it[username] = "device-enrollment-$index"
                        it[displayName] = "Test account $index"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
            }
            val repository = PostgreSQLDeviceEnrollmentRepository()
            val id = UUID.randomUUID()
            val now = System.currentTimeMillis()
            val enrollment = DeviceEnrollment(id, owner, "Mac", "public", "123456", now + 60_000, claimHash = "approve-claim")

            runBlocking {
                repository.create(enrollment)
                assertNull(repository.get(other, id, now))
                assertEquals(enrollment, repository.get(owner, id, now))
                assertFalse(repository.approve(other, id, "123456", "opaque", now))
                assertFalse(repository.approve(owner, id, "000000", "opaque", now))
                assertTrue(repository.approve(owner, id, "123456", "opaque", now))
                assertTrue(repository.approve(owner, id, "123456", "opaque", now))
                assertFalse(repository.approve(owner, id, "123456", "again", now))
                assertEquals("opaque", repository.claim("approve-claim", now)?.encryptedEnvelope)
                assertEquals("opaque", repository.claim("approve-claim", now)?.encryptedEnvelope)
                assertNull(repository.consume(other, id, now))
                assertEquals("opaque", repository.consume(owner, id, now))
                assertNull(repository.consume(owner, id, now))
                assertNull(repository.claim("approve-claim", now))

                val expired = enrollment.copy(id = UUID.randomUUID(), expiresAt = now - 1)
                repository.create(expired)
                assertNull(repository.get(owner, expired.id, now))
                assertFalse(repository.approve(owner, expired.id, "123456", "opaque", now))

                val rejected = enrollment.copy(id = UUID.randomUUID(), claimHash = "claim-hash")
                assertEquals(rejected, repository.create(rejected))
                assertEquals(rejected, repository.create(rejected.copy(id = UUID.randomUUID())))
                assertNull(repository.create(rejected.copy(id = UUID.randomUUID(), deviceName = "Another Mac")))
                assertFalse(repository.reject(other, rejected.id, now))
                assertTrue(repository.reject(owner, rejected.id, now))
                assertEquals(EnrollmentStatus.REJECTED, repository.get(owner, rejected.id, now)?.status)
                assertFalse(repository.approve(owner, rejected.id, "123456", "opaque", now))
                assertEquals(EnrollmentStatus.REJECTED, repository.claim("claim-hash", now)?.status)
                assertEquals(EnrollmentStatus.REJECTED, repository.claim("claim-hash", now)?.status)
                assertEquals(SessionIssueResult.NOT_PENDING, repository.markSessionIssued(owner, rejected.id, now))
            }
        }

    @Test
    fun `persistent session is issued once for a pending owned request`() =
        withH2Database(AccountsTable, DeviceEnrollmentTable) {
            val owner = Uuid.random().toJavaUUID()
            val other = Uuid.random().toJavaUUID()
            transaction {
                for ((index, id) in listOf(owner, other).withIndex()) {
                    AccountsTable.insert {
                        it[AccountsTable.id] = id
                        it[username] = "enrollment-session-$index"
                        it[displayName] = "Test account $index"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
            }
            val repository = PostgreSQLDeviceEnrollmentRepository()
            val now = System.currentTimeMillis()
            val enrollment = DeviceEnrollment(UUID.randomUUID(), owner, "Mac", "public", "123456", now + 60_000)

            runBlocking {
                repository.create(enrollment)
                assertEquals(SessionIssueResult.NOT_FOUND, repository.markSessionIssued(other, enrollment.id, now))
                assertEquals(SessionIssueResult.NOT_FOUND, repository.markSessionIssued(owner, enrollment.id, now + 60_000))
                assertEquals(SessionIssueResult.ISSUED, repository.markSessionIssued(owner, enrollment.id, now))
                assertEquals(SessionIssueResult.ALREADY_ISSUED, repository.markSessionIssued(owner, enrollment.id, now))
                assertTrue(repository.get(owner, enrollment.id, now)?.sessionIssued == true)
                assertTrue(repository.approve(owner, enrollment.id, "123456", "opaque", now))
                assertEquals(SessionIssueResult.ALREADY_ISSUED, repository.markSessionIssued(owner, enrollment.id, now))
            }
        }
}
