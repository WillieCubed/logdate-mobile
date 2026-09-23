package app.logdate.client.device.crypto

import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformCryptoModule(): Module =
    module {
        single<CryptoManager> { IosCryptoManager() }
        single<PlcRecoveryKeyManager> { IosPlcRecoveryKeyManager() }
        // TODO: swap for IosIdentityKeyBackupStore once it lands (see following commit) -- until
        // then iOS keeps its previous no-backup behavior, unchanged from before this commit.
        single<IdentityKeyBackupStore> { NoOpIdentityKeyBackupStore }
    }
