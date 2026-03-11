package app.logdate.server.di

import app.logdate.server.ServerDescriptorConfig
import app.logdate.server.atproto.AtprotoPasswordCredentialRepository
import app.logdate.server.atproto.AtprotoPasswordService
import app.logdate.server.atproto.AtprotoPdsSessionService
import app.logdate.server.atproto.AtprotoSessionRepository
import app.logdate.server.atproto.AtprotoSessionTokenService
import app.logdate.server.atproto.InMemoryAtprotoPasswordCredentialRepository
import app.logdate.server.atproto.InMemoryAtprotoSessionRepository
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
import app.logdate.server.database.AccountsTable
import app.logdate.server.database.AtprotoPasswordCredentialsTable
import app.logdate.server.database.AtprotoRepoBlockLinksTable
import app.logdate.server.database.AtprotoRepoBlocksTable
import app.logdate.server.database.AtprotoRepoCommitsTable
import app.logdate.server.database.AtprotoRepoHeadsTable
import app.logdate.server.database.AtprotoSessionsTable
import app.logdate.server.database.DatabaseConfig
import app.logdate.server.database.HostedPlcOperationsTable
import app.logdate.server.database.LogDateAtprotoBlobsTable
import app.logdate.server.database.LogDateBackupsTable
import app.logdate.server.database.LogDateCollectionRecordsTable
import app.logdate.server.database.LogDateCollectionStatesTable
import app.logdate.server.database.LogDateMediaRecordsTable
import app.logdate.server.database.PostgreSQLAccountIdentityRepository
import app.logdate.server.database.PostgreSQLAccountRepository
import app.logdate.server.database.PostgreSQLAtprotoPasswordCredentialRepository
import app.logdate.server.database.PostgreSQLAtprotoSessionRepository
import app.logdate.server.database.PostgreSQLHostedPlcOperationRepository
import app.logdate.server.database.PostgreSQLLogDateAtprotoBlobRepository
import app.logdate.server.database.PostgreSQLLogDateBackupRepository
import app.logdate.server.database.PostgreSQLLogDateCollectionsMetadataStore
import app.logdate.server.database.PostgreSQLLogDateMediaRepository
import app.logdate.server.database.PostgreSQLPasskeyRepository
import app.logdate.server.database.PostgreSQLRepoBlockStore
import app.logdate.server.database.PostgreSQLSessionManager
import app.logdate.server.database.PostgreSQLSigningKeyRepository
import app.logdate.server.database.SigningKeysTable
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.HostedPlcOperationRepository
import app.logdate.server.identity.InMemoryHostedPlcOperationRepository
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.PlcIdentityService
import app.logdate.server.identity.SigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.InMemoryLogDateAtprotoBlobRepository
import app.logdate.server.logdate.InMemoryLogDateBackupRepository
import app.logdate.server.logdate.InMemoryLogDateCollectionsMetadataStore
import app.logdate.server.logdate.InMemoryLogDateMediaRepository
import app.logdate.server.logdate.LogDateAtprotoBlobRepository
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateCollectionsMetadataStore
import app.logdate.server.logdate.LogDateMediaRepository
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthClientMetadataResolver
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.WebAuthnConfig
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.sync.AssociationSyncTable
import app.logdate.server.sync.BackupSyncTable
import app.logdate.server.sync.ContentSyncTable
import app.logdate.server.sync.DbSyncRepository
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.JournalSyncTable
import app.logdate.server.sync.MediaSyncTable
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import studio.hypertext.atproto.plc.KtorPlcDirectoryClient
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoBlockStore

/**
 * Initializes the database connection and tables.
 * @return true if database is available, false otherwise
 */
fun initializeDatabase(): Boolean =
    try {
        val dataSource = DatabaseConfig.createDataSource()
        val autoMigrate = DatabaseConfig.shouldRunMigrations()
        if (autoMigrate) {
            DatabaseConfig.initializeDatabase(dataSource, autoMigrate = true)
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    AccountsTable,
                    AtprotoPasswordCredentialsTable,
                    AtprotoSessionsTable,
                    ContentSyncTable,
                    JournalSyncTable,
                    AssociationSyncTable,
                    MediaSyncTable,
                    BackupSyncTable,
                    AccountIdentitiesTable,
                    AccountLinkEventsTable,
                    SigningKeysTable,
                    HostedPlcOperationsTable,
                    AtprotoRepoHeadsTable,
                    AtprotoRepoBlocksTable,
                    AtprotoRepoBlockLinksTable,
                    AtprotoRepoCommitsTable,
                    LogDateCollectionStatesTable,
                    LogDateCollectionRecordsTable,
                    LogDateMediaRecordsTable,
                    LogDateBackupsTable,
                    LogDateAtprotoBlobsTable,
                )
            }
        } else {
            DatabaseConfig.initializeDatabase(dataSource, autoMigrate = false)
            Napier.i("Database schema reconciliation skipped because AUTO_MIGRATE=false")
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

        single<AtprotoPasswordCredentialRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLAtprotoPasswordCredentialRepository()
            } else {
                InMemoryAtprotoPasswordCredentialRepository()
            }
        }

        single<AtprotoSessionRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLAtprotoSessionRepository()
            } else {
                InMemoryAtprotoSessionRepository()
            }
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

        single<SigningKeyRepository> {
            if (isDatabaseAvailable) PostgreSQLSigningKeyRepository() else InMemorySigningKeyRepository()
        }

        single<SessionManager> {
            if (isDatabaseAvailable) PostgreSQLSessionManager() else InMemorySessionManager()
        }

        single { WebAuthnConfig.fromEnvironment(serverOrigin = get<AtprotoIdentityConfig>().pdsServiceEndpoint) }
        single {
            val webAuthnConfig: WebAuthnConfig = get()
            WebAuthnPasskeyService(
                passkeyRepository = get(),
                relyingPartyId = webAuthnConfig.relyingPartyId,
                relyingPartyName = webAuthnConfig.relyingPartyName,
                origin = webAuthnConfig.origin,
            )
        }
        single { AtprotoIdentityConfig.fromEnvironment() }
        single { ServerDescriptorConfig.fromEnvironment() }
        single {
            OAuthConfig.fromEnvironment(
                defaultIssuer = get<AtprotoIdentityConfig>().pdsServiceEndpoint,
            )
        }
        single { HttpClient(OkHttp) }
        single { OAuthKeyService() }
        single { OAuthNonceService() }
        single { OAuthDpopVerifier() }
        single { OAuthClientMetadataResolver(httpClient = get()) }
        single { OAuthAccessTokenService(config = get(), keyService = get()) }
        single {
            val config: OAuthConfig = get()
            OAuthAuthorizationService(
                clientMetadataResolver = get(),
                dpopVerifier = get(),
                accessTokenService = get(),
                nonceService = get(),
                authorizationServerIssuer = config.normalizedIssuer,
            )
        }
        single {
            SigningKeyService(
                repository = get(),
                encryptionKeySeed =
                    System.getenv("ATPROTO_SIGNING_KEY_KEK")
                        ?: System.getenv("JWT_SECRET")
                        ?: "logdate-atproto-dev-signing-key",
            )
        }
        single {
            PlcIdentityService(
                signingKeyService = get(),
                config = get(),
                hostedPlcOperationRepository = get(),
                plcDirectoryClient =
                    get<AtprotoIdentityConfig>()
                        .takeIf(AtprotoIdentityConfig::publishHostedPlcOperations)
                        ?.let { config ->
                            KtorPlcDirectoryClient(
                                httpClient = get(),
                                baseUrl = config.normalizedPlcDirectoryUrl,
                            )
                        },
            )
        }
        single {
            AtprotoIdentityService(
                accountRepository = get(),
                signingKeyService = get(),
                config = get(),
                plcIdentityService = get(),
            )
        }
        single { AtprotoPasswordService(repository = get()) }
        single { AtprotoSessionTokenService(sessionRepository = get()) }
        single {
            AtprotoPdsSessionService(
                accountRepository = get(),
                identityService = get(),
                passwordService = get(),
                sessionTokenService = get(),
            )
        }

        single<SyncRepository> {
            if (isDatabaseAvailable) DbSyncRepository() else InMemorySyncRepository()
        }

        single<RepoBlockStore> {
            if (isDatabaseAvailable) PostgreSQLRepoBlockStore() else InMemoryRepoBlockStore()
        }

        single<LogDateCollectionsMetadataStore> {
            if (isDatabaseAvailable) {
                PostgreSQLLogDateCollectionsMetadataStore()
            } else {
                InMemoryLogDateCollectionsMetadataStore()
            }
        }

        single<LogDateMediaRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLLogDateMediaRepository()
            } else {
                InMemoryLogDateMediaRepository()
            }
        }

        single<LogDateBackupRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLLogDateBackupRepository()
            } else {
                InMemoryLogDateBackupRepository()
            }
        }

        single<LogDateAtprotoBlobRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLLogDateAtprotoBlobRepository()
            } else {
                InMemoryLogDateAtprotoBlobRepository()
            }
        }

        single<HostedPlcOperationRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLHostedPlcOperationRepository()
            } else {
                InMemoryHostedPlcOperationRepository()
            }
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
