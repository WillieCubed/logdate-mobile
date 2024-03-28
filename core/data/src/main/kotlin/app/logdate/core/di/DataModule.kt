package app.logdate.core.di

import app.logdate.core.data.InMemoryLibraryContentRepository
import app.logdate.core.data.JournalRepository
import app.logdate.core.data.LibraryContentRepository
import app.logdate.core.data.journals.OfflineFirstJournalRepository
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.core.data.rewind.DefaultUserRewindRepository
import app.logdate.core.data.rewind.RewindRepository
import app.logdate.core.data.timeline.DefaultTimelineRepository
import app.logdate.core.data.timeline.TimelineRepository
import app.logdate.core.data.user.OfflineFirstUserStateRepository
import app.logdate.core.data.user.UserStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindTimelineRepository(repository: DefaultTimelineRepository): TimelineRepository

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
}
