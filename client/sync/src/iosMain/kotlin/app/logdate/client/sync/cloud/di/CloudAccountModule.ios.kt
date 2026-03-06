package app.logdate.client.sync.cloud.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.cloud.account.DefaultCloudAccountRepository
import app.logdate.shared.model.CloudAccountRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific cloud account module.
 */
actual val cloudAccountModule: Module =
    module {
        // Cloud API client
        single<CloudApiClient> {
            LogDateCloudApiClient(
                baseUrl = "https://api.logdate.app/v1",
                httpClient = get(),
            )
        }

        // Cloud account repository
        single<CloudAccountRepository> {
            DefaultCloudAccountRepository(
                apiClient = get(),
                secureStorage = get<KeyValueStorage>(),
            )
        }
    }
