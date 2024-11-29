package app.logdate.feature.onboarding.di

import app.logdate.client.di.billingModule
import app.logdate.feature.onboarding.editor.AudioEntryRecorder
import app.logdate.feature.onboarding.editor.StubAudioEntryRecorder
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

actual val onboardingFeatureModule: Module = module {
    includes(billingModule)
    factory<AudioEntryRecorder> { StubAudioEntryRecorder }
    viewModel { OnboardingViewModel(get(), get(), get()) }
}