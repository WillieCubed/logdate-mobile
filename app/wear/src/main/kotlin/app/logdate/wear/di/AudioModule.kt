package app.logdate.wear.di

import android.app.Application
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.presentation.audio.AudioRecordingViewModel
import app.logdate.wear.recording.WearAudioRecordingManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for Wear OS audio recording and capture features.
 */
val wearAudioModule = module {
    single { StorageSpaceChecker(get()) }
    single<AudioStorage> { AndroidAudioStorage(get()) }
    single { WearAudioRecordingManager(get(), get(), get()) }
    viewModel {
        AudioRecordingViewModel(
            get<Application>(),
            get<WearAudioRecordingManager>(),
            get<JournalNotesRepository>(),
            get<StorageSpaceChecker>(),
        )
    }
}
