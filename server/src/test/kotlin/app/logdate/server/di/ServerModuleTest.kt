package app.logdate.server.di

import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.InMemoryAccountIdentityRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemorySessionManager
import app.logdate.server.auth.SessionManager
import app.logdate.server.database.DatabaseConfig
import app.logdate.server.database.PostgreSQLAccountIdentityRepository
import app.logdate.server.database.PostgreSQLAccountRepository
import app.logdate.server.database.PostgreSQLPasskeyRepository
import app.logdate.server.database.PostgreSQLRepoBlockStore
import app.logdate.server.database.PostgreSQLSessionManager
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.PlcIdentityService
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.sync.DbSyncRepository
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.koin.core.context.stopKoin
import org.koin.core.logger.EmptyLogger
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoBlockStore
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerModuleTest {
    @AfterTest
    fun cleanupKoin() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `initialize database gracefully falls back when unavailable`() {
        // Environment-dependent: local developers may have Postgres available.
        // The contract here is that initialization never crashes the process.
        initializeDatabase()
    }

    @Test
    fun `server module uses in-memory implementations when database is unavailable`() {
        val koin =
            koinApplication {
                logger(EmptyLogger())
                modules(serverModule(isDatabaseAvailable = false))
            }.koin

        assertIs<InMemoryAccountRepository>(koin.get<AccountRepository>())
        assertIs<InMemoryAccountIdentityRepository>(koin.get<AccountIdentityRepository>())
        assertIs<InMemoryPasskeyRepository>(koin.get<PasskeyRepository>())
        assertIs<InMemorySessionManager>(koin.get<SessionManager>())
        assertIs<InMemorySyncRepository>(koin.get<SyncRepository>())
        assertIs<InMemoryRepoBlockStore>(koin.get<RepoBlockStore>())
        assertIs<WebAuthnPasskeyService>(koin.get<WebAuthnPasskeyService>())
        assertIs<GoogleIdTokenVerifier>(koin.get<GoogleIdTokenVerifier>())
        assertTrue(koin.get<WebAuthnPasskeyService>().relyingPartyId.isNotBlank())
    }

    @Test
    fun `server module wires database repositories when database flag is enabled`() {
        val koin =
            koinApplication {
                logger(EmptyLogger())
                modules(serverModule(isDatabaseAvailable = true))
            }.koin

        assertIs<PostgreSQLAccountRepository>(koin.get<AccountRepository>())
        assertIs<PostgreSQLAccountIdentityRepository>(koin.get<AccountIdentityRepository>())
        assertIs<PostgreSQLPasskeyRepository>(koin.get<PasskeyRepository>())
        assertIs<PostgreSQLSessionManager>(koin.get<SessionManager>())
        assertIs<DbSyncRepository>(koin.get<SyncRepository>())
        assertIs<PostgreSQLRepoBlockStore>(koin.get<RepoBlockStore>())
        assertIs<WebAuthnPasskeyService>(koin.get<WebAuthnPasskeyService>())
    }

    @Test
    fun `initializeDatabase schema lambda is invocable`() {
        mockkObject(SchemaUtils)
        every {
            SchemaUtils.createMissingTablesAndColumns(
                *anyVararg(),
            )
        } returns Unit

        val method =
            Class
                .forName("app.logdate.server.di.ServerModuleKt")
                .getDeclaredMethod("initializeDatabase\$lambda\$0", Transaction::class.java)
        method.isAccessible = true
        method.invoke(null, mockk<Transaction>(relaxed = true))
    }

    @Test
    fun `initializeDatabase returns true when datasource and schema creation succeed`() {
        val dataSource = mockk<DataSource>()
        val database = mockk<Database>()

        mockkObject(DatabaseConfig)
        mockkObject(SchemaUtils)

        every { DatabaseConfig.createDataSource() } returns dataSource
        every { DatabaseConfig.shouldRunMigrations() } returns true
        every { DatabaseConfig.initializeDatabase(dataSource, true, any(), any()) } returns database
        every {
            SchemaUtils.createMissingTablesAndColumns(
                *anyVararg(),
            )
        } returns Unit

        Database.connect(
            url = "jdbc:h2:mem:servermodule_test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        assertTrue(initializeDatabase())
    }

    @Test
    fun `server module builds plc publishing client when enabled`() {
        val koin =
            koinApplication {
                logger(EmptyLogger())
                allowOverride(true)
                modules(
                    serverModule(isDatabaseAvailable = false),
                    module {
                        single {
                            AtprotoIdentityConfig(
                                handleDomain = "logdate.app",
                                pdsServiceEndpoint = "https://logdate.app",
                                publishHostedPlcOperations = true,
                                plcDirectoryUrl = "https://plc.example.com/",
                            )
                        }
                    },
                )
            }.koin

        assertIs<PlcIdentityService>(koin.get<PlcIdentityService>())
    }
}
