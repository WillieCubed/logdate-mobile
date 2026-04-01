package app.logdate.feature.onboarding.di

import app.logdate.client.di.billingModule
import app.logdate.feature.onboarding.flow.InMemoryOnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import app.logdate.feature.onboarding.ui.PersonalIntroViewModel
import app.logdate.feature.onboarding.ui.WelcomeBackViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

actual val onboardingFeatureModule: Module =
    module {
        includes(billingModule)
        single<OnboardingDeviceStateRepository> { InMemoryOnboardingDeviceStateRepository() }
        viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { MemorySelectionViewModel(get(), get()) }
        viewModel { PersonalIntroViewModel(get()) }
        viewModel { WelcomeBackViewModel(get()) }
    }
