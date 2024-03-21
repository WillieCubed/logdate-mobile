package app.logdate.core.di

import app.logdate.core.data.DefaultJournalRepository
import app.logdate.core.data.InMemoryLibraryContentRepository
import app.logdate.core.data.JournalRepository
import app.logdate.core.data.LibraryContentRepository
import app.logdate.core.data.notes.InMemoryJournalNotesRepository
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.data.rewind.DefaultUserRewindRepository
import app.logdate.core.data.rewind.RewindRepository
import app.logdate.core.data.timeline.DefaultTimelineRepository
import app.logdate.core.data.timeline.TimelineRepository
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
    abstract fun bindJournalRepository(repository: DefaultJournalRepository): JournalRepository

    @Binds
    abstract fun bindJournalNotesRepository(repository: InMemoryJournalNotesRepository): JournalNotesRepository

    @Binds
    abstract fun bindLibraryContentRepository(repository: InMemoryLibraryContentRepository): LibraryContentRepository

    @Binds
    abstract fun bindRewindRepository(repository: DefaultUserRewindRepository): RewindRepository
}
