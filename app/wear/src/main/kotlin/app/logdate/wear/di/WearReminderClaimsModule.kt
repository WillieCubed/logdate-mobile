package app.logdate.wear.di

import android.content.Context
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.CoordinatedReminderClaims
import app.logdate.client.domain.reminders.DeviceReminderBudget
import app.logdate.client.domain.reminders.ReminderClaimResponder
import app.logdate.client.domain.reminders.ReminderDayClaims
import app.logdate.client.domain.reminders.ReminderDeliveryResponder
import app.logdate.client.domain.reminders.WireReminderClaimPeer
import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.wear.reminders.WatchNudgeNotifier
import app.logdate.wear.reminders.WatchReminderPreferences
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.TimeZone
import org.koin.dsl.module

val wearReminderClaimsModule =
    module {
        single { ReminderDayClaims(get(), PeerRole.WATCH) }
        single { DeviceReminderBudget(get()) }
        single {
            val messages = Wearable.getMessageClient(get<Context>())
            WireReminderClaimPeer(PeerRole.WATCH) { node, path, request -> messages.sendRequest(node, path, request).await() }
        }
        single {
            val flags = get<FeatureFlagStore>()
            val peers = get<WatchDeliveryPeer>()
            val states = get<PeerStateStore>()
            val budget = get<DeviceReminderBudget>()
            val preferences = get<WatchReminderPreferences>()

            suspend fun phoneState() = states.documents(PeerRole.PHONE).first()[peers.current()?.phoneNode]?.phone
            CoordinatedReminderClaims(
                claims = get(),
                role = PeerRole.WATCH,
                peer = get<WireReminderClaimPeer>(),
                isCurrent = { peers.isCurrent(it) },
                enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                available = { day ->
                    val phone = phoneState()
                    budget.available(day) &&
                        preferences.lastReminderAt()?.toStreakDay(
                            phone?.zone?.let(TimeZone::of) ?: TimeZone.currentSystemDefault(),
                            phone?.dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR,
                        ) != day
                },
                dayStartHour = { phoneState()?.dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR },
                timeZone = { phoneState()?.zone?.let(TimeZone::of) ?: TimeZone.currentSystemDefault() },
            )
        }
        single { ReminderClaimResponder(get(), PeerRole.WATCH) }
        single {
            val flags = get<FeatureFlagStore>()
            val preferences = get<WatchReminderPreferences>()
            val notifier = WatchNudgeNotifier(get())
            ReminderDeliveryResponder(
                claims = get(),
                budget = get(),
                enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) && preferences.enabled.value },
                post = { trigger, marks -> notifier.post(trigger, marks) },
                recordPostedAt = { preferences.setLastReminderAt(it) },
            )
        }
    }
