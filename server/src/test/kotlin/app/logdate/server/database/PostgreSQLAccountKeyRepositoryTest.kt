package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class PostgreSQLAccountKeyRepositoryTest {
    @Test
    fun `encrypted account key survives repository recreation and cannot be replaced`() =
        withH2Database(AccountsTable, AccountKeyVaultTable) {
            val owner = UUID.randomUUID()
            val other = UUID.randomUUID()
            transaction {
                listOf(owner, other).forEachIndexed { index, id ->
                    AccountsTable.insert {
                        it[AccountsTable.id] = id
                        it[username] = "account-key-$index"
                        it[displayName] = "Account $index"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
            }
            val first = PostgreSQLAccountKeyRepository()
            val ciphertext = ByteArray(70) { it.toByte() }
            runBlocking {
                assertTrue(first.insertIfAbsent(owner, ciphertext))
                assertFalse(first.insertIfAbsent(owner, ByteArray(70) { 0x55 }))
                assertContentEquals(ciphertext, PostgreSQLAccountKeyRepository().get(owner))
                assertNull(first.get(other))
            }
        }
}
