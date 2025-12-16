package app.logdate.client.di

import app.logdate.client.datastore.DataStoreSessionStorage
import app.logdate.client.datastore.SessionStorage
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Android-specific datastore module implementation
 */
actual val datastoreModule: Module = module {
    // Include common datastore module components
    includes(commonDatastoreModule)
    
    // Android-specific session storage
    single<SessionStorage> { 
        DataStoreSessionStorage(get(named("mainDataStore"))) 
    }
}