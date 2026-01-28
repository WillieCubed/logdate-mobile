package app.logdate.client.device.crypto

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual fun platformCryptoModule(): Module = module {
    singleOf(::DesktopCryptoManager) { bind<CryptoManager>() }
}
