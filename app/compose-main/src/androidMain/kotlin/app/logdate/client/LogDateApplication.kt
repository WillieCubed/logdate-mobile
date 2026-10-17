@file:Suppress("ktlint:standard:filename")

package app.logdate.client

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import app.logdate.client.activity.PhoneActivityAwareness
import app.logdate.client.ambient.AmbientPromptScheduler
import app.logdate.client.ambient.AmbientPromptSchedulingObserver
import app.logdate.client.bubble.RecordingBubbleNotifier
import app.logdate.client.calendar.CalendarImportScheduler
import app.logdate.client.data.notes.NativeMediaQuarantine
import app.logdate.client.data.notes.StoredMediaReferenceMigrationLauncher
import app.logdate.client.data.search.AndroidPlatformSearchIndexManager
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.reminders.ReminderSignalObserver
import app.logdate.client.events.EventInferenceScheduler
import app.logdate.client.feature.widgets.publishWidgetPreviews
import app.logdate.client.image.DataSaverImageInterceptor
import app.logdate.client.launch.ApplicationStartup
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.notifications.LiveUpdatesSwitch
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.notifications.RecordingBubbleSwitch
import app.logdate.client.platform.PlatformComponentToggler
import app.logdate.client.rewind.RewindGenerationScheduler
import app.logdate.client.shortcuts.DynamicShortcutRefreshObserver
import app.logdate.client.shortcuts.DynamicShortcutScheduler
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.v2.PhoneStatePublisher
import app.logdate.client.sync.v2.link.PhoneLinkInitiator
import app.logdate.client.sync.v2.receipts.WatchReceiptNotifier
import app.logdate.client.sync.v2.window.WatchWindowPublisher
import app.logdate.client.tiles.RecordVoiceTileUpdater
import app.logdate.client.tiles.RecordingWindowTracker
import app.logdate.client.watch.presence.WatchPresenceRegistration
import app.logdate.di.initializeKoin
import app.logdate.ui.media.addLocalMedia
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val startup =
        ApplicationStartup(startupScope) {
            try {
                get<LogDateDatabase>()
                quarantineRestoredMedia()
                get<AndroidPlatformSearchIndexManager>()
                startBackgroundWork()
                Napier.i("Encrypted storage and startup observers ready", tag = APP_STARTUP_TAG)
            } catch (error: Exception) {
                Napier.e("Application storage initialization failed", error, tag = APP_STARTUP_TAG)
                throw error
            }
        }

    private suspend fun quarantineRestoredMedia() {
        val detector = PostRestoreDetector(this)
        val delivery = detector.pendingMediaQuarantineId() ?: return
        get<NativeMediaQuarantine>().run { detector.pendingMediaQuarantineId() == delivery }
        check(detector.acknowledgeMediaQuarantine(delivery)) { "Native media delivery changed" }
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            // Cross-fade every image as it loads. Without this, thumbnails
            // pop in abruptly when the placeholder color is replaced; with
            // it, the gallery and timeline feel smooth on scroll.
            .crossfade(IMAGE_CROSSFADE_MS)
            .components {
                // Stored media strings name files by LogDate reference or by a path that may come
                // from another install; resolve them before Coil's own file handling sees them.
                addLocalMedia(get())
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
        Napier.i("Application onCreate: initializing logging and DI", tag = APP_STARTUP_TAG)

        installStrictModeIfDebuggable()

        // Napier's DebugAntilog derives its tag by walking the stack trace at a fixed depth. R8
        // inlines frames away, so in a minified build that index runs off the end and every
        // Napier call throws ArrayIndexOutOfBoundsException - crashing the app from inside its
        // own error logging. LogcatAntilog does no stack introspection, so it is safe minified,
        // and it keeps warnings and errors in release builds: without them a failing release
        // install produces no on-device output at all.
        Napier.base(LogcatAntilog(isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0))
        Napier.base(CrashlyticsAntilog())
        initializeKoin()
        Napier.i("Application onCreate: Koin initialized", tag = APP_STARTUP_TAG)
        startup.start()
    }

    /**
     * Starts the work that runs alongside the app: notification channels, migrations, and the
     * schedulers. Each part is isolated so one failing to start doesn't stop the others.
     */
    private fun startBackgroundWork() {
        startRestoreCredentialCleanup()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { get<IdentityKeyManager>().ensureExistingIdentityBackup() }
                .onFailure { Napier.w("Native encryption recovery material needs attention", it) }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            publishWidgetPreviews(this@LogdateApplication)
        }
        isolated("Failed to register notification channels on app startup") {
            LogDateNotificationRegistrar(this).registerAllPhoneChannels()
        }
        isolated("Failed to start the media reference migration") {
            get<StoredMediaReferenceMigrationLauncher>().start()
        }
        isolated("Failed to initialize ambient prompt scheduling") {
            get<AmbientPromptSchedulingObserver>().start()
            get<AmbientPromptScheduler>().enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
        }
        isolated("Failed to observe reminder signals") {
            get<ReminderSignalObserver>().start()
        }
        isolated("Failed to start location tracking manager on app startup") {
            get<LocationTrackingManager>().startTracking()
        }
        isolated("Failed to initialize rewind generation scheduling") {
            val rewindScheduler = RewindGenerationScheduler(this)
            rewindScheduler.schedulePeriodicGeneration()
            rewindScheduler.disableAnnualGeneration()
            rewindScheduler.enqueueImmediateCheck()
        }
        isolated("Failed to initialize event inference scheduling") {
            val scheduler = EventInferenceScheduler(this)
            if (FeatureFlag.EVENTS.availableForLaunch) scheduler.schedulePeriodicInference() else scheduler.disableForLaunch()
        }
        isolated("Failed to initialize calendar import scheduling") {
            val scheduler = CalendarImportScheduler(this)
            if (FeatureFlag.EVENTS.availableForLaunch) scheduler.schedulePeriodicImport() else scheduler.disableForLaunch()
        }
        isolated("Failed to initialize dynamic shortcut scheduling") {
            val shortcutScheduler = get<DynamicShortcutScheduler>()
            shortcutScheduler.schedulePeriodicRefresh()
            shortcutScheduler.enqueueImmediateRefresh()
            get<DynamicShortcutRefreshObserver>().start()
        }
        isolated("Failed to start syncing flag-gated components") {
            get<PlatformComponentToggler>().start(get<CoroutineScope>())
            get<RecordVoiceTileUpdater>().start(get<CoroutineScope>())
            get<RecordingWindowTracker>().start(this, get<CoroutineScope>())
            get<RecordingBubbleNotifier>().start(get<CoroutineScope>())
            // Notification builders read these switches synchronously. Creating them now starts
            // their flag reads, so the first recording after launch already sees the flags.
            get<LiveUpdatesSwitch>()
            get<RecordingBubbleSwitch>()
        }
        startPhoneStatePublishing()
        startActivityAwareness()
        startWatchLinking()
        startWatchReceipts()
        runCatching { get<WatchWindowPublisher>().start(get()) }
            .onFailure { Napier.w("Failed to start publishing watch libraries", it) }
    }

    /** Tells watches when their notes are backed up; a failure here must not stop startup. */
    private fun startWatchReceipts() {
        runCatching {
            get<WatchReceiptNotifier>().start(get(), get<SyncMetadataService>().observePendingCount())
        }.onFailure { error ->
            Napier.w("Failed to start telling watches which notes are backed up", error)
        }
    }

    /** Links this phone's watches while the redesigned watch app is on; a failure here must not stop startup. */
    private fun startWatchLinking() {
        runCatching {
            get<PhoneLinkInitiator>().start(get())
        }.onFailure { error ->
            Napier.w("Failed to start linking this phone's watches", error)
        }
    }

    /** Follows the phone's activity while activity awareness is on; a failure here must not stop startup. */
    private fun startActivityAwareness() {
        runCatching {
            get<PhoneActivityAwareness>().start(get())
        }.onFailure { error ->
            Napier.w("Failed to start following the phone's activity", error)
        }
    }

    /** Publishes this phone's Labs watch switch to its watches; a failure here must not stop startup. */
    private fun startPhoneStatePublishing() {
        runCatching {
            get<PhoneStatePublisher>().start(get())
            get<WatchPresenceRegistration>().start(get())
        }.onFailure { error ->
            Napier.w("Failed to start publishing this phone's state to its watches", error)
        }
    }

    /** Runs one part of startup, logging [failure] instead of letting it stop the parts after it. */
    private fun startRestoreCredentialCleanup() =
        isolated("Failed to start restore credential cleanup reconciliation") {
            get<app.logdate.client.data.account.RestoreCredentialLifecycle>().start(get<CoroutineScope>())
        }

    private inline fun isolated(
        failure: String,
        start: () -> Unit,
    ) {
        runCatching(start).onFailure { error -> Napier.w(failure, error) }
    }

    /**
     * Surfaces main-thread disk reads / writes, network, slow custom calls, and leaked closables
     * in debug builds. Violations are logged but don't crash — too aggressive for normal dev
     * iteration. The whole block is no-op in release.
     */
    private fun installStrictModeIfDebuggable() {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return
        runCatching {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build(),
            )
        }.onFailure { error ->
            Napier.w("Failed to install StrictMode policies", error)
        }
    }
}

private const val APP_STARTUP_TAG = "LogDateStartup"
private const val IMAGE_CACHE_DIR = "image_cache"
private const val IMAGE_CACHE_MAX_BYTES = 250L * 1024 * 1024 // 250 MB
private const val IMAGE_CROSSFADE_MS = 200
