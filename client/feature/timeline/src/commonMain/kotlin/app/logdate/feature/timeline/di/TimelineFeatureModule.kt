package app.logdate.feature.timeline.di

import app.logdate.client.domain.di.domainModule
import app.logdate.client.intelligence.di.intelligenceModule
import app.logdate.client.networking.di.networkingModule
import app.logdate.feature.timeline.ui.TimelineViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val timelineFeatureModule: Module = module {
    includes(domainModule)
    includes(intelligenceModule)
    includes(networkingModule)

    single<TimelineViewModel> { TimelineViewModel(get(), get(), get()) }
}