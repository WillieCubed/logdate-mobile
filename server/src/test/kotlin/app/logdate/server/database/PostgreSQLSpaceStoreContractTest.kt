package app.logdate.server.database

import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.withOwnPostgresDatabase
import app.logdate.server.space.SpaceStore
import app.logdate.server.space.SpaceStoreContractTest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.UUID
import kotlin.time.Duration

/**
 * Runs the whole [SpaceStoreContractTest] suite against PostgreSQL through the real migrations, in a
 * database of its own, so the running totals, quota checks and reservations are proved on the
 * database the server uses and not only on H2.
 */
class PostgreSQLSpaceStoreContractTest : SpaceStoreContractTest() {
    override fun withStore(
        changesByteBudget: Long,
        objectDeleteTimeout: Duration,
        block: suspend (SpaceStore, UUID) -> Unit,
    ) = withOwnPostgresDatabase("space_contract_") { url ->
        localTestPostgresDataSource(url).use { source ->
            val previous = TransactionManager.defaultDatabase
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val account = UUID.randomUUID()
                insertTestAccountRow(account)
                runBlocking { block(PostgreSQLSpaceStore(changesByteBudget, objectDeleteTimeout), account) }
            } finally {
                TransactionManager.defaultDatabase = previous
            }
        }
    }
}
