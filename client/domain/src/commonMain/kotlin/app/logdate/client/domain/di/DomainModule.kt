package app.logdate.client.domain.di

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.rewind.GetPastRewindsUseCase
import app.logdate.client.domain.rewind.GetRewindUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.timeline.GetMediaUrisUseCase
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.domain.timeline.SummarizeJournalEntriesUseCase
import app.logdate.client.domain.world.GetLocationUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.domain.world.ObserveLocationUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

val domainModule: Module = module {
    factory { AddNoteUseCase(get(), get(), get()) }
    factory { FetchTodayNotesUseCase(get()) }
    factory { LogLocationUseCase(get(), get()) }

    // Rewind
    factory { GetPastRewindsUseCase(get()) }
    factory { GetRewindUseCase(get()) }
    factory { GetWeekRewindUseCase(get()) }

    // Timeline
    factory { GetMediaUrisUseCase(get()) }
    factory { GetTimelineUseCase(get(), get(), get(), get()) }
    factory { SummarizeJournalEntriesUseCase(get(), get()) }

    // World
    factory { GetLocationUseCase(get()) }
    factory { ObserveLocationUseCase(get()) }
    factory { LogLocationUseCase(get(), get()) }
}