package app.logdate.client.sync.migration.di

import app.logdate.client.sync.migration.FileMigrationStorage
import app.logdate.client.sync.migration.MigrationStorage
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop-specific migration dependencies.
 */
actual val migrationModule: Module = module {
    // Provide Desktop-specific migration storage
    single<MigrationStorage> { FileMigrationStorage(get()) }
}