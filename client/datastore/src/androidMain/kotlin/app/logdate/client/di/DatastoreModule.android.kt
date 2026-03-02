package app.logdate.client.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific datastore module implementation
 */
actual val datastoreModule: Module =
    module {
        // Include common datastore module components
        includes(commonDatastoreModule)
    }
