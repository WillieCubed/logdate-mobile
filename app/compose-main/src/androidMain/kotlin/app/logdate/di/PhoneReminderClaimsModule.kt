package app.logdate.di

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.CoordinatedReminderClaims
import app.logdate.client.domain.reminders.ReminderClaimResponder
import app.logdate.client.domain.reminders.ReminderDayClaims
import app.logdate.client.domain.reminders.ReminderPostedHistory
import app.logdate.client.domain.reminders.WireReminderClaimPeer
import app.logdate.client.domain.reminders.WireReminderDeliveryPeer
import app.logdate.client.domain.reminders.WireWatchPresence
import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.reminders.PhoneMomentReminderNotifier
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val phoneReminderClaimsModule =
    module {
        single { ReminderPostedHistory(get()) }
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
            CoordinatedReminderClaims(
                claims = get(),
                role = PeerRole.PHONE,
                peer = get<WireReminderClaimPeer>(),
                isCurrent = { peers.isCurrent(it) },
                enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                dayStartHour = { preferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR },
            )
        }
        single { ReminderClaimResponder(get(), PeerRole.PHONE) }
        single {
            val messages = Wearable.getMessageClient(androidContext())
            WireReminderDeliveryPeer { node, path, request -> messages.sendRequest(node, path, request).await() }
        }
    }
