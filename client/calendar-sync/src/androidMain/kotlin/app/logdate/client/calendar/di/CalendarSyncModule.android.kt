package app.logdate.client.calendar.di

import app.logdate.client.calendar.AndroidDeviceCalendarReader
import app.logdate.client.calendar.DeviceCalendarReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val calendarSyncModule: Module =
    module {
        single<DeviceCalendarReader> { AndroidDeviceCalendarReader(androidContext()) }
    }
