package app.logdate.client.data.di

import app.logdate.client.data.journals.JournalUserDataRepository
import app.logdate.client.data.journals.LocalFirstDraftRepository
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.journals.OfflineFirstJournalUserDataRepository
import app.logdate.client.data.journals.RemoteJournalDataSource
import app.logdate.client.data.journals.StubJournalDataSource
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.data.notes.drafts.IosLocalEntryDraftStore
import app.logdate.client.data.notes.drafts.LocalEntryDraftStore
import app.logdate.client.data.notes.drafts.OfflineFirstEntryDraftRepository
import app.logdate.client.data.rewind.DefaultRewindGenerationManager
import app.logdate.client.data.rewind.OfflineFirstRewindRepository
import app.logdate.client.data.media.OfflineIndexedMediaRepository
import app.logdate.client.data.timeline.OfflineFirstActivityTimelineRepository
import app.logdate.client.data.transcription.OfflineFirstTranscriptionRepository
import app.logdate.client.data.location.OfflineFirstLocationHistoryRepository
import app.logdate.client.data.location.StubLocationHistoryRepository
import app.logdate.client.data.places.StubUserPlacesRepository
import app.logdate.client.data.profile.OfflineFirstProfileRepository
import app.logdate.client.data.account.StubAccountRepository
import app.logdate.client.data.quota.StubRemoteQuotaDataSource
import app.logdate.client.data.search.OfflineFirstSearchRepository
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.data.user.StubUserDeviceRepository
import app.logdate.client.data.user.StubUserStateRepository
import app.logdate.client.database.databaseModule
import app.logdate.client.device.di.deviceInstanceModule
import app.logdate.client.di.datastoreModule
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.repository.user.devices.UserDeviceRepository
import app.logdate.client.permissions.di.permissionsModule
import app.logdate.shared.config.configModule
import org.koin.core.module.Module
import org.koin.dsl.module

actual val dataModule: Module = module {
    includes(deviceInstanceModule)
    includes(datastoreModule)
    includes(databaseModule)
    includes(configModule)
    includes(permissionsModule)

    // Journals
    factory<RemoteJournalDataSource> { StubJournalDataSource }
    single<JournalUserDataRepository> { OfflineFirstJournalUserDataRepository(get()) }
    single<DraftRepository> { LocalFirstDraftRepository(get(), get()) }
    single<JournalRepository> {
        OfflineFirstJournalRepository(
            get(),
            get(),
            get(),
            syncManagerProvider = { get() },
            syncMetadataService = get()
        )
    }

    // Notes
    single<JournalNotesRepository> {
        OfflineFirstJournalNotesRepository(
            get(), // textNoteDao
            get(), // imageNoteDao
            get(), // voiceNoteDao
            get(), // videoNoteDao
            get(), // journalNotesDao
            get(), // journalRepository
            syncManagerProvider = { get() },
            syncMetadataService = get()
        )
    }
    single<JournalContentRepository> {
        OfflineFirstJournalContentRepository(
            get(),
            get(),
            get(),
            syncMetadataService = get()
        )
    }

    single<EntryDraftRepository> { OfflineFirstEntryDraftRepository(get(), get()) }
    factory<LocalEntryDraftStore> { IosLocalEntryDraftStore() }

    // Rewind
    single<RewindRepository> { OfflineFirstRewindRepository(get()) }
    single<RewindGenerationManager> { DefaultRewindGenerationManager(get()) }
    
    // Media
    single<IndexedMediaRepository> { OfflineIndexedMediaRepository(get()) }

    // Timeline
    single<ActivityTimelineRepository> { OfflineFirstActivityTimelineRepository() }

    // Location
    single<LocationHistoryRepository> { OfflineFirstLocationHistoryRepository(get()) }
    
    // Places
    single<UserPlacesRepository> { StubUserPlacesRepository() }

    // Profile
    single<ProfileRepository> { OfflineFirstProfileRepository(get()) }

    // Account
    single<AccountRepository> { StubAccountRepository() }

    // User
    single<UserDeviceRepository> { StubUserDeviceRepository }
    single<UserStateRepository> { StubUserStateRepository }

    // Quota
    factory<RemoteQuotaDataSource> { StubRemoteQuotaDataSource() }
    
    // Transcription
    single<TranscriptionRepository> {
        OfflineFirstTranscriptionRepository(
            get(), // transcriptionDao
            get(), // voiceNoteDao
            get()  // transcriptionManager
        )
    }

    // Search
    single<SearchRepository> { OfflineFirstSearchRepository(get()) }
}
