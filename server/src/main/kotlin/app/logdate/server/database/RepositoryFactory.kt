package app.logdate.server.database

import app.logdate.server.auth.*
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import app.logdate.server.sync.ContentSyncTable
import app.logdate.server.sync.JournalSyncTable
import app.logdate.server.sync.AssociationSyncTable
import app.logdate.server.sync.MediaSyncTable

/**
 * Factory for creating repository instances based on available database connection.
 */
object RepositoryFactory {
    
    private var database: Database? = null
    private var isDatabaseAvailable = false
    
    fun initializeDatabase(): Boolean {
        return try {
            val dataSource = DatabaseConfig.createDataSource()
            database = DatabaseConfig.initializeDatabase(dataSource)
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    ContentSyncTable,
                    JournalSyncTable,
                    AssociationSyncTable,
                    MediaSyncTable
                )
            }
            isDatabaseAvailable = true
            Napier.i("Database repositories initialized successfully")
            true
        } catch (e: Exception) {
            Napier.w("Database not available, using in-memory repositories", e)
            isDatabaseAvailable = false
            false
        }
    }
    
    fun createAccountRepository(): AccountRepository {
        return if (isDatabaseAvailable) {
            PostgreSQLAccountRepository()
        } else {
            InMemoryAccountRepository()
        }
    }
    
    fun createPasskeyRepository(): PasskeyRepository {
        return if (isDatabaseAvailable) {
            PostgreSQLPasskeyRepository()
        } else {
            InMemoryPasskeyRepository()
        }
    }
    
    fun createSessionManager(): SessionManager {
        return if (isDatabaseAvailable) {
            PostgreSQLSessionManager()
        } else {
            InMemorySessionManager()
        }
    }
    
    fun createWebAuthnService(): WebAuthnPasskeyService {
        return if (isDatabaseAvailable) {
            // For database version, we use the existing simple service
            // A full implementation would integrate with DatabaseWebAuthnPasskeyService
            WebAuthnPasskeyService()
        } else {
            WebAuthnPasskeyService()
        }
    }

    fun createSyncRepository(): SyncRepository = InMemorySyncRepository()
    
    fun createDatabaseWebAuthnService(): DatabaseWebAuthnPasskeyService? {
        return if (isDatabaseAvailable) {
            DatabaseWebAuthnPasskeyService(PostgreSQLPasskeyRepository())
        } else {
            null
        }
    }
    
    fun isDatabaseEnabled(): Boolean = isDatabaseAvailable
}
