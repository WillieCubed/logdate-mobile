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
        // EventsSettingsViewModel and CalendarSyncOverviewViewModel both take a
        // `clock: () -> Instant` constructor parameter with a default value for testability.
        // Koin's `viewModelOf` resolves *every* parameter from the graph, including the
        // lambda — which it can't, because there's no `Function0<Instant>` binding. Bind
        // them explicitly instead so the default clock lambda kicks in at production sites.
        viewModel { EventsSettingsViewModel(preferences = get(), inferenceLauncher = get()) }
        viewModel {
            CalendarSyncOverviewViewModel(
                preferences = get(),
                launcher = get(),
                deviceCalendarReader = get(),
            )
        }
        viewModelOf(::CalendarSyncCalendarsViewModel)
        viewModelOf(::CalendarSyncActivityViewModel)
        // Same `clock` lambda problem as the two settings VMs above — bind explicitly so
        // the default lambda survives Koin's reflective resolver.
        viewModel { EventsCalendarViewModel(observeEventsForMonth = get()) }
    }
