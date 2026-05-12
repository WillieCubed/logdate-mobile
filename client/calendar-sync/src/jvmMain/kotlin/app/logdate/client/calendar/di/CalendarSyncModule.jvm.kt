package app.logdate.client.calendar.di

import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.calendar.UnavailableDeviceCalendarReader
import org.koin.core.module.Module
import org.koin.dsl.module

actual val calendarSyncModule: Module =
    module {
        single<DeviceCalendarReader> { UnavailableDeviceCalendarReader() }
    }
