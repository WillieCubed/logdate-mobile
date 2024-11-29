package app.logdate.feature.core.di

import app.logdate.client.domain.di.domainModule
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.core.settings.ui.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature module exposing core app screens and functionality.
 */
actual val coreFeatureModule: Module = module {
    includes(domainModule)
    // TODO: Refactor to separate auth module
    single<BiometricGatekeeper> { AndroidBiometricGatekeeper() }
    // Provide AndroidBiometricGatekeeper so it can be injected manually in activity
    single { get<BiometricGatekeeper>() as AndroidBiometricGatekeeper }

    viewModel { AppViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { HomeViewModel(get()) }
}