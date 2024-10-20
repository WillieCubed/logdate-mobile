package app.logdate.core.di

import app.logdate.core.data.journals.JournalRepository
import app.logdate.core.data.journals.OfflineFirstJournalRepository
import app.logdate.core.data.media.InMemoryLibraryContentRepository
import app.logdate.core.data.media.LibraryContentRepository
import app.logdate.core.data.notes.ExportableJournalContentRepository
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.core.data.rewind.DefaultUserRewindRepository
import app.logdate.core.data.rewind.RewindRepository
import app.logdate.core.data.timeline.ActivityTimelineRepository
import app.logdate.core.data.timeline.OfflineFirstActivityTimelineRepository
import app.logdate.core.data.timeline.cache.GenerativeAICache
import app.logdate.core.data.timeline.cache.OfflineGenerativeAICache
import app.logdate.core.data.user.OfflineFirstUserStateRepository
import app.logdate.core.data.user.UserStateRepository
import app.logdate.core.data.user.cloud.DefaultUserAccountRepository
import app.logdate.core.data.user.cloud.RemoteUserAccountRepository
import app.logdate.core.data.user.devices.DefaultUserDeviceRepository
import app.logdate.core.data.user.devices.UserDeviceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindTimelineRepository(repository: OfflineFirstActivityTimelineRepository): ActivityTimelineRepository

    @Binds
    abstract fun bindJournalRepository(repository: OfflineFirstJournalRepository): JournalRepository

    @Binds
    abstract fun bindJournalNotesRepository(repository: OfflineFirstJournalNotesRepository): JournalNotesRepository

    @Binds
    abstract fun bindLibraryContentRepository(repository: InMemoryLibraryContentRepository): LibraryContentRepository

    @Binds
    abstract fun bindRewindRepository(repository: DefaultUserRewindRepository): RewindRepository

    @Binds
    abstract fun bindUserStateRepository(repository: OfflineFirstUserStateRepository): UserStateRepository

    @Binds
    abstract fun bindUserDeviceRepository(repository: DefaultUserDeviceRepository): UserDeviceRepository

    @Binds
    abstract fun bindUserAccountRepository(repository: DefaultUserAccountRepository): RemoteUserAccountRepository

    @Binds
    abstract fun bindExportableJournalContentRepository(repository: OfflineFirstJournalNotesRepository): ExportableJournalContentRepository

    @Binds
    abstract fun bindGenerativeAICache(cache: OfflineGenerativeAICache): GenerativeAICache
}
