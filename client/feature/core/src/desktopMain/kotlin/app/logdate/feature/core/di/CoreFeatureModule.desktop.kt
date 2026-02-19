package app.logdate.feature.core.di

import app.logdate.client.domain.di.accountModule
import app.logdate.client.domain.di.domainModule
import app.logdate.client.location.di.locationSettingsModule
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.StubBiometricGatekeeper
import app.logdate.feature.core.export.DesktopExportLauncher
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.restore.DesktopRestoreLauncher
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.core.profile.ui.ProfileViewModel
import app.logdate.feature.core.settings.ui.AccountSettingsViewModel
import app.logdate.feature.core.settings.ui.AdvancedSettingsViewModel
import app.logdate.feature.core.settings.ui.DataSettingsViewModel
import app.logdate.feature.core.settings.ui.DangerZoneSettingsViewModel
import app.logdate.feature.core.settings.ui.PrivacySettingsViewModel
import app.logdate.feature.core.settings.ui.LocationSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature module exposing core app screens and functionality.
 */
actual val coreFeatureModule: Module = module {
    // Include our domain modules - note that accountModule is included separately to avoid circular deps
    includes(domainModule)
    includes(accountModule)
    includes(devicesModule)
    includes(locationSettingsModule)
    
    // TODO: Refactor to separate auth module
    single<BiometricGatekeeper> { StubBiometricGatekeeper() }
    
    // Export functionality for desktop
    single<ExportLauncher> { DesktopExportLauncher() }
    single<RestoreLauncher> { DesktopRestoreLauncher() }

    viewModel { AppViewModel(get(), get(), get()) }
    viewModel {
        AccountSettingsViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel {
        PrivacySettingsViewModel(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel {
        DataSettingsViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel { AdvancedSettingsViewModel(get(), get()) }
    viewModel {
        DangerZoneSettingsViewModel(
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { CloudAccountOnboardingViewModel(get(), get(), get()) }
    // TODO(desktop): Wire location settings UX and platform permissions; keep settings storage available for now.
    viewModel { LocationSettingsViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get()) }
}
