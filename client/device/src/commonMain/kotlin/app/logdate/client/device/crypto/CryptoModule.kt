package app.logdate.client.device.crypto

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin dependency injection module for E2EE crypto infrastructure.
 *
 * Provides:
 * - CryptoManager (platform-specific)
 * - IdentityKeyManager (manage recovery phrases and identity keys)
 * - KeyDerivation (HKDF for per-content keys)
 * - ContentEncryptionService (high-level encryption API)
 */
val cryptoModule: Module =
    module {
        // Platform-specific CryptoManager (one of the following)
        // These must be provided by platform-specific modules

        // Common crypto services
        singleOf(::IdentityKeyManager)
        singleOf(::KeyDerivation)
        singleOf(::ContentEncryptionService)
    }

/**
 * Platform-specific crypto module provider.
 *
 * Each platform (Android, iOS, Desktop) must provide its own CryptoManager.
 * Use this interface to access the platform's crypto capabilities.
 */
expect fun platformCryptoModule(): Module
