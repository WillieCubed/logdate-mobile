package app.logdate.feature.core.di

import android.app.Activity
import app.logdate.client.domain.di.accountModule
import app.logdate.client.domain.di.domainModule
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.account.ui.SaveAccountSetupDataUseCase
import app.logdate.feature.core.export.AndroidExportLauncher
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.core.profile.ui.ProfileViewModel
import app.logdate.feature.core.settings.ui.LocationSettingsViewModel
import app.logdate.feature.core.settings.ui.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
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
    
    // TODO: Refactor to separate auth module
    single<BiometricGatekeeper> { AndroidBiometricGatekeeper() }
    single { AndroidBiometricGatekeeper() }
    
    // Export functionality with activity provider for file picker
    // We use lazy provider for current activity that will be set by the MainActivity
    single { ActivityProvider() }
    
    // Create AndroidExportLauncher and expose it both as itself and as ExportLauncher interface
    single<ExportLauncher> { AndroidExportLauncher(androidContext()) }
    single { AndroidExportLauncher(androidContext()) }

    // Account setup helpers
    factoryOf(::SaveAccountSetupDataUseCase)

    viewModel { AppViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { CloudAccountOnboardingViewModel(get(), get(), get()) }
    viewModel { LocationSettingsViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get()) }
}

// TODO: Fix this obvious code smell
/**
 * Helper class to hold a reference to the current activity.
 * This is needed for launching the file picker.
 */
class ActivityProvider {
    var currentActivity: Activity? = null
}