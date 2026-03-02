package app.logdate.client.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import app.logdate.client.database.encryption.DatabasePassphraseProvider
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module =
    module {
        single { DatabasePassphraseProvider(get()) }
        single { DatabaseStartupMonitor() }
        single { DatabaseRecoveryController(androidContext(), get(), get()) }
        single {
            val context = androidContext()
            val startupMonitor: DatabaseStartupMonitor = get()

            Log.i(DB_INIT_TAG, "Startup: encrypted database initialization starting")
            val passphrase = runBlocking { get<DatabasePassphraseProvider>().getOrCreatePassphrase() }
            Log.i(DB_INIT_TAG, "Startup: loaded database passphrase (${passphrase.size} bytes)")

            runCatching {
                migratePlaintextDatabaseIfNeeded(context, passphrase)
                val database =
                    getRoomDatabase(
                        getDatabaseBuilder(context, passphrase),
                        driver = null,
                    )

                // Open eagerly so key/migration errors are detected up front.
                database.openHelper.writableDatabase
                protectDatabaseFile(context)
                startupMonitor.markReady()
                Log.i(DB_INIT_TAG, "Startup: encrypted database opened and protections applied")
                database
            }.onFailure { error ->
                startupMonitor.markRecoveryRequired(error)
                Log.e(
                    DB_INIT_TAG,
                    "Startup: encrypted database unavailable, using in-memory safety mode until recovery action",
                    error,
                )
            }.getOrElse {
                createRecoveryFallbackDatabase(context)
            }
        }
        includes(daosModule)
    }

private const val DB_INIT_TAG = "LogDateDatabaseInit"

private fun createRecoveryFallbackDatabase(context: Context): LogDateDatabase {
    Log.w(DB_INIT_TAG, "Creating in-memory fallback database for recovery mode")
    return getRoomDatabase(
        builder = Room.inMemoryDatabaseBuilder(context.applicationContext, LogDateDatabase::class.java),
        driver = null,
    )
}
