package app.logdate.client.database

import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module = module {
    single { getRoomDatabase(getDatabaseBuilder()) }
    includes(daosModule)
}
