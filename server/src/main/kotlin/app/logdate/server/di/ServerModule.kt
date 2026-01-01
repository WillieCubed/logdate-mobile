package app.logdate.server.di

import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemorySessionManager
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.database.DatabaseConfig
import app.logdate.server.database.DatabaseWebAuthnPasskeyService
import app.logdate.server.database.PostgreSQLAccountRepository
import app.logdate.server.database.PostgreSQLPasskeyRepository
import app.logdate.server.database.PostgreSQLSessionManager
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.sync.ContentSyncTable
import app.logdate.server.sync.JournalSyncTable
import app.logdate.server.sync.AssociationSyncTable
import app.logdate.server.sync.MediaSyncTable
import app.logdate.server.sync.DbSyncRepository
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module

/**
 * Initializes the database connection and tables.
 * @return true if database is available, false otherwise
 */
fun initializeDatabase(): Boolean {
    return try {
        val dataSource = DatabaseConfig.createDataSource()
        DatabaseConfig.initializeDatabase(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                ContentSyncTable,
                JournalSyncTable,
                AssociationSyncTable,
                MediaSyncTable
            )
        }
        Napier.i("Database repositories initialized successfully")
        true
    } catch (e: Exception) {
        Napier.w("Database not available, using in-memory repositories", e)
        false
    }
}

/**
 * Creates server Koin module based on database availability.
 */
fun serverModule(isDatabaseAvailable: Boolean) = module {
    single<AccountRepository> {
        if (isDatabaseAvailable) PostgreSQLAccountRepository() else InMemoryAccountRepository()
    }

    single<PasskeyRepository> {
        if (isDatabaseAvailable) PostgreSQLPasskeyRepository() else InMemoryPasskeyRepository()
    }

    single<SessionManager> {
        if (isDatabaseAvailable) PostgreSQLSessionManager() else InMemorySessionManager()
    }

    single { WebAuthnPasskeyService() }

    single<SyncRepository> {
        if (isDatabaseAvailable) DbSyncRepository() else InMemorySyncRepository()
    }

    single {
        JwtTokenService(
            secret = System.getenv("JWT_SECRET") ?: JwtTokenService.generateSecret()
        )
    }

    single {
        if (isDatabaseAvailable) {
            DatabaseWebAuthnPasskeyService(get())
        } else {
            null
        }
    }
}
