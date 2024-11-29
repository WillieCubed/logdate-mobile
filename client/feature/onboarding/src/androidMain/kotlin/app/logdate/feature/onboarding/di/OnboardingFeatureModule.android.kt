package app.logdate.feature.onboarding.di

import app.logdate.client.di.billingModule
import app.logdate.feature.onboarding.AndroidAudioEntryRecorder
import app.logdate.feature.onboarding.editor.AudioEntryRecorder
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Module for onboarding functionality
 */
actual val onboardingFeatureModule: Module = module {
    includes(billingModule)
    factory<AudioEntryRecorder> { AndroidAudioEntryRecorder(get()) }
    viewModel { OnboardingViewModel(get(), get(), get()) }
}