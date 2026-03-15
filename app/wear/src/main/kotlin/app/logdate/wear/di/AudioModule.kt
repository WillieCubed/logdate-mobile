package app.logdate.wear.di

import android.app.Application
import android.content.Context
import android.os.VibratorManager
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.haptic.WearHapticEngine
import app.logdate.wear.presentation.audio.AudioRecordingViewModel
import app.logdate.wear.presentation.home.WearHomeViewModel
import app.logdate.wear.presentation.mood.MoodCheckInViewModel
import app.logdate.wear.presentation.recording.WearRecordingViewModel
import app.logdate.wear.presentation.timeline.WearTimelineViewModel
import app.logdate.wear.recording.WearAudioRecordingManager
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
        )
    }
    viewModel {
        WearRecordingViewModel(
            get<WearAudioRecordingManager>(),
            get<JournalNotesRepository>(),
            get<StorageSpaceChecker>(),
        )
    }
    viewModel {
        MoodCheckInViewModel(
            get<JournalNotesRepository>(),
        )
    }
    viewModel {
        WearHomeViewModel(
            get<JournalNotesRepository>(),
        )
    }
    viewModel {
        WearTimelineViewModel(
            get<JournalNotesRepository>(),
        )
    }
}
