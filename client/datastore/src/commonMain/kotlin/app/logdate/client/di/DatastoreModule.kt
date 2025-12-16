package app.logdate.client.di

import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.createDataStore
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Module for the app-wide datastore.
 * Platform-specific implementations will provide the actual datastores.
 */
expect val datastoreModule: Module

/**
 * Common parts of the datastore module that are shared across all platforms
 */
val commonDatastoreModule = module {
    // Create main DataStore instance
    single(named("mainDataStore")) { 
        createDataStore() 
    }
    
    // Provide KeyValueStorage implementation backed by DataStore
    single<KeyValueStorage> { 
        DataStoreKeyValueStorage(get(named("mainDataStore")))
    }
    
    // Create namespaced key-value stores for different parts of the app
    factory(named("deviceKeyValueStorage")) {
        // Same implementation but with a qualified DataStore instance
        // to isolate device-specific data
        DataStoreKeyValueStorage(get(named("mainDataStore")))
    }
    
    factory(named("userKeyValueStorage")) {
        // Same implementation but with a qualified DataStore instance
        // to isolate user-specific data
        DataStoreKeyValueStorage(get(named("mainDataStore")))
    }
    
    // Legacy data source (will be migrated to use KeyValueStorage)
    factory { 
        LogdatePreferencesDataSource(get(named("mainDataStore")))
    }
}