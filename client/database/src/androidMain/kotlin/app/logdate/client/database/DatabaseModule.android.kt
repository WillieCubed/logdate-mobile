package app.logdate.client.database

import app.logdate.client.database.encryption.DatabasePassphraseProvider
import app.logdate.client.database.encryption.PassphraseBackupStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module =
    module {
        single { PassphraseBackupStore(androidContext()) }
        single { DatabasePassphraseProvider(get(), get()) }
        single { DatabaseStartupMonitor() }
        single { DatabaseRecoveryController(androidContext(), get(), get()) }
        single {
            val context = androidContext()
            val startupMonitor: DatabaseStartupMonitor = get()

            Napier.i("Startup: encrypted database initialization starting")
            val passphrase = runBlocking { get<DatabasePassphraseProvider>().getOrCreatePassphrase() }
            Napier.i("Startup: loaded database passphrase (${passphrase.size} bytes)")

            runCatching {
                migratePlaintextDatabaseIfNeeded(context, passphrase)
                val database =
                    getRoomDatabase(
                        getDatabaseBuilder(context, passphrase),
                        driver = null,
                    )

                // Open eagerly so key/migration errors are detected up front.
                try {
                    database.openHelper.writableDatabase
                    protectDatabaseFile(context)
                } catch (error: Exception) {
                    database.close()
                    throw error
                }
                startupMonitor.markReady()
                Napier.i("Startup: encrypted database opened and protections applied")
                database
            }.onFailure { error ->
                startupMonitor.markRecoveryRequired(error)
                Napier.e("Startup: encrypted database unavailable; journal access is paused", error)
            }.getOrThrow()
        }
        includes(daosModule)
    }
