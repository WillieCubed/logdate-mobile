package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.rewind.DefaultRewindGenerator
import app.logdate.client.intelligence.rewind.RewindGenerator
import app.logdate.client.intelligence.rewind.RewindMessageGenerator
import app.logdate.client.intelligence.rewind.WittyRewindMessageGenerator
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that provides app intelligence features.
 */
val intelligenceModule: Module = module {
    includes(clientsModule)
    includes(cacheModule)

    single<RewindGenerator> { DefaultRewindGenerator() }
    single<RewindMessageGenerator> { WittyRewindMessageGenerator() }
    single { EntrySummarizer(get(), get()) }
    single { PeopleExtractor(get(), get()) }
}

expect val cacheModule: Module