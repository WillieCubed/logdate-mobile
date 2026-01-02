package app.logdate.client.domain.di

import app.logdate.client.domain.app.GetAppInfoUseCase
import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.domain.journals.DeleteJournalUseCase
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.journals.GetJournalByIdUseCase
import app.logdate.client.domain.journals.UpdateJournalUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.domain.onboarding.ProcessPersonalIntroductionUseCase
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.profile.UpdateProfileUseCase
import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.search.SearchEntriesUseCase
import app.logdate.client.domain.rewind.GenerateBasicRewindUseCase
import app.logdate.client.domain.rewind.GenerateRewindTitleUseCase
import app.logdate.client.domain.rewind.GetPastRewindsUseCase
import app.logdate.client.domain.rewind.GetRewindUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.StubMediaManager
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.domain.di.StubIndexedMediaRepository
import app.logdate.client.domain.timeline.GetMediaUrisUseCase
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.GetTimelineBannerUseCase
import app.logdate.client.domain.timeline.GetTimelineDayUseCase
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.domain.timeline.SummarizeJournalEntriesUseCase
import app.logdate.client.domain.di.healthDomainModule
import app.logdate.client.domain.di.locationDomainModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Main domain module with use cases that don't create circular dependencies.
 * Location, Account, and Quota use cases are in separate modules to avoid circular dependencies.
 */
val domainModule: Module = module {
    // Domain module dependencies
    
    // Entities
    factory { ExtractPeopleUseCase(get()) }

    // App info
    factory { GetAppInfoUseCase(get()) }

    // Export
    factory { ExportUserDataUseCase(get(), get(), get(), get(), get(), get()) }

    // Notes
    factory { AddNoteUseCase(
        repository = get(),
        journalContentRepository = get(),
        logLocationUseCase = get(),
        logCurrentLocationUseCase = get(),
        mediaManager = get()
    ) }
    factory { FetchTodayNotesUseCase(get()) }
    factory { FetchNotesForDayUseCase(get()) }
    factory { HasNotesForTodayUseCase(get()) }
    factory { RemoveNoteUseCase(get()) }
    factory { GetAllAudioNotesUseCase(get()) }

    // Drafts
    factory { CreateEntryDraftUseCase(get()) }
    factory { DeleteEntryDraftUseCase(get()) }
    factory { FetchEntryDraftUseCase(get()) }
    factory { FetchMostRecentDraftUseCase(get()) }
    factory { GetAllDraftsUseCase(get()) }
    factory { UpdateEntryDraftUseCase(get()) }

    // Rewind
    factory { GetPastRewindsUseCase(get()) }
    factory { GetRewindUseCase(get(), get(), get()) }
    factory { GetWeekRewindUseCase(get()) }
    factory { GenerateRewindTitleUseCase() }
    
    // Media indexing
    factory { IndexMediaForPeriodUseCase(get(), get()) }
    
    // Media manager for accessing device media
    single<MediaManager> { StubMediaManager() }
    
    // Note: The actual implementations for these repositories are provided by the platform-specific
    // data modules. These stub implementations are provided as fallbacks for testing.
    single<IndexedMediaRepository> { StubIndexedMediaRepository() }
    single<RewindGenerationManager> { StubRewindGenerationManager() }
    
    // Create the GenerateBasicRewindUseCase with all its dependencies
    factory { GenerateBasicRewindUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Timeline
    factory { GetMediaUrisUseCase(get()) }
    factory { GetTimelineUseCase(get(), get()) }
    factory { GetStreamingTimelineUseCase(get(), get()) }
    factory { GetTimelineDayUseCase(get(), get(), get()) }
    factory { GetTimelineBannerUseCase(get(), get()) }
    factory { SummarizeJournalEntriesUseCase(get()) }
    
    // Include health domain module
    includes(healthDomainModule)
    
    // Include location domain module
    includes(locationDomainModule)

    // Onboarding
    factory { ProcessPersonalIntroductionUseCase(get(), get(), get()) }

    // Journals
    factory { GetCurrentUserJournalsUseCase(get()) }
    factory { GetDefaultSelectedJournalsUseCase(get(), get()) }
    factory { GetJournalByIdUseCase(get()) }
    factory { UpdateJournalUseCase(get()) }
    factory { DeleteJournalUseCase(get()) }

    // Places
    factory { ResolveLocationToPlaceUseCase(get(), get()) }
    
    // Profile
    factory { UpdateProfileUseCase(get()) }

    // Search
    factory { SearchEntriesUseCase(get()) }
}
