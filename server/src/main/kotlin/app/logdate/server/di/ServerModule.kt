package app.logdate.server.di

import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.HttpGoogleIdTokenVerifier
import app.logdate.server.auth.InMemoryAccountIdentityRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemorySessionManager
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.database.AccountIdentitiesTable
import app.logdate.server.database.AccountLinkEventsTable
import app.logdate.server.database.DatabaseConfig
import app.logdate.server.database.PostgreSQLAccountIdentityRepository
import app.logdate.server.database.PostgreSQLAccountRepository
import app.logdate.server.database.PostgreSQLPasskeyRepository
import app.logdate.server.database.PostgreSQLSessionManager
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.sync.AssociationSyncTable
import app.logdate.server.sync.ContentSyncTable
import app.logdate.server.sync.DbSyncRepository
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.JournalSyncTable
import app.logdate.server.sync.MediaSyncTable
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module

/**
 * Initializes the database connection and tables.
 * @return true if database is available, false otherwise
 */
fun initializeDatabase(): Boolean =
    try {
        val dataSource = DatabaseConfig.createDataSource()
        DatabaseConfig.initializeDatabase(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                ContentSyncTable,
                JournalSyncTable,
                AssociationSyncTable,
                MediaSyncTable,
                AccountIdentitiesTable,
                AccountLinkEventsTable,
            )
        }
        Napier.i("Database repositories initialized successfully")
        true
    } catch (e: Exception) {
        Napier.w("Database not available, using in-memory repositories", e)
        false
    }

/**
 * Creates server Koin module based on database availability.
 */
fun serverModule(isDatabaseAvailable: Boolean) =
    module {
        single<AccountRepository> {
            if (isDatabaseAvailable) PostgreSQLAccountRepository() else InMemoryAccountRepository()
        }

        single<AccountIdentityRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLAccountIdentityRepository()
            } else {
                InMemoryAccountIdentityRepository()
            }
        }

        single<PasskeyRepository> {
            if (isDatabaseAvailable) PostgreSQLPasskeyRepository() else InMemoryPasskeyRepository()
        }

        single<SessionManager> {
            if (isDatabaseAvailable) PostgreSQLSessionManager() else InMemorySessionManager()
        }

        single {
            WebAuthnPasskeyService(
                passkeyRepository = get(),
                relyingPartyId = System.getenv("WEBAUTHN_RP_ID") ?: "logdate.app",
                relyingPartyName = System.getenv("WEBAUTHN_RP_NAME") ?: "LogDate",
                origin = System.getenv("WEBAUTHN_ORIGIN") ?: "https://app.logdate.com",
            )
        }

        single<SyncRepository> {
            if (isDatabaseAvailable) DbSyncRepository() else InMemorySyncRepository()
        }

        single { SyncMetricsRegistry() }
        single { AuthMetricsRegistry() }

        single {
            JwtTokenService(
                secret = System.getenv("JWT_SECRET") ?: JwtTokenService.generateSecret(),
            )
        }

        single<GoogleIdTokenVerifier> {
            val allowedClientIds =
                (System.getenv("GOOGLE_OIDC_CLIENT_IDS") ?: "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            HttpGoogleIdTokenVerifier(allowedClientIds = allowedClientIds)
        }
    }
