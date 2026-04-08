package app.logdate.feature.events.di

import app.logdate.feature.events.ui.EventDetailViewModel
import app.logdate.feature.events.ui.calendar.EventsCalendarViewModel
import app.logdate.feature.events.ui.calendarsync.CalendarSyncActivityViewModel
import app.logdate.feature.events.ui.calendarsync.CalendarSyncCalendarsViewModel
import app.logdate.feature.events.ui.calendarsync.CalendarSyncOverviewViewModel
import app.logdate.feature.events.ui.settings.EventsSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val eventsFeatureModule: Module =
    module {
        viewModelOf(::EventDetailViewModel)
        viewModelOf(::EventsSettingsViewModel)
        viewModelOf(::CalendarSyncOverviewViewModel)
        viewModelOf(::CalendarSyncCalendarsViewModel)
        viewModelOf(::CalendarSyncActivityViewModel)
        // EventsCalendarViewModel still has a `clock: () -> Instant` constructor parameter
        // with a default value for testability. Koin's `viewModelOf` resolves *every*
        // parameter from the graph, including the lambda — which it can't, because there's
        // no `Function0<Instant>` binding. Bind it explicitly so the default clock lambda
        // kicks in at production sites.
        viewModel { EventsCalendarViewModel(observeEventsForMonth = get()) }
    }
