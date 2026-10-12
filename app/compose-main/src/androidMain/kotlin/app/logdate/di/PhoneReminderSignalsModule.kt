package app.logdate.di

import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.location.ObserveLocationStopsUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.reminders.CampfireFadeDetector
import app.logdate.client.domain.reminders.LivePhoneReminderSnapshotSource
import app.logdate.client.domain.reminders.NovelPlaceArrivalDetector
import app.logdate.client.domain.reminders.PhoneReminderSnapshotSource
import app.logdate.client.domain.reminders.SupplementalReminderSource
import app.logdate.client.domain.reminders.WindDownTimeResolver
import app.logdate.client.health.AndroidSleepRepository
import app.logdate.client.health.SleepRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.reminders.PhoneReminderEnvironmentReader
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.moments.MarkedMomentRepository
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val phoneReminderSignalsModule =
    module {
        single<SleepRepository> { AndroidSleepRepository(androidContext()) }
        factory { WindDownTimeResolver(get()) }
        factory { CampfireFadeDetector(get(), get()) }
        single {
            val stops = get<ObserveLocationStopsUseCase>()
            val places = get<PlaceResolutionCache>()
            val device = get<DeviceIdProvider>()
            NovelPlaceArrivalDetector(get(), get(), locations = {
                stops.currentDevice(device.getDeviceId().value.toString()).first()
            }, resolve = { places.resolve(it) })
        }
        factory {
            val nodes = Wearable.getNodeClient(androidContext())
            val permissions = get<PermissionManager>()
            PhoneReminderEnvironmentReader(
                sessions = get(),
                config = get(),
                preferences = get(),
                shared = get(),
                flags = get(),
                peers = get(),
                states = get(),
                connectedNodes = { withTimeoutOrNull(2_000) { nodes.connectedNodes.await().mapTo(HashSet()) { it.id } }.orEmpty() },
                locationAllowed = { permissions.isPermissionGranted(PermissionType.LOCATION) },
            )
        }
        factory {
            val drafts = get<FetchMostRecentDraftUseCase>()
            val notes = get<JournalNotesRepository>()
            val recall = get<GetMemoryRecallUseCase>()
            val settings = get<MemoriesSettingsRepository>()
            SupplementalReminderSource(
                latestDraft = { drafts().first() },
                savedSince = { since -> notes.observeEntryTimestamps().first().any { it >= since } },
                recall = {
                    val preferences = settings.getSettings()
                    recall(preferences.aiRecallEnabled, preferences.recallMode, preferences.widgetContentTypes).first()
                },
                history = get(),
            )
        }
        factory<PhoneReminderSnapshotSource> {
            val environment = get<PhoneReminderEnvironmentReader>()
            val marks = get<MarkedMomentRepository>()
            val supplemental = get<SupplementalReminderSource>()
            LivePhoneReminderSnapshotSource(
                environment = { environment.read() },
                windDown = get(),
                journal = get(),
                arrivals = get(),
                openMarks = { marks.observeOpenMarks().first().size },
                supplemental = { supplemental.read(it) },
            )
        }
    }
