package app.logdate.wear.di

import android.content.Context
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.createAndroidPlatformHapticsController
import app.logdate.ui.platform.isSystemHapticFeedbackOff
import app.logdate.wear.capture.QuickNoteWriter
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.v2.flow.FlowViewModel
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.haptics.SharedPreferencesHapticsPreferenceStore
import app.logdate.wear.v2.haptics.watchHaptics
import app.logdate.wear.v2.mark.MarkedMomentStore
import app.logdate.wear.v2.permissions.AndroidPermissionChecker
import app.logdate.wear.v2.permissions.PermissionChecker
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.SharedPreferencesPermissionPromptStore
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.recorder.RecorderViewModel
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayViewModel
import app.logdate.wear.v2.today.WatchDayBoundaryPreferences
import app.logdate.wear.v2.today.WatchStreakSettings
import app.logdate.wear.v2.today.timeChangeSignals
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the redesigned watch app. Only the new app's screens resolve anything from it. */
val wearV2Module =
    module {
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
        single { MarkedMomentStore(markedMomentDao = get(), peerDeliveryDao = get(), syncMetadataService = get()) }
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
                dayBoundaryPreferences = WatchDayBoundaryPreferences(phoneDayStartHour = { null }),
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
    }
