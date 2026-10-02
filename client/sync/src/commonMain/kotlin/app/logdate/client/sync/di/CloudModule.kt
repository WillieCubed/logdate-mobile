package app.logdate.client.sync.di

import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.networking.LocationHistoryApiClient
import app.logdate.client.repository.location.ActivityHistoryRepository
import app.logdate.client.repository.location.HistoryOwnerAdoptionRepair
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudBackupDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.crypto.StoredMediaPayloadCrypto
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.location.LocationHistoryStager
import app.logdate.client.sync.location.LocationHistorySyncEngine
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.DurableCloudApiClient
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerProtocolFeature
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Koin module for Cloud API client dependencies.
 *
 * This module provides the CloudApiClient implementation
 * for communicating with the LogDate Cloud API.
 */
val cloudModule =
    module {
        // API Client
        single<CloudApiClient> {
            val recorder = get<app.logdate.client.sync.diagnostics.SyncDiagnosticRecorder>()
            val sources = get<app.logdate.client.sync.diagnostics.DiagnosticSourceProvider>()
            LogDateCloudApiClient(
                configRepository = get(),
                httpClient = get(),
                sessionStorage = get<SessionStorage>(),
                diagnosticSource = { sources.current() },
                scopedDiagnostics = { event, source -> recorder.record(event, source?.scope, source?.epoch) },
            )
        }

        single {
            val sessions = get<SessionStorage>()
            val recorder = get<app.logdate.client.sync.diagnostics.SyncDiagnosticRecorder>()
            val sources = get<app.logdate.client.sync.diagnostics.DiagnosticSourceProvider>()
            DownloadInbox(
                get<LogDateDatabase>().downloadInboxDao(),
                get(),
                {
                    val bound = sessions.getOriginBoundSession()
                    DownloadScope(bound?.session?.accountId.orEmpty(), bound?.origin.orEmpty())
                },
                { Clock.System.now().toEpochMilliseconds() },
                diagnosticSource = sources::current,
                diagnostics = { event, source -> recorder.record(event, source?.scope, source?.epoch) },
            )
        }
        single { DurableCloudApiClient(get<CloudApiClient>(), get()) }

        // Raw API remains available for identity emptiness checks; incremental inbox reads cannot prove emptiness.
        // Cloud Data Sources
        single { SyncPayloadCipher(get(), get(), get()) }
        single {
            val locations = get<LocationHistoryRepository>()
            val activities = get<ActivityHistoryRepository>()
            val device = get<DeviceIdProvider>()
            LocationHistoryStager(
                locations::getLocationHistoryPage,
                activities::getActivityHistoryPage,
                get(),
                get(),
                { device.getDeviceId().value.toString() },
            )
        }
        single {
            val httpClient = get<io.ktor.client.HttpClient>()
            val flags = get<FeatureFlagStore>()
            val stager = get<LocationHistoryStager>()
            val adoption = get<HistoryOwnerAdoptionRepair>()
            LocationHistorySyncEngine(
                apiFactory = { config -> LocationHistoryApiClient(httpClient, config) },
                store = get(),
                cipher = get(),
                sessions = get(),
                config = get(),
                enabled = { flags.isEnabled(FeatureFlag.HUMAN_LOCATION_HISTORY) },
                prepare = { owner, origin ->
                    adoption.reconcile(owner, origin)
                    stager.stage(owner, origin)
                },
            )
        }
        single<CloudContentDataSource> { DefaultCloudContentDataSource(get<DurableCloudApiClient>(), get()) }
        single<CloudJournalDataSource> { DefaultCloudJournalDataSource(get<DurableCloudApiClient>(), get()) }
        single<CloudAssociationDataSource> { DefaultCloudAssociationDataSource(get<DurableCloudApiClient>()) }
        single<CloudBackupDataSource> { DefaultCloudBackupDataSource(get()) }
        single<CloudDraftDataSource> {
            val config = get<LogDateConfigRepository>()
            DefaultCloudDraftDataSource(get<DurableCloudApiClient>(), get()) {
                config.getCurrentServerDescriptor()?.hasProtocolFeature(ServerProtocolFeature.RICH_DRAFTS_V1) == true
            }
        }
        single { MediaPayloadKeyProvider(get(), get(), get(), get()) }
        single<MediaPayloadCrypto> { StoredMediaPayloadCrypto(get()) }
        single<CloudMediaDataSource> { DefaultCloudMediaDataSource(get(), get()) }
    }
