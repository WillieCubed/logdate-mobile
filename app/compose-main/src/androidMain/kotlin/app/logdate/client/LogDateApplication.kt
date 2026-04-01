@file:Suppress("ktlint:standard:filename")

package app.logdate.client

import android.app.Application
import android.content.Context
import android.util.Log
import app.logdate.client.image.DataSaverImageInterceptor
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.di.initializeKoin
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
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
        initializeKoin()
        Log.i(APP_STARTUP_TAG, "Application onCreate: Koin initialized")
        runCatching {
            LogDateNotificationRegistrar(this).registerAllPhoneChannels()
        }.onFailure { error ->
            Napier.w("Failed to register notification channels on app startup", error)
        }
        runCatching {
            get<LocationTrackingManager>().startTracking()
        }.onFailure { error ->
            Napier.w("Failed to start location tracking manager on app startup", error)
        }
    }
}

private const val APP_STARTUP_TAG = "LogDateStartup"
private const val IMAGE_CACHE_DIR = "image_cache"
private const val IMAGE_CACHE_MAX_BYTES = 250L * 1024 * 1024 // 250 MB
