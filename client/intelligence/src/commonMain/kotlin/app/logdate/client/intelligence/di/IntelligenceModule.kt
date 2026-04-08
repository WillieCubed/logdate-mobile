package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.entity.moments.MomentExtractor
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.events.EventNamingExtractor
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.intelligence.narrative.WeekNarrativeSynthesizer
import app.logdate.client.intelligence.rewind.RewindMessageGenerator
import app.logdate.client.intelligence.rewind.WittyRewindMessageGenerator
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that provides app intelligence features.
 */
val intelligenceModule: Module =
    module {
        includes(clientsModule)
        includes(cacheModule)

        single<RewindMessageGenerator> { WittyRewindMessageGenerator() }
        single { EntrySummarizer(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { MomentExtractor(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { EventNamingExtractor(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { PeopleExtractor(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { WeekNarrativeSynthesizer(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { RewindSequencer() }
    }

expect val cacheModule: Module
