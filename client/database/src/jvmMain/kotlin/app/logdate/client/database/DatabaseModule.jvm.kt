package app.logdate.client.database

import app.logdate.client.device.storage.DesktopFileCipher
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module =
    module {
        single {
            val vault = desktopDatabaseVault(get<DesktopFileCipher>())
            vault.unseal()
            val database = getRoomDatabase(getDatabaseBuilder())
            protectDatabaseFile()
            scheduleDatabaseEncryption(database, vault)
            database
        }
        includes(daosModule)
    }
