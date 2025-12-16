package app.logdate.client.sync.migration.di

import app.logdate.client.sync.migration.MigrationStorage
import app.logdate.client.sync.migration.SharedPreferencesMigrationStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific migration dependencies.
 */
actual val migrationModule: Module = module {
    // Provide Android-specific migration storage
    single<MigrationStorage> { SharedPreferencesMigrationStorage(androidContext(), get()) }
}