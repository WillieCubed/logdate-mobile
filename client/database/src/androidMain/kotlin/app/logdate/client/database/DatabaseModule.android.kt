package app.logdate.client.database

import app.logdate.client.database.encryption.DatabasePassphraseProvider
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module = module {
    single { DatabasePassphraseProvider(get()) }
    single {
        val passphrase = runBlocking { get<DatabasePassphraseProvider>().getOrCreatePassphrase() }
        val database = getRoomDatabase(
            getDatabaseBuilder(androidContext(), passphrase),
            driver = null
        )
        protectDatabaseFile(androidContext())
        database
    }
    includes(daosModule)
}
