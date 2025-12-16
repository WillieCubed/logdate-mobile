package app.logdate.client.sync.migration.di

import app.logdate.client.sync.migration.KeychainMigrationStorage
import app.logdate.client.sync.migration.MigrationStorage
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific migration dependencies.
 */
actual val migrationModule: Module = module {
    // Provide iOS-specific migration storage
    single<MigrationStorage> { KeychainMigrationStorage(get()) }
}