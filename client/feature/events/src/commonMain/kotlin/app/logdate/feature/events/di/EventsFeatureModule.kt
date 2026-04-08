package app.logdate.feature.events.di

import app.logdate.feature.events.ui.EventDetailViewModel
import app.logdate.feature.events.ui.calendarsync.CalendarSyncActivityViewModel
import app.logdate.feature.events.ui.calendarsync.CalendarSyncCalendarsViewModel
import app.logdate.feature.events.ui.calendarsync.CalendarSyncOverviewViewModel
import app.logdate.feature.events.ui.settings.EventsSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val eventsFeatureModule: Module =
    module {
        viewModelOf(::EventDetailViewModel)
        viewModelOf(::EventsSettingsViewModel)
        viewModelOf(::CalendarSyncOverviewViewModel)
        viewModelOf(::CalendarSyncCalendarsViewModel)
        viewModelOf(::CalendarSyncActivityViewModel)
    }
