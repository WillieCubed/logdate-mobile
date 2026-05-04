package app.logdate.client.device.crypto

import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformCryptoModule(): Module =
    module {
        single<CryptoManager> { IosCryptoManager() }
        single<PlcRecoveryKeyManager> { IosPlcRecoveryKeyManager() }
    }
