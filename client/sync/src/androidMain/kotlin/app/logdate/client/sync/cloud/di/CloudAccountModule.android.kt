package app.logdate.client.sync.cloud.di

import android.content.Context
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.BuildConfigAppInfoProvider
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.cloud.account.AndroidPlatformInfoProvider
import app.logdate.client.sync.cloud.account.DefaultCloudAccountRepository
import app.logdate.client.sync.cloud.account.PlatformInfoProvider
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific cloud account module.
 */
actual val cloudAccountModule: Module = module {
    // Cloud API client
    single<CloudApiClient> { 
        LogDateCloudApiClient(
            baseUrl = "https://api.logdate.app/v1",
            httpClient = get()
        )
    }
    
    // Platform info provider
    single<PlatformInfoProvider> { 
        AndroidPlatformInfoProvider(
            context = androidContext(),
            appInfoProvider = get()
        )
    }
    
    // Cloud account repository
    single<CloudAccountRepository> {
        DefaultCloudAccountRepository(
            apiClient = get(),
            secureStorage = get<KeyValueStorage>(),
            platformInfoProvider = get()
        )
    }
}