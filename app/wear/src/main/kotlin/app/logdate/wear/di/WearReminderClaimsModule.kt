package app.logdate.wear.di

import android.content.Context
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.CoordinatedReminderClaims
import app.logdate.client.domain.reminders.ReminderClaimResponder
import app.logdate.client.domain.reminders.ReminderDayClaims
import app.logdate.client.domain.reminders.WireReminderClaimPeer
import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.TimeZone
import org.koin.dsl.module

val wearReminderClaimsModule =
    module {
        single { ReminderDayClaims(get(), PeerRole.WATCH) }
        single {
            val messages = Wearable.getMessageClient(get<Context>())
            WireReminderClaimPeer(PeerRole.WATCH) { node, path, request -> messages.sendRequest(node, path, request).await() }
        }
        single {
            val flags = get<FeatureFlagStore>()
            val peers = get<WatchDeliveryPeer>()
            val states = get<PeerStateStore>()

            suspend fun phoneState() = states.documents(PeerRole.PHONE).first()[peers.current()?.phoneNode]?.phone
            CoordinatedReminderClaims(
                claims = get(),
                role = PeerRole.WATCH,
                peer = get<WireReminderClaimPeer>(),
                isCurrent = { peers.isCurrent(it) },
                enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                dayStartHour = { phoneState()?.dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR },
                timeZone = { phoneState()?.zone?.let(TimeZone::of) ?: TimeZone.currentSystemDefault() },
            )
        }
        single { ReminderClaimResponder(get(), PeerRole.WATCH) }
    }
