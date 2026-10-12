package app.logdate.di

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.reminders.CoordinatedReminderClaims
import app.logdate.client.domain.reminders.DeviceReminderBudget
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import app.logdate.client.domain.reminders.PhoneReminderPreflight
import app.logdate.client.domain.reminders.PhoneReminderSnapshotSource
import app.logdate.client.domain.reminders.ReminderClaimResponder
import app.logdate.client.domain.reminders.ReminderDayClaims
import app.logdate.client.domain.reminders.ReminderPostedHistory
import app.logdate.client.domain.reminders.WireReminderClaimPeer
import app.logdate.client.domain.reminders.WireReminderDeliveryPeer
import app.logdate.client.domain.reminders.WireWatchPresence
import app.logdate.client.domain.reminders.reminderTrigger
import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.reminders.PhoneMomentReminderNotifier
import app.logdate.client.reminders.PhoneReminderEnvironmentReader
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.TimeZone
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val phoneReminderClaimsModule =
    module {
        includes(phoneReminderSignalsModule)
        includes(phoneReminderSchedulingModule)
        single { ReminderPostedHistory(get()) }
        single { DeviceReminderBudget(get()) }
        single { PhoneMomentReminderNotifier(androidContext()) }
        single {
            val messages = Wearable.getMessageClient(androidContext())
            val peers = get<PhoneDeliveryPeers>()
            WireWatchPresence(
                exchange = { node, path, request -> messages.sendRequest(node, path, request).await() },
                isCurrent = { peers.isCurrent(it) },
            )
        }
        single { ReminderDayClaims(get(), PeerRole.PHONE) }
        single {
            val messages = Wearable.getMessageClient(androidContext())
            WireReminderClaimPeer(PeerRole.PHONE) { node, path, request -> messages.sendRequest(node, path, request).await() }
        }
        single {
            val flags = get<FeatureFlagStore>()
            val peers = get<PhoneDeliveryPeers>()
            val preferences = get<LogdatePreferencesDataSource>()
            val budget = get<DeviceReminderBudget>()
            val history = get<ReminderPostedHistory>()
            CoordinatedReminderClaims(
                claims = get(),
                role = PeerRole.PHONE,
                peer = get<WireReminderClaimPeer>(),
                isCurrent = { peers.isCurrent(it) },
                enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                available = { day ->
                    budget.available(day) &&
                        history.latest()?.toStreakDay(
                            TimeZone.currentSystemDefault(),
                            preferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR,
                        ) != day
                },
                dayStartHour = { preferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR },
            )
        }
        factory { parameters ->
            val sourceOverride = parameters.getOrNull<PhoneReminderSnapshotSource>()
            val source = sourceOverride ?: get<PhoneReminderSnapshotSource>()
            val environment = get<PhoneReminderEnvironmentReader>()
            val preflight = PhoneReminderPreflight({ environment.read() }, get(), get())
            val ambientHistory = get<AmbientPromptHistoryRepository>()
            val peers = get<PhoneDeliveryPeers>()
            val notifier = get<PhoneMomentReminderNotifier>()
            PhoneReminderDelivery(
                budget = get(),
                history = get(),
                claims = get(),
                presence = get(),
                watchDelivery = get(),
                snapshot = source,
                isCurrent = { peers.isCurrent(it) },
                postPhone = { trigger, marks, day -> notifier.post(trigger, marks, day) },
                postSupplemental = { candidate, marks, day ->
                    candidate.reminderTrigger()?.let { notifier.post(it, marks, day, candidate) } ?: false
                },
                supplementalPosted = { ambientHistory.recordShown(it) },
                preflight = { if (sourceOverride == null) preflight.check() else null },
            )
        }
        single { ReminderClaimResponder(get(), PeerRole.PHONE) }
        single {
            val messages = Wearable.getMessageClient(androidContext())
            WireReminderDeliveryPeer { node, path, request -> messages.sendRequest(node, path, request).await() }
        }
    }
