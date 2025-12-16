package app.logdate.client.sync.di

import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for Cloud API client dependencies.
 *
 * This module provides the CloudApiClient implementation
 * for communicating with the LogDate Cloud API.
 */
val cloudModule = module {
    // API Client
    single<CloudApiClient> { 
        LogDateCloudApiClient(
            // In a real app, this URL would come from configuration
            baseUrl = "https://api.logdate.app/api/v1",
            httpClient = get()
        )
    }
    
    // Cloud Data Sources
    single<CloudContentDataSource> { DefaultCloudContentDataSource(get()) }
    single<CloudJournalDataSource> { DefaultCloudJournalDataSource(get()) }
    single<CloudAssociationDataSource> { DefaultCloudAssociationDataSource(get()) }
    single<CloudMediaDataSource> { DefaultCloudMediaDataSource(get()) }
}