package app.logdate.server.di

import app.logdate.server.ServerDescriptorConfig
import app.logdate.server.atproto.AtprotoPasswordCredentialRepository
import app.logdate.server.atproto.AtprotoPasswordService
import app.logdate.server.atproto.AtprotoPdsSessionService
import app.logdate.server.atproto.AtprotoSessionRepository
import app.logdate.server.atproto.AtprotoSessionTokenService
import app.logdate.server.atproto.InMemoryAtprotoPasswordCredentialRepository
import app.logdate.server.atproto.InMemoryAtprotoSessionRepository
import app.logdate.server.atproto.LogDatePdsBlobStore
import app.logdate.server.atproto.LogDateRepoStore
import app.logdate.server.auth.AccountDeletionService
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
import app.logdate.server.auth.TokenService
import app.logdate.server.config.RuntimeProfile
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
import app.logdate.server.database.OAuthAuthorizationCodesTable
import app.logdate.server.database.OAuthAuthorizationRequestsTable
import app.logdate.server.database.OAuthRefreshTokensTable
import app.logdate.server.database.PostgreSQLAccountIdentityRepository
import app.logdate.server.database.PostgreSQLAccountRepository
import app.logdate.server.database.PostgreSQLAtprotoPasswordCredentialRepository
import app.logdate.server.database.PostgreSQLAtprotoSessionRepository
import app.logdate.server.database.PostgreSQLHostedPlcOperationRepository
import app.logdate.server.database.PostgreSQLLogDateAtprotoBlobRepository
import app.logdate.server.database.PostgreSQLLogDateBackupRepository
import app.logdate.server.database.PostgreSQLLogDateCollectionsMetadataStore
import app.logdate.server.database.PostgreSQLLogDateMediaRepository
import app.logdate.server.database.PostgreSQLOAuthRuntimeStateRepository
import app.logdate.server.database.PostgreSQLPasskeyRepository
import app.logdate.server.database.PostgreSQLRepoBlockStore
import app.logdate.server.database.PostgreSQLRestoreCredentialRepository
import app.logdate.server.database.PostgreSQLSessionManager
import app.logdate.server.database.PostgreSQLSigningKeyRepository
import app.logdate.server.database.RestoreCredentialsTable
import app.logdate.server.database.SigningKeysTable
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.HostedPlcOperationRepository
import app.logdate.server.identity.InMemoryHostedPlcOperationRepository
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.PlcIdentityService
import app.logdate.server.identity.SigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.CompositeLogDateMediaBlobRepository
import app.logdate.server.logdate.FilesystemLogDateBlobStorage
import app.logdate.server.logdate.InMemoryLogDateAtprotoBlobRepository
import app.logdate.server.logdate.InMemoryLogDateBackupRepository
import app.logdate.server.logdate.InMemoryLogDateBlobStorage
import app.logdate.server.logdate.InMemoryLogDateCollectionsMetadataStore
import app.logdate.server.logdate.InMemoryLogDateMediaRepository
import app.logdate.server.logdate.LogDateAtprotoBlobRepository
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateCollectionsMetadataStore
import app.logdate.server.logdate.LogDateMediaBlobRepository
import app.logdate.server.logdate.LogDateMediaRepository
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.server.oauth.InMemoryOAuthRuntimeStateRepository
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthClientMetadataResolver
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.oauth.OAuthRuntimeStateRepository
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.InMemoryRestoreCredentialRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.RestoreCredentialRepository
import app.logdate.server.passkeys.RestoreCredentialService
import app.logdate.server.passkeys.WebAuthnConfig
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.sync.AssociationSyncTable
import app.logdate.server.sync.BackupSyncTable
import app.logdate.server.sync.ContentSyncTable
import app.logdate.server.sync.DbSyncRepository
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.JournalSyncTable
import app.logdate.server.sync.MediaSyncTable
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.bind
import org.koin.dsl.module
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.PdsBlobService
import studio.hypertext.atproto.pds.PdsDiscoveryService
import studio.hypertext.atproto.pds.PdsRepoService
import studio.hypertext.atproto.pds.PdsSessionService
import studio.hypertext.atproto.pds.PdsSyncService
import studio.hypertext.atproto.pds.runtime.DefaultPdsBlobService
import studio.hypertext.atproto.pds.runtime.DefaultPdsRepoService
import studio.hypertext.atproto.pds.runtime.DefaultPdsSyncService
import studio.hypertext.atproto.pds.runtime.StaticPdsDiscoveryService
import studio.hypertext.atproto.plc.KtorPlcDirectoryClient
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoEngine
import studio.hypertext.atproto.repo.RepoRecordStore

/**
 * Initializes the database connection and tables.
 *
 * Flyway migrations run only when `AUTO_MIGRATE=true` (the documented production policy is to
 * apply migrations as a separate CI step). The Exposed `createMissingTablesAndColumns`
 * reconciliation, by contrast, runs on every boot — it's idempotent, only adds missing
 * tables/columns, and is what fills in Exposed-defined columns that no Flyway migration owns
 * (e.g. `deleted` / `deleted_at` on `sync_*`). Coupling the two was what let an in-memory
 * fallback hide an empty production database for weeks.
 *
 * In production (`LOGDATE_ENV=production`) any DB failure is rethrown so Cloud Run's startup
 * probe rolls the revision back. In dev/test, the legacy in-memory fallback is preserved so
 * local iteration without a Postgres instance keeps working.
 *
 * @return true if Postgres is wired in, false if the dev/test in-memory fallback was taken.
 */
fun initializeDatabase(): Boolean =
    try {
        val dataSource = DatabaseConfig.createDataSource()
        val runFlyway = DatabaseConfig.shouldRunMigrations()
        DatabaseConfig.initializeDatabase(dataSource, autoMigrate = runFlyway)
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
                OAuthAuthorizationRequestsTable,
                OAuthAuthorizationCodesTable,
                OAuthRefreshTokensTable,
                RestoreCredentialsTable,
            )
        }
        Napier.i("Database repositories initialized successfully")
        true
    } catch (e: Exception) {
        if (RuntimeProfile.fromEnvironment().isProduction) {
            Napier.e("Production database unavailable; refusing to start with in-memory fallback", e)
            throw e
        }
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

        single<RestoreCredentialRepository> {
            if (isDatabaseAvailable) PostgreSQLRestoreCredentialRepository() else InMemoryRestoreCredentialRepository()
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
        single {
            val webAuthnConfig: WebAuthnConfig = get()
            RestoreCredentialService(
                restoreCredentialRepository = get(),
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
        single<OAuthRuntimeStateRepository> {
            if (isDatabaseAvailable) {
                PostgreSQLOAuthRuntimeStateRepository()
            } else {
                InMemoryOAuthRuntimeStateRepository()
            }
        }
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
                runtimeStateRepository = get(),
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
        single<PdsSessionService> {
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

        single<TokenService> {
            JwtTokenService(
                secret = System.getenv("JWT_SECRET") ?: JwtTokenService.generateSecret(),
            )
        }

        single {
            CompositeLogDateMediaBlobRepository(
                mediaRepository = get(),
                atprotoBlobRepository = get(),
            )
        } bind LogDateMediaBlobRepository::class

        single {
            AccountDeletionService(
                accountRepository = get(),
                mediaBlobRepository = get(),
                backupRepository = get(),
                blobStorage = getOrNull<LogDateBlobStorage>(),
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

        single<LogDateBlobStorage> {
            GcsMediaStorage.fromEnvironment()
                ?: FilesystemLogDateBlobStorage.fromEnvironment()
                ?: InMemoryLogDateBlobStorage()
        }

        // AT Protocol Runtime Services
        single {
            RepoBackedLogDateCollectionsRepository(
                accountRepository = get(),
                identityService = get(),
                signingKeyService = get(),
                blockStore = get(),
                metadataStore = get(),
            )
        }
        single {
            LogDateRepoStore(
                collectionsRepository = get<RepoBackedLogDateCollectionsRepository>(),
                identityService = get(),
                signingKeyService = get(),
                accountRepository = get(),
                blockStore = get(),
            )
        } bind RepoEngine::class bind RepoRecordStore::class

        single<PdsRepoService> { DefaultPdsRepoService(get()) }
        single<PdsSyncService> { DefaultPdsSyncService(get()) }
        single<PdsBlobService> {
            DefaultPdsBlobService(
                LogDatePdsBlobStore(
                    identityService = get(),
                    mediaBlobRepository = get(),
                    blobStorage = get(),
                ),
            )
        }
        single<PdsDiscoveryService> {
            val oauthConfig: OAuthConfig = get()
            val identityConfig: AtprotoIdentityConfig = get()
            StaticPdsDiscoveryService(
                authorizationServerMetadata = oauthConfig.authorizationServerMetadata(),
                protectedResourceMetadata = oauthConfig.protectedResourceMetadata(),
                describeServerResponse =
                    DescribeServerResponse(
                        did = identityConfig.serverDid,
                        availableUserDomains = listOf(identityConfig.normalizedHandleDomain),
                        inviteCodeRequired = false,
                        phoneVerificationRequired = false,
                    ),
            )
        }
    }
