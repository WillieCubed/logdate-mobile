package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.identity.StoredHostedPlcOperation
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLHostedPlcOperationRepositoryTest {
    @Test
    fun `postgres hosted plc operation repository stores and queries history`() =
        kotlinx.coroutines.test.runTest {
            withH2Database(AccountsTable, HostedPlcOperationsTable) {
                val repository = PostgreSQLHostedPlcOperationRepository()
                val accountId = Uuid.random()
                val did = "did:plc:ewvi7nxzyoun6zhxrhs64oiz"

                transaction {
                    AccountsTable.insert {
                        it[id] = accountId.toJavaUUID()
                        it[username] = "plc-history"
                        it[displayName] = "PLC History"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }

                val first =
                    StoredHostedPlcOperation(
                        id = Uuid.random(),
                        accountId = accountId,
                        did = did,
                        cid = "cid-1",
                        operationType = "plc_operation",
                        operationJson = """{"sig":"one"}""",
                    )
                val second =
                    StoredHostedPlcOperation(
                        id = Uuid.random(),
                        accountId = accountId,
                        did = did,
                        cid = "cid-2",
                        prevCid = "cid-1",
                        operationType = "plc_operation",
                        operationJson = """{"sig":"two"}""",
                    )

                kotlinx.coroutines.runBlocking {
                    repository.save(first)
                    repository.save(second)

                    val byAccount = repository.listByAccountId(accountId)
                    val byDid = repository.listByDid(did)

                    assertEquals(listOf(first, second).map(StoredHostedPlcOperation::id), byAccount.map(StoredHostedPlcOperation::id))
                    assertEquals(listOf("cid-1", "cid-2"), byDid.map(StoredHostedPlcOperation::cid))
                    assertEquals(listOf(null, "cid-1"), byDid.map(StoredHostedPlcOperation::prevCid))
                }
            }
        }
}
