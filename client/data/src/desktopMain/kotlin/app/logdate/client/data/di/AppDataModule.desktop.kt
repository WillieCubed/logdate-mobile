package app.logdate.client.data.di

import app.logdate.client.sync.di.syncModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Aggregates data-related modules including sync.
 * This separate module breaks the circular dependency between dataModule and syncModule.
 */
val appDataModule: Module = module {
    includes(dataModule)
    includes(syncModule)
}