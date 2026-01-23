package app.logdate.wear.di

import android.app.Application
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioStorage
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.presentation.audio.AudioRecordingViewModel
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.repository.WearJournalNotesRepository
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Minimal Koin module for Wear OS.
 */
val wearAudioModule = module {
    single { StorageSpaceChecker(get()) }
    single<AudioStorage> { AndroidAudioStorage(get()) }
    single { WearJournalNotesRepository(get()) }
    single { WearAudioRecordingManager(get(), get(), get()) }
    viewModel { AudioRecordingViewModel(get<Application>(), get(), get(), get()) }
}
