package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.passkeys.InMemoryRestoreCredentialRepository
import app.logdate.server.passkeys.RestoreCredentialRepository
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class RestoreCredentialConsumptionRepositoryTest {
    @Test
    fun `both stores consume a credential once under contention`() =
        withStores { repository, owner ->
            val id = Uuid.random().toString()
            assertTrue(repository.store(owner, id, byteArrayOf(1, 2, 3), 0))
            val expected = assertNotNull(repository.findByCredentialId(id))
            val barrier = CyclicBarrier(2)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val results =
                    (1..2)
                        .map {
                            executor.submit<Boolean> {
                                barrier.await(5, TimeUnit.SECONDS)
                                runBlocking { repository.consume(expected, 1) }
                            }
                        }.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it })
                assertNull(repository.findByCredentialId(id))
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `both stores reject a changed verified record without consuming its replacement`() =
        withStores { repository, owner ->
            val id = Uuid.random().toString()
            assertTrue(repository.store(owner, id, byteArrayOf(1, 2, 3), 0))
            val expected = assertNotNull(repository.findByCredentialId(id))
            assertFalse(repository.consume(expected.copy(userId = Uuid.random()), 1))
            assertFalse(repository.consume(expected.copy(publicKey = byteArrayOf(4, 5, 6)), 1))
            assertFalse(repository.consume(expected.copy(signCount = 1), 2))
            assertNotNull(repository.findByCredentialId(id))
            assertTrue(repository.consume(expected, 1))
        }

    @Test
    fun `both stores refuse consumption after account-wide revocation`() =
        withStores { repository, owner ->
            val id = Uuid.random().toString()
            assertTrue(repository.store(owner, id, byteArrayOf(1, 2, 3), 0))
            val expected = assertNotNull(repository.findByCredentialId(id))
            assertTrue(repository.deactivateAllForUser(owner))
            assertFalse(repository.consume(expected, 1))
        }

    private fun withStores(block: suspend (RestoreCredentialRepository, Uuid) -> Unit) =
        withH2Database(AccountsTable, RestoreCredentialsTable) {
            val owner = Uuid.random()
            transaction {
                AccountsTable.insert {
                    it[id] = owner.toJavaUUID()
                    it[username] = "restore-consumption"
                    it[displayName] = "Restore fixture"
                    it[createdAt] = Clock.System.now()
                    it[isActive] = true
                    it[preferences] = "{}"
                }
            }
            runBlocking {
                block(PostgreSQLRestoreCredentialRepository(), owner)
                block(InMemoryRestoreCredentialRepository(), owner)
            }
        }
}
