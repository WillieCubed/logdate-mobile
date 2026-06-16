package app.logdate.client.sync.di

import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.crypto.StoredMediaPayloadCrypto
import app.logdate.client.sync.crypto.SyncPayloadCipher
import org.koin.dsl.module

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
            LogDateCloudApiClient(
                configRepository = get(),
                httpClient = get(),
            )
        }

        // Cloud Data Sources
        single { SyncPayloadCipher(get()) }
        single<CloudContentDataSource> { DefaultCloudContentDataSource(get(), get()) }
        single<CloudJournalDataSource> { DefaultCloudJournalDataSource(get(), get()) }
        single<CloudAssociationDataSource> { DefaultCloudAssociationDataSource(get()) }
        single<CloudDraftDataSource> { DefaultCloudDraftDataSource(get(), get()) }
        single { MediaPayloadKeyProvider(get(), get()) }
        single<MediaPayloadCrypto> { StoredMediaPayloadCrypto(get()) }
        single<CloudMediaDataSource> { DefaultCloudMediaDataSource(get(), get()) }
    }
