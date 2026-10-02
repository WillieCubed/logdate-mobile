package app.logdate.client.device.di

import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.BuildConfigAppInfoProvider
import app.logdate.client.device.crypto.cryptoModule
import app.logdate.client.device.crypto.platformCryptoModule
import app.logdate.client.device.identity.di.deviceIdentityModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Main entry point for the device module.
 * Includes all common and platform-specific components.
 */
val deviceModule: Module =
    module {
        single {
            val secure = get<app.logdate.client.device.storage.SecureStorage>()
            app.logdate.shared.config.PrivacyScopeEpoch(
                object : app.logdate.shared.config.PrivacyEpochStorage {
                    override suspend fun read(): String? = secure.getString("diagnostic_privacy_epoch")

                    override suspend fun write(epoch: String) = secure.putString("diagnostic_privacy_epoch", epoch)
                },
            )
        }
        // Include device identity components
        includes(deviceIdentityModule)

        // Include device instance module
        includes(deviceInstanceModule)

        // Crypto primitives and identity key management
        includes(cryptoModule)
        includes(platformCryptoModule())

        // Provide the default app info provider if not already provided by platform
        single<AppInfoProvider> { BuildConfigAppInfoProvider() }
    }
