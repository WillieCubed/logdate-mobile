package app.logdate.feature.core.di

import app.logdate.client.domain.di.domainModule
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.StubBiometricGatekeeper
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
    single<BiometricGatekeeper> { StubBiometricGatekeeper() }

    viewModel { AppViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}