package app.logdate.client.sync.cloud.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.account.DefaultCloudAccountRepository
import app.logdate.shared.model.CloudAccountRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific cloud account module.
 */
actual val cloudAccountModule: Module =
    module {
        // Cloud account repository
        single<CloudAccountRepository> {
            DefaultCloudAccountRepository(
                apiClient = get(),
                secureStorage = get<KeyValueStorage>(),
            )
        }
    }
