package app.logdate.integration.e2e.harness

import app.logdate.server.database.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

internal suspend fun <T> withHarnessDatabase(block: suspend (Boolean) -> T): T {
    if (System.getenv("DATABASE_URL").isNullOrBlank()) return block(false)

    val source = DatabaseConfig.createDataSource()
    var database: Database? = null
    try {
        database = DatabaseConfig.initializeDatabase(source)
        TransactionManager.defaultDatabase = database
        return block(true)
    } finally {
        try {
            database?.let(TransactionManager::closeAndUnregister)
        } finally {
            (source as AutoCloseable).close()
        }
    }
}
