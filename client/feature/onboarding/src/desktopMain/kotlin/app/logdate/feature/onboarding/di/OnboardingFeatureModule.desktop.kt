package app.logdate.feature.onboarding.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.di.billingModule
import app.logdate.feature.core.settings.ui.OnboardingStateResetter
import app.logdate.feature.onboarding.flow.KeyValueOnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import app.logdate.feature.onboarding.ui.PersonalIntroViewModel
import app.logdate.feature.onboarding.ui.RecoveryPhraseViewModel
import app.logdate.feature.onboarding.ui.WelcomeBackViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual val onboardingFeatureModule: Module =
    module {
        includes(billingModule)
        single<OnboardingDeviceStateRepository> {
            KeyValueOnboardingDeviceStateRepository(get<KeyValueStorage>(named("deviceKeyValueStorage")))
        }
        single<OnboardingStateResetter> { OnboardingStateResetter { get<OnboardingDeviceStateRepository>().clear() } }
        viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { MemorySelectionViewModel(get(), get()) }
        viewModel { PersonalIntroViewModel(get(), get()) }
        viewModel { WelcomeBackViewModel(get(), get()) }
        viewModel { RecoveryPhraseViewModel(get()) }
    }
