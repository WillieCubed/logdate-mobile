package app.logdate.wear.di

import android.content.Context
import android.hardware.SensorManager
import androidx.health.services.client.HealthServices
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.work.WorkManager
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.createAndroidPlatformHapticsController
import app.logdate.ui.platform.isSystemHapticFeedbackOff
import app.logdate.wear.LogDateWearApplication
import app.logdate.wear.capture.QuickNoteWriter
import app.logdate.wear.health.HeartRateSampler
import app.logdate.wear.health.MeasureClientHeartRateReadings
import app.logdate.wear.health.MeasuredHeartRateSampler
import app.logdate.wear.launch.FeatureSwitch
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.playback.WatchMediaCache
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.reminders.ActivityRegistration
import app.logdate.wear.reminders.ActivityTransitionStore
import app.logdate.wear.reminders.AndroidReminderPermissions
import app.logdate.wear.reminders.EveningReminderScheduler
import app.logdate.wear.reminders.EveningReminderWork
import app.logdate.wear.reminders.HealthServicesActivityRegistration
import app.logdate.wear.reminders.NudgeNotifier
import app.logdate.wear.reminders.ReminderPermissions
import app.logdate.wear.reminders.SensorManagerOffBodyReadings
import app.logdate.wear.reminders.SharedPreferencesActivityTransitionStore
import app.logdate.wear.reminders.SharedPreferencesWatchReminderPreferences
import app.logdate.wear.reminders.SharedWatchReminderSettings
import app.logdate.wear.reminders.WatchNudgeNotifier
import app.logdate.wear.reminders.WatchReminderDispatcher
import app.logdate.wear.reminders.WatchReminderPreferences
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.reminders.WearPresenceResponder
import app.logdate.wear.reminders.WearPresenceSampler
import app.logdate.wear.reminders.WorkManagerEveningReminderWork
import app.logdate.wear.reminders.WorkoutWatcher
import app.logdate.wear.sync.deleteLocalAudio
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.window.WindowProtocol
import app.logdate.wear.v2.archive.ArchiveViewModel
import app.logdate.wear.v2.capture.CaptureSheetViewModel
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.haptics.SharedPreferencesHapticsPreferenceStore
import app.logdate.wear.v2.haptics.watchHaptics
import app.logdate.wear.v2.mark.BackgroundMarker
import app.logdate.wear.v2.mark.MarkMomentUseCase
import app.logdate.wear.v2.mark.MarkViewModel
import app.logdate.wear.v2.mark.MarkedMomentStore
import app.logdate.wear.v2.memories.MemoriesViewModel
import app.logdate.wear.v2.memories.OnThisDayFinder
import app.logdate.wear.v2.memories.WATCH_RECALL_TYPES
import app.logdate.wear.v2.memories.WeeklyRewindSource
import app.logdate.wear.v2.moments.MomentViewModel
import app.logdate.wear.v2.permissions.AndroidPermissionChecker
import app.logdate.wear.v2.permissions.PermissionChecker
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.SharedPreferencesPermissionPromptStore
import app.logdate.wear.v2.permissions.WatchPermission
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.playback.AndroidMomentPlaybackEngine
import app.logdate.wear.v2.playback.MomentPlaybackEngine
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.PlaybackPreferencesStore
import app.logdate.wear.v2.playback.SharedPreferencesPlaybackPreferencesStore
import app.logdate.wear.v2.playon.OutputPicker
import app.logdate.wear.v2.playon.SystemOutputPicker
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.settings.WatchSettingsViewModel
import app.logdate.wear.v2.surfaces.AndroidSurfaceRefresher
import app.logdate.wear.v2.surfaces.PackageManagerComponentSwitch
import app.logdate.wear.v2.surfaces.SurfaceComponents
import app.logdate.wear.v2.surfaces.SurfaceCoordinator
import app.logdate.wear.v2.surfaces.SurfaceUpdater
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayViewModel
import app.logdate.wear.v2.today.WatchDayBoundaryPreferences
import app.logdate.wear.v2.today.WatchStreakSettings
import app.logdate.wear.v2.today.timeChangeSignals
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.uuid.Uuid

/** Koin module for the redesigned watch app. Only the new app's screens resolve anything from it. */
val wearV2Module =
    module {
        includes(wearReminderClaimsModule)
        single<HapticsPreferenceStore> {
            SharedPreferencesHapticsPreferenceStore(
                get<Context>().getSharedPreferences(SharedPreferencesHapticsPreferenceStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<LogDateHaptics> {
            val context = get<Context>()
            watchHaptics(
                controller = createAndroidPlatformHapticsController(context),
                preferences = get(),
                systemHapticsOff = { isSystemHapticFeedbackOff(context) },
            )
        }

        single<PermissionChecker> { AndroidPermissionChecker(get()) }
        single<PermissionPromptStore> {
            SharedPreferencesPermissionPromptStore(
                get<Context>().getSharedPreferences(SharedPreferencesPermissionPromptStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single { PermissionPrompts(checker = get(), store = get()) }

        single { QuickNoteWriter(notesRepository = get(), locationCaptureCoordinator = get()) }
        single {
            MarkedMomentStore(
                markedMomentDao = get(),
                peerDeliveryDao = get(),
                syncMetadataService = get(),
                transactions = get<SyncTransactionManager>(WatchCloud),
            )
        }
        single<HeartRateSampler> {
            val checker = get<PermissionChecker>()
            try {
                MeasuredHeartRateSampler(
                    readings = MeasureClientHeartRateReadings(HealthServices.getClient(get<Context>()).measureClient),
                    hasPermission = { checker.isGranted(WatchPermission.HEART_RATE) },
                )
            } catch (e: Exception) {
                Napier.w("Health Services is not available; marks have no heart rate on this watch", e)
                HeartRateSampler.None
            }
        }
        single {
            val location = get<WearLocationCaptureCoordinator>()
            val devices = get<DeviceIdProvider>()
            MarkMomentUseCase(
                store = get(),
                deviceId = { devices.getDeviceId().value.toString() },
                place = { location.captureForJournalEntry() },
                heartRate = get(),
            )
        }
        single { BackgroundMarker(featureFlags = get(), markMoment = get(), haptics = get()) }
        viewModel { MarkViewModel(markMoment = get(), haptics = get(), permissions = get(), applicationScope = get()) }
        viewModel {
            val notes = get<QuickNoteWriter>()
            CaptureSheetViewModel(textSaver = notes, feelingSaver = notes, phoneReachability = get(), haptics = get())
        }
        factory {
            AudioMomentSaver(
                notesRepository = get(),
                durationResolver = get(),
                noteHealthAnnotator = get(),
                locationCaptureCoordinator = get(),
            )
        }

        single { DayClock(timeChanges = timeChangeSignals(get())) }
        // The phone shares its day-start hour through the state document (Track A, C2); until then there is none.
        single {
            ObserveCampfireUseCase(
                notesRepository = get(),
                streakSettingsRepository = WatchStreakSettings(get()),
                dayBoundaryPreferences = WatchDayBoundaryPreferences(phoneDayStartHour = get<WindowProtocol>()::dayStartHour),
            )
        }
        single {
            val context = get<Context>()
            PhoneLinkRepository(
                capabilityClient = Wearable.getCapabilityClient(context),
                nodeClient = Wearable.getNodeClient(context),
                phoneType = { PhoneTypeHelper.getPhoneDeviceType(context) },
                ioDispatcher = Dispatchers.IO,
            )
        }
        single {
            val context = get<Context>()
            PhoneHandoff(
                remoteActivityHelper = RemoteActivityHelper(context),
                packageName = context.packageName,
                ioDispatcher = Dispatchers.IO,
            )
        }
        viewModel {
            TodayViewModel(
                dayClock = get(),
                notesRepository = get(),
                observeCampfire = get(),
                phoneLinks = get(),
                phoneHandoff = get(),
            )
        }
        viewModel {
            RecorderViewModel(
                recorder = get(),
                saver = get(),
                notesRepository = get(),
                removalNotifier = get(),
                feelingSaver = get<QuickNoteWriter>(),
                haptics = get(),
                permissions = get(),
                applicationScope = get(),
                mediaFiles = get(),
            )
        }
        viewModel { FlowViewModel(dayClock = get(), notesRepository = get()) }
        viewModel { ArchiveViewModel(dayClock = get(), notesRepository = get()) }

        single { GetMemoryRecallUseCase(notesRepository = get()) }
        single {
            val recall = get<GetMemoryRecallUseCase>()
            OnThisDayFinder(recall = { recall(contentTypes = WATCH_RECALL_TYPES).first() }, notesRepository = get())
        }
        single { WeeklyRewindSource(rewinds = get(), notesRepository = get()) }
        viewModel { MemoriesViewModel(dayClock = get(), onThisDay = get(), weeklyRewind = get()) }

        single<PlaybackPreferencesStore> {
            SharedPreferencesPlaybackPreferencesStore(
                get<Context>().getSharedPreferences(SharedPreferencesPlaybackPreferencesStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<MomentPlaybackEngine> { AndroidMomentPlaybackEngine(manager = get(), scope = get()) }
        // One player for every screen, on the main thread the media controller needs.
        single {
            MomentPlayer(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                engine = get(),
                outputs = get(),
                resolver = get(),
                preferences = get(),
            )
        }
        viewModel { params ->
            val mediaCache = get<WatchMediaCache>()
            val mediaFiles = get<MediaFileResolver>()
            MomentViewModel(
                noteId = Uuid.parseOrNull(params.get<String>()),
                notesRepository = get(),
                player = get(),
                haptics = get(),
                removalNotifier = get(),
                discardAudio = { audio ->
                    withContext(Dispatchers.IO) {
                        mediaCache.evict(audio.uid)
                        deleteLocalAudio(audio.mediaRef, mediaFiles)
                    }
                },
            )
        }
        single<OutputPicker> { SystemOutputPicker(get()) }
        viewModel {
            WatchSettingsViewModel(
                syncManager = get(),
                phoneReachability = get(),
                haptics = get(),
                locationSettings = get(),
                permissionManager = get(),
                phoneLinks = get(),
                protocolGate = get(),
                phoneHandoff = get(),
                reminders = get(),
                reminderPermissions = get(),
            )
        }
        single<WatchReminderPreferences> {
            SharedPreferencesWatchReminderPreferences(
                get<Context>().getSharedPreferences(SharedPreferencesWatchReminderPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<NudgeNotifier> { WatchNudgeNotifier(get()) }
        single { WearPresenceSampler(SensorManagerOffBodyReadings(get<Context>().getSystemService(SensorManager::class.java))) }
        single {
            val flags = get<FeatureFlagStore>()
            val peers = get<WatchDeliveryPeer>()
            WearPresenceResponder(
                sampler = get(),
                currentPeer = { peers.current() },
                enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
            )
        }
        single { WatchReminderDispatcher(preferences = get(), notifier = get()) }
        single<ReminderPermissions> { AndroidReminderPermissions(get()) }
        single<ActivityRegistration> { HealthServicesActivityRegistration(get()) }
        single<ActivityTransitionStore> {
            SharedPreferencesActivityTransitionStore(
                get<Context>().getSharedPreferences(SharedPreferencesActivityTransitionStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single { (get<Context>().applicationContext as LogDateWearApplication).newWatchApp }
        single {
            val experience = get<FeatureSwitch>()
            val preferences = get<WatchReminderPreferences>()
            val permissions = get<ReminderPermissions>()
            WorkoutWatcher(transitions = get(), dispatcher = get(), experienceEnabled = {
                experience.isOn &&
                    preferences.enabled.value &&
                    permissions.notificationsGranted() &&
                    permissions.activityRecognitionGranted()
            }, awaitExperience = experience::awaitReady)
        }
        single {
            val experience = get<FeatureSwitch>()
            val shared = get<SharedWatchReminderSettings>()
            WatchReminders(
                preferences = get(),
                activity = get(),
                permissions = get(),
                evening = get(),
                experienceEnabled = { experience.isOn },
                awaitExperience = experience::awaitReady,
                writeSharedChoice = shared::persistChoice,
            )
        }
        single<EveningReminderWork> { WorkManagerEveningReminderWork(WorkManager.getInstance(get<Context>())) }
        single { EveningReminderScheduler(work = get()) }

        single { SurfaceUpdater(refresher = AndroidSurfaceRefresher(get()), scope = get()) }
        single { SurfaceComponents(PackageManagerComponentSwitch(get())) }
        single {
            SurfaceCoordinator(
                newAppEnabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                components = get(),
                updater = get(),
                pendingUploads =
                    get<SyncMetadataService>().observePendingUploads().map { uploads ->
                        uploads.map { it.operationId ?: "${it.entityType}:${it.entityId}:${it.operation}" }.toSet()
                    },
                days = get<DayClock>().observe(),
            )
        }
    }
