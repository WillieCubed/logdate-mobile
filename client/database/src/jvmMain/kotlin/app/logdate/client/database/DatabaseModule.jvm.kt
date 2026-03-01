package app.logdate.client.database

import app.logdate.client.device.storage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module = module {
    single {
        val secureStorage = get<SecureStorage>()
        prepareEncryptedDatabase(secureStorage)
        val database = getRoomDatabase(
            getDatabaseBuilder(),
            destroyTablesOnUpgrade = true,
        )
        protectDatabaseFile()
        scheduleDatabaseEncryption(secureStorage)
        database
    }
    includes(daosModule)
}
