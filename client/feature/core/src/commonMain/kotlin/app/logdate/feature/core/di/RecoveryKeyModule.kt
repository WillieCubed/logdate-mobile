package app.logdate.feature.core.di

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.feature.core.settings.account.recoverykey.RecoveryKeyViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection for the Recovery key page under Account. It is the same on every platform;
 * only the P-256 work behind the identity repository differs.
 */
val recoveryKeyModule: Module =
    module {
        viewModel {
            RecoveryKeyViewModel(
                identity = get(),
                connectedServer = get(),
                loadPhrase = { get<IdentityKeyManager>().getStoredRecoveryPhrase()?.words },
                isPhraseConfirmed = { get<IdentityKeyManager>().isRecoveryPhraseVerified() },
            )
        }
    }
