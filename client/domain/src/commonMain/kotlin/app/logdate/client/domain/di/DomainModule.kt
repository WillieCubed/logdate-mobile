package app.logdate.client.domain.di

import app.logdate.client.domain.app.GetAppInfoUseCase
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.dayboundary.DefaultDayBoundarySettingsRepository
import app.logdate.client.domain.di.StubIndexedMediaRepository
import app.logdate.client.domain.di.healthDomainModule
import app.logdate.client.domain.di.locationDomainModule
import app.logdate.client.domain.editor.ObserveEditorDataUseCase
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.domain.events.DeleteEventUseCase
import app.logdate.client.domain.events.GetAttachableNotesForEventUseCase
import app.logdate.client.domain.events.GetEventByIdUseCase
import app.logdate.client.domain.events.LinkNoteToEventUseCase
import app.logdate.client.domain.events.ObserveEventsForDateRangeUseCase
import app.logdate.client.domain.events.ObserveEventsForNoteUseCase
import app.logdate.client.domain.events.ObserveLinkedNotesForEventUseCase
import app.logdate.client.domain.events.ObserveNotesForEventUseCase
import app.logdate.client.domain.events.ObserveUserPlacesUseCase
import app.logdate.client.domain.events.UnlinkNoteFromEventUseCase
import app.logdate.client.domain.events.UpdateEventUseCase
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.domain.export.GetExportCountsUseCase
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.journals.DeleteJournalUseCase
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.journals.GetJournalByIdUseCase
import app.logdate.client.domain.journals.SuggestJournalsUseCase
import app.logdate.client.domain.journals.UpdateJournalUseCase
import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.domain.onboarding.ProcessPersonalIntroductionUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.profile.UpdateProfileUseCase
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.DefaultAmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.DefaultMemoriesSettingsRepository
import app.logdate.client.domain.recommendation.DefaultPlaceFamiliarityRepository
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.PlaceFamiliarityRepository
import app.logdate.client.domain.restore.PreviewArchiveUseCase
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.domain.rewind.GenerateBasicRewindUseCase
import app.logdate.client.domain.rewind.GenerateRewindTitleUseCase
import app.logdate.client.domain.rewind.GetPastRewindsUseCase
import app.logdate.client.domain.rewind.GetRewindUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.search.ObserveRecentSearchesUseCase
import app.logdate.client.domain.search.SearchEntriesUseCase
import app.logdate.client.domain.search.SearchInJournalUseCase
import app.logdate.client.domain.search.SearchJournalsUseCase
import app.logdate.client.domain.search.UniversalSearchUseCase
import app.logdate.client.domain.streak.CalculateStreakUseCase
import app.logdate.client.domain.streak.ObserveStreakUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.domain.streak.SetStreakEnabledUseCase
import app.logdate.client.domain.timeline.GetJournalMembershipUseCase
import app.logdate.client.domain.timeline.GetMediaUrisUseCase
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.GetTimelineDayUseCase
import app.logdate.client.domain.timeline.GetTimelinePageUseCase
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.domain.timeline.GroupNotesByDayBoundsUseCase
import app.logdate.client.domain.timeline.InferMomentsUseCase
import app.logdate.client.domain.timeline.SummarizeJournalEntriesUseCase
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Main domain module with use cases that don't create circular dependencies.
 * Location, Account, and Quota use cases are in separate modules to avoid circular dependencies.
 */
val domainModule: Module =
    module {
        // Domain module dependencies

        // Entities
        factory { ExtractPeopleUseCase(get()) }

        // App info
        factory { GetAppInfoUseCase(get()) }

        // Export
        factory { ExportUserDataUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
        factory { GetExportCountsUseCase(get(), get()) }
        factory { RestoreUserDataUseCase(get(), get(), get(), get(), get(), get()) }
        factory { PreviewArchiveUseCase() }

        // Notes
        factory {
            AddNoteUseCase(
                repository = get(),
                journalContentRepository = get(),
                logLocationUseCase = get(),
                logCurrentLocationUseCase = get(),
                settingsRepository = get(),
                mediaManager = get(),
            )
        }
        factory { FetchEntryUseCase(get()) }
        factory { FetchTodayNotesUseCase(get()) }
        factory { FetchNotesForDayUseCase(get()) }
        factory { HasNotesForTodayUseCase(get()) }
        factory { RemoveNoteUseCase(get()) }
        factory { GetAllAudioNotesUseCase(get()) }

        // Drafts
        factory { CleanupExpiredDraftsUseCase(get()) }
        factory { CreateEntryDraftUseCase(get()) }
        factory { DeleteAllDraftsUseCase(get()) }
        factory { DeleteEntryDraftUseCase(get()) }
        factory { FetchEntryDraftUseCase(get()) }
        factory { FetchMostRecentDraftUseCase(get()) }
        factory { GetAllDraftsUseCase(get()) }
        factory { UpdateEntryDraftUseCase(get()) }

        // Editor
        factory { ObserveEditorDataUseCase(get(), get(), get(), get()) }
        factory { SaveEntryUseCase(get(), get()) }

        // Rewind
        factory { GetPastRewindsUseCase(get()) }
        factory { GetRewindUseCase(get(), get(), get()) }
        factory {
            val prefs: app.logdate.client.datastore.LogdatePreferencesDataSource = get()
            GetWeekRewindUseCase(get(), prefs.observeFirstDayOfWeek())
        }
        factory { GenerateRewindTitleUseCase() }

        // Media indexing
        factory { IndexMediaForPeriodUseCase(get(), get()) }

        // Note: The actual implementations for these repositories are provided by the platform-specific
        // data modules. These stub implementations are provided as fallbacks for testing.
        single<IndexedMediaRepository> { StubIndexedMediaRepository() }
        single<RewindGenerationManager> { StubRewindGenerationManager() }

        // Create the GenerateBasicRewindUseCase with all its dependencies
        factory { GenerateBasicRewindUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

        // Timeline
        factory { GetJournalMembershipUseCase(get()) }
        factory { GetMediaUrisUseCase(get()) }
        factory { GroupNotesByDayBoundsUseCase(get(), get()) }
        factory { GetTimelineUseCase(get(), get(), get(), get()) }
        factory { GetStreamingTimelineUseCase(get(), get(), get()) }
        factory { GetTimelinePageUseCase(get(), get()) }
        factory { InferMomentsUseCase(get(), get()) }
        factory { GetTimelineDayUseCase(get(), get(), get(), get()) }
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
        factory { SuggestJournalsUseCase(get(), get()) }
        factory { GetJournalByIdUseCase(get()) }
        factory { UpdateJournalUseCase(get()) }
        factory { DeleteJournalUseCase(get()) }

        // Events
        factory { ObserveEventsForDateRangeUseCase(get()) }
        factory { GetEventByIdUseCase(get()) }
        factory { UpdateEventUseCase(get()) }
        factory { DeleteEventUseCase(get()) }
        factory { ObserveEventsForNoteUseCase(get()) }
        factory { ObserveNotesForEventUseCase(get()) }
        factory { LinkNoteToEventUseCase(get()) }
        factory { UnlinkNoteFromEventUseCase(get()) }
        factory { ObserveLinkedNotesForEventUseCase(get(), get()) }
        factory { GetAttachableNotesForEventUseCase(get(), get()) }
        factory { ObserveUserPlacesUseCase(get()) }

        // Places
        factory { ResolveLocationToPlaceUseCase(get(), get(), get()) }
        single { PlaceResolutionCache(get()) }

        // Profile
        factory { UpdateProfileUseCase(get()) }

        // Identity
        factory { ObserveUserIdentityUseCase(get(), get(), get(), get()) }

        // Search
        factory { SearchEntriesUseCase(get()) }
        factory { SearchInJournalUseCase(get()) }
        factory { SearchJournalsUseCase(get()) }
        factory { UniversalSearchUseCase(get()) }
        factory { ObserveRecentSearchesUseCase(get()) }

        // Recommendations
        factory { GetMemoryRecallUseCase(get(), getOrNull()) }
        factory { GetHomeRecommendationUseCase(get(), get(), get(), get(), get(), get()) }
        factory { GenerateAmbientPromptCandidatesUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
        single<AmbientPromptHistoryRepository> { DefaultAmbientPromptHistoryRepository(get()) }
        single<PlaceFamiliarityRepository> { DefaultPlaceFamiliarityRepository(get()) }
        single<MemoriesSettingsRepository> { DefaultMemoriesSettingsRepository(get()) }

        // Streaks
        factory { CalculateStreakUseCase(get()) }
        factory { ObserveStreakUseCase(get()) }
        single { RefreshStreakUseCase(get(), get()) }
        factory { SetStreakEnabledUseCase(get()) }

        // Day boundaries
        single<DayBoundarySettingsRepository> { DefaultDayBoundarySettingsRepository(get(), get()) }
    }
