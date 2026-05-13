@file:Suppress("ktlint:standard:filename")

package app.logdate.client

import android.app.Application
import android.content.Context
import android.util.Log
import app.logdate.client.ambient.AmbientPromptScheduler
import app.logdate.client.ambient.AmbientPromptSchedulingObserver
import app.logdate.client.calendar.CalendarImportScheduler
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.events.EventInferenceScheduler
import app.logdate.client.image.DataSaverImageInterceptor
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.rewind.RewindGenerationScheduler
import app.logdate.client.shortcuts.DynamicShortcutRefreshObserver
import app.logdate.client.shortcuts.DynamicShortcutScheduler
import app.logdate.di.initializeKoin
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Application class for the LogDate Android app.
 *
 * For app UI, see [MainActivity].
 */
class LogdateApplication :
    Application(),
    KoinComponent,
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            // Cross-fade every image as it loads. Without this, thumbnails
            // pop in abruptly when the placeholder color is replaced; with
            // it, the gallery and timeline feel smooth on scroll.
            .crossfade(IMAGE_CROSSFADE_MS)
            .components {
                add(VideoFrameDecoder.Factory())
                add(DataSaverImageInterceptor(get<DataUsagePolicy>()))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve(IMAGE_CACHE_DIR))
                    .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                    .build()
            }.build()

    override fun onCreate() {
        super.onCreate()
        Log.i(APP_STARTUP_TAG, "Application onCreate: initializing logging and DI")

        Napier.base(DebugAntilog())
        Napier.base(CrashlyticsAntilog())
        initializeKoin()
        Log.i(APP_STARTUP_TAG, "Application onCreate: Koin initialized")
        runCatching {
            LogDateNotificationRegistrar(this).registerAllPhoneChannels()
        }.onFailure { error ->
            Napier.w("Failed to register notification channels on app startup", error)
        }
        runCatching {
            get<AmbientPromptSchedulingObserver>().start()
            get<AmbientPromptScheduler>().enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
        }.onFailure { error ->
            Napier.w("Failed to initialize ambient prompt scheduling", error)
        }
        runCatching {
            get<LocationTrackingManager>().startTracking()
        }.onFailure { error ->
            Napier.w("Failed to start location tracking manager on app startup", error)
        }
        runCatching {
            val rewindScheduler = RewindGenerationScheduler(this)
            rewindScheduler.schedulePeriodicGeneration()
            rewindScheduler.scheduleAnnualGeneration()
            rewindScheduler.enqueueImmediateCheck()
        }.onFailure { error ->
            Napier.w("Failed to initialize rewind generation scheduling", error)
        }
        runCatching {
            EventInferenceScheduler(this).schedulePeriodicInference()
        }.onFailure { error ->
            Napier.w("Failed to initialize event inference scheduling", error)
        }
        runCatching {
            CalendarImportScheduler(this).schedulePeriodicImport()
        }.onFailure { error ->
            Napier.w("Failed to initialize calendar import scheduling", error)
        }
        runCatching {
            val shortcutScheduler = get<DynamicShortcutScheduler>()
            shortcutScheduler.schedulePeriodicRefresh()
            shortcutScheduler.enqueueImmediateRefresh()
            get<DynamicShortcutRefreshObserver>().start()
        }.onFailure { error ->
            Napier.w("Failed to initialize dynamic shortcut scheduling", error)
        }
    }
}

private const val APP_STARTUP_TAG = "LogDateStartup"
private const val IMAGE_CACHE_DIR = "image_cache"
private const val IMAGE_CACHE_MAX_BYTES = 250L * 1024 * 1024 // 250 MB
private const val IMAGE_CROSSFADE_MS = 200
