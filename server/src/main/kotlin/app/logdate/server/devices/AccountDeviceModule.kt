package app.logdate.server.devices

import app.logdate.server.database.PostgreSQLAccountDeviceRepository
import org.koin.dsl.module

internal fun accountDeviceModule(databaseAvailable: Boolean) =
    module {
        single<AccountDeviceRepository> {
            if (databaseAvailable) PostgreSQLAccountDeviceRepository() else InMemoryAccountDeviceRepository()
        }
    }
