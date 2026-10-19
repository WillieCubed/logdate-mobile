package app.logdate.feature.core.di

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.feature.core.settings.account.recoverykey.RecoveryKeyViewModel
import app.logdate.feature.core.settings.account.recoverykey.passkeysSupported
import app.logdate.feature.core.settings.account.recoverykey.recoveryKeyOffered
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection for the Recovery key page under Account. It is the same on every platform;
 * only the P-256 work behind the identity repository and the passkey prompt differ.
 */
val recoveryKeyModule: Module =
    module {
        viewModel {
            RecoveryKeyViewModel(
                identity = get(),
                connectedServer = get(),
                offered = recoveryKeyOffered(featureFlags = get(), connectedServer = get(), passkeysSupported = passkeysSupported(get())),
                loadPhrase = { get<IdentityKeyManager>().getStoredRecoveryPhrase()?.words },
                isPhraseConfirmed = { get<IdentityKeyManager>().isRecoveryPhraseVerified() },
            )
        }
    }
