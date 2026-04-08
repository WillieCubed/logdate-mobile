package app.logdate.client.calendar.di

import org.koin.core.module.Module

/**
 * Koin module providing the platform [app.logdate.client.calendar.DeviceCalendarReader]
 * implementation. Each target supplies its own actual binding — Android wires
 * `AndroidDeviceCalendarReader`, every other platform binds the no-op stub.
 */
expect val calendarSyncModule: Module
