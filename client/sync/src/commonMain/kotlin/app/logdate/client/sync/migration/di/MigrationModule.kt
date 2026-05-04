package app.logdate.client.sync.migration.di

import app.logdate.client.sync.migration.DefaultIdentitySyncProvider
import app.logdate.client.sync.migration.DefaultMigrationManager
import app.logdate.client.sync.migration.IdentitySyncProvider
import app.logdate.client.sync.migration.InMemoryMigrationStorage
import app.logdate.client.sync.migration.MigrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common migration dependencies shared across all platforms.
 *
 * Json is intentionally not bound here — `DataModule.{android,ios,desktop}.kt`
 * already provides the canonical `Json` singleton with the project's lenient
 * deserialization options, and `MigrationManager` uses it via Koin lookup.
 */
internal val migrationCoreModule =
    module {
        // Provide a CoroutineScope for migrations
        single { CoroutineScope(SupervisorJob()) }

        // Register the migration manager
        single<MigrationManager> {
            DefaultMigrationManager(
                migrationStorage = get(),
                userIdentityProvider = get<IdentitySyncProvider>(),
//            coroutineScope = get(),
            )
        }

        // Register in-memory storage for testing
        factory { InMemoryMigrationStorage(get()) }

        // Register identity sync provider that uses DeviceIdProvider
        single<IdentitySyncProvider> {
            DefaultIdentitySyncProvider(get())
        }
    }

/**
 * A module for platform-specific migration dependencies.
 */
expect val migrationModule: Module
