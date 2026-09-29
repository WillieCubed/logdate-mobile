package app.logdate.feature.onboarding.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.di.billingModule
import app.logdate.client.media.AndroidManagedMediaDiscarder
import app.logdate.client.media.AndroidManagedMediaImportSource
import app.logdate.client.media.ManagedMediaImporter
import app.logdate.client.media.MediaManager
import app.logdate.feature.core.settings.ui.OnboardingStateResetter
import app.logdate.feature.onboarding.flow.KeyValueOnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingCompletionCoordinator
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import app.logdate.feature.onboarding.ui.OnboardingViewModel
import app.logdate.feature.onboarding.ui.PersonalIntroViewModel
import app.logdate.feature.onboarding.ui.RecoveryPhraseViewModel
import app.logdate.feature.onboarding.ui.SelectedMemoryMediaImporter
import app.logdate.feature.onboarding.ui.WelcomeBackViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Module for onboarding functionality
 */
actual val onboardingFeatureModule: Module =
    module {
        includes(billingModule)
        single<OnboardingDeviceStateRepository> {
            KeyValueOnboardingDeviceStateRepository(get<KeyValueStorage>(named("deviceKeyValueStorage")))
        }
        single<OnboardingStateResetter> { OnboardingStateResetter { get<OnboardingDeviceStateRepository>().clear() } }
        single { OnboardingCompletionCoordinator(get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        single<SelectedMemoryMediaImporter> {
            val manager = get<MediaManager>()
            val context = androidContext().applicationContext
            val discarder = AndroidManagedMediaDiscarder(context, manager)
            val importer = ManagedMediaImporter(manager, AndroidManagedMediaImportSource(context), discarder::discard)
            object : SelectedMemoryMediaImporter {
                override suspend fun import(sourceUri: String): String = importer.import(sourceUri)

                override suspend fun discard(managedUri: String) = discarder.discard(managedUri)
            }
        }
        viewModel { MemorySelectionViewModel(get(), get(), get(), get(), get()) }
        viewModel { PersonalIntroViewModel(get(), get()) }
        viewModel { WelcomeBackViewModel(get(), get(), get()) }
        viewModel { RecoveryPhraseViewModel(get()) }
    }
