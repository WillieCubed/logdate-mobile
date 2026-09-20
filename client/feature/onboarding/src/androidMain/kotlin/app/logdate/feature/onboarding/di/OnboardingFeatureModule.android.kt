package app.logdate.feature.onboarding.di

import app.logdate.client.di.billingModule
import app.logdate.feature.onboarding.flow.NoBackupOnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import app.logdate.feature.onboarding.ui.PersonalIntroViewModel
import app.logdate.feature.onboarding.ui.RecoveryPhraseViewModel
import app.logdate.feature.onboarding.ui.WelcomeBackViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Module for onboarding functionality
 */
actual val onboardingFeatureModule: Module =
    module {
        includes(billingModule)
        single<OnboardingDeviceStateRepository> { NoBackupOnboardingDeviceStateRepository(androidContext()) }
        viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { MemorySelectionViewModel(get(), get()) }
        viewModel { PersonalIntroViewModel(get(), get()) }
        viewModel { WelcomeBackViewModel(get()) }
        viewModel { RecoveryPhraseViewModel(get()) }
    }
