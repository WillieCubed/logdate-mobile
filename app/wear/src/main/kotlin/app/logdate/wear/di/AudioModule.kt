package app.logdate.wear.di

import android.app.Application
import android.content.Context
import android.os.VibratorManager
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.client.sync.SyncManager
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.haptic.WearHapticEngine
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.presentation.audio.AudioRecordingViewModel
import app.logdate.wear.presentation.camera.WearRemoteCameraViewModel
import app.logdate.wear.presentation.health.HealthDashboardViewModel
import app.logdate.wear.presentation.home.WearHomeViewModel
import app.logdate.wear.presentation.mood.MoodCheckInViewModel
import app.logdate.wear.presentation.onboarding.WearOnboardingViewModel
import app.logdate.wear.presentation.recording.WearRecordingViewModel
import app.logdate.wear.presentation.rewind.WearRewindViewModel
import app.logdate.wear.presentation.settings.WearSettingsViewModel
import app.logdate.wear.presentation.timeline.WearTimelineViewModel
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.sync.WearDataLayerClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for Wear OS audio recording and capture features.
 */
val wearAudioModule = module {
    single { StorageSpaceChecker(get()) }
    single<AudioStorage> { AndroidAudioStorage(get()) }
    single {
        val vibratorManager = get<Context>()
            .getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        WearHapticEngine(vibratorManager.defaultVibrator)
    }
    single { WearAudioRecordingManager(get(), get(), get()) }
    viewModel {
        AudioRecordingViewModel(
            get<Application>(),
            get<WearAudioRecordingManager>(),
            get<JournalNotesRepository>(),
            get<StorageSpaceChecker>(),
            get<NoteHealthAnnotator>(),
        )
    }
    viewModel {
        WearRecordingViewModel(
            get<WearAudioRecordingManager>(),
            get<JournalNotesRepository>(),
            get<StorageSpaceChecker>(),
            get<NoteHealthAnnotator>(),
            get<WearDataLayerClient>(),
        )
    }
    viewModel {
        MoodCheckInViewModel(
            get<JournalNotesRepository>(),
            get<WearDataLayerClient>(),
        )
    }
    viewModel {
        WearHomeViewModel(
            get<JournalNotesRepository>(),
            get<SyncManager>(),
            get<WearDataLayerClient>(),
        )
    }
    viewModel {
        WearTimelineViewModel(
            get<JournalNotesRepository>(),
        )
    }
    viewModel {
        WearRewindViewModel(
            get<RewindRepository>(),
        )
    }
    viewModel {
        WearRemoteCameraViewModel(
            get(),
        )
    }
    viewModel {
        HealthDashboardViewModel(
            get(),
            get(),
        )
    }
    viewModel {
        WearOnboardingViewModel(
            get<WearDataLayerClient>(),
        )
    }
    viewModel {
        WearSettingsViewModel(
            get<SyncManager>(),
            get<WearDataLayerClient>(),
        )
    }
}
