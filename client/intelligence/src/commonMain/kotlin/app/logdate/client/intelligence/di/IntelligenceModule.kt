package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.curation.BeatBucketer
import app.logdate.client.intelligence.curation.DiversitySelector
import app.logdate.client.intelligence.curation.PhotoHardFilter
import app.logdate.client.intelligence.curation.RewindMediaCurator
import app.logdate.client.intelligence.curation.SignificanceScorer
import app.logdate.client.intelligence.entity.moments.MomentExtractor
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.events.EventNamingExtractor
import app.logdate.client.intelligence.milestones.LocationChangeMilestoneDetector
import app.logdate.client.intelligence.milestones.MilestoneDetector
import app.logdate.client.intelligence.narrative.AnnualRewindSequencer
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.intelligence.narrative.WeekNarrativeSynthesizer
import app.logdate.client.intelligence.narrative.YearNarrativeSynthesizer
import app.logdate.client.intelligence.rewind.RewindMessageGenerator
import app.logdate.client.intelligence.rewind.WittyRewindMessageGenerator
import app.logdate.client.intelligence.weather.HistoricalWeatherProvider
import app.logdate.client.intelligence.weather.OpenMeteoHistoricalWeatherProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that provides app intelligence features.
 */
val intelligenceModule: Module =
    module {
        includes(clientsModule)
        includes(cacheModule)
        includes(curationModule)

        single<RewindMessageGenerator> { WittyRewindMessageGenerator() }
        single { EntrySummarizer(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { MomentExtractor(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { EventNamingExtractor(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single { PeopleExtractor(get(), get(), networkAvailabilityMonitor = get(), dataUsagePolicy = get()) }
        single<HistoricalWeatherProvider> { OpenMeteoHistoricalWeatherProvider(get()) }
        single {
            WeekNarrativeSynthesizer(
                generativeAICache = get(),
                genAIClient = get(),
                networkAvailabilityMonitor = get(),
                dataUsagePolicy = get(),
                weatherProvider = get(),
            )
        }
        single { RewindSequencer() }

        // Rewind media curation pipeline. The platform-specific signal extractor is
        // bound by [curationModule] above; the four stages here are stateless.
        single { PhotoHardFilter() }
        single { SignificanceScorer() }
        single { BeatBucketer() }
        single { DiversitySelector() }
        single { RewindMediaCurator(get(), get(), get(), get(), get()) }

        // Annual rewind
        single { YearNarrativeSynthesizer(get(), get(), get(), get()) }
        single { AnnualRewindSequencer() }

        // Milestone detection — registered as a list so the worker iterates the
        // full set without needing to know each detector individually. The list
        // currently has one entry; future detectors (sentiment shift, language
        // pattern) will be appended here.
        single<List<MilestoneDetector>> {
            listOf(
                LocationChangeMilestoneDetector(get()),
            )
        }
    }

expect val cacheModule: Module

expect val curationModule: Module
