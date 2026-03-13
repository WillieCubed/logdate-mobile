package app.logdate.client.location.di

import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.location.tracking.OptimizedBackgroundLocationRegistrar
import app.logdate.client.location.tracking.ScheduledLocationTrackerWorker
import app.logdate.client.location.tracking.ScheduledLocationTrackingService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Koin module for scheduled location tracking services.
 */
val scheduledLocationModule =
    module {
        single<CoroutineDispatcher>(named("io-dispatcher")) { Dispatchers.IO }
        single<Clock> { Clock.System }
        workerOf(::ScheduledLocationTrackerWorker)
        single { ScheduledLocationTrackingService(androidContext()) }
        single { OptimizedBackgroundLocationRegistrar(androidContext(), get()) }
        single { LocationTrackingManager(androidContext(), get(), get(), get(), get()) }
    }
