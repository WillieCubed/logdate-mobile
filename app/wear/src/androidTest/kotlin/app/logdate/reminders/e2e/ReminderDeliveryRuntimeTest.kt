package app.logdate.reminders.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.reminders.CoordinatedReminderClaims
import app.logdate.client.domain.reminders.DeviceReminderBudget
import app.logdate.client.domain.reminders.ReminderClaimPeer
import app.logdate.client.domain.reminders.ReminderDayClaim
import app.logdate.client.domain.reminders.ReminderDayClaims
import app.logdate.client.domain.reminders.ReminderDeliveryRequest
import app.logdate.client.domain.reminders.ReminderDeliveryResponder
import app.logdate.client.domain.reminders.ReminderDeliveryResponse
import app.logdate.client.domain.reminders.ReminderDeliveryStatus
import app.logdate.client.domain.reminders.ReminderTrigger
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.wear.reminders.SharedPreferencesWatchReminderPreferences
import app.logdate.wear.reminders.WatchNudgeNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryRuntimeTest {
    @Test
    fun delegatedWireRequestPostsOnceUsingNativeClaimsAndPreferences() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            val fixture = "delivery-test-${Uuid.random()}"
            val base = KoinJavaComponent.getKoin().get<KeyValueStorage>()
            val written = mutableSetOf<String>()
            val storage =
                object : KeyValueStorage by base {
                    override suspend fun getString(key: String) = base.getString("$fixture:$key")

                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        written += "$fixture:$key"
                        base.putString("$fixture:$key", value)
                    }
                }
            val preferences = SharedPreferencesWatchReminderPreferences(context.getSharedPreferences(fixture, Context.MODE_PRIVATE))
            val manager = context.getSystemService(NotificationManager::class.java)
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "fixture-phone", "fixture-watch", LinkMode.PAIR)
            val clock =
                object : Clock {
                    override fun now() = Instant.parse("2026-10-12T12:00:00Z")
                }
            val claim = ReminderDayClaim(peer, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.PHONE)

            fun coordinator() =
                CoordinatedReminderClaims(
                    ReminderDayClaims(storage, PeerRole.WATCH),
                    PeerRole.WATCH,
                    ReminderClaimPeer { true },
                    isCurrent = { it == peer },
                    enabled = { true },
                    clock = clock,
                    timeZone = { TimeZone.UTC },
                )

            fun responder() =
                ReminderDeliveryResponder(
                    coordinator(),
                    DeviceReminderBudget(storage),
                    { preferences.enabled.value },
                    post = { trigger, marks -> WatchNudgeNotifier(context).post(trigger, marks) },
                    recordPostedAt = { preferences.setLastReminderAt(it) },
                    clock = clock,
                )
            val request =
                WearJson.encodeToBytes(
                    ReminderDeliveryRequest.serializer(),
                    ReminderDeliveryRequest(
                        claim = claim,
                        trigger = ReminderTrigger.CAMPFIRE_EMBERS,
                        openMarks = 3,
                    ),
                )

            suspend fun status() =
                assertIs<Decoded.Supported<ReminderDeliveryResponse>>(
                    WearJson.decode(requireNotNull(responder().answer("fixture-phone", request)), 1, ReminderDeliveryResponse.serializer()),
                ).value.status
            try {
                preferences.setEnabled(true)
                assertTrue(coordinator().grant(claim))
                assertEquals(ReminderDeliveryStatus.POSTED, status())
                assertEquals(ReminderDeliveryStatus.ALREADY_HANDLED, status())
                assertEquals(
                    clock.now().toEpochMilliseconds(),
                    SharedPreferencesWatchReminderPreferences(
                        context.getSharedPreferences(fixture, Context.MODE_PRIVATE),
                    ).lastReminderAt()?.toEpochMilliseconds(),
                )
                withTimeout(5_000) {
                    while (manager.activeNotifications.none { it.tag == WatchNudgeNotifier.TAG }) delay(25)
                }
                val notification = manager.activeNotifications.single { it.tag == WatchNudgeNotifier.TAG }.notification
                assertEquals(
                    "Keep your campfire going",
                    notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString(),
                )
                assertTrue(
                    notification.extras
                        .getCharSequence(android.app.Notification.EXTRA_TEXT)
                        .toString()
                        .contains("3 marked moments to finish"),
                )
            } finally {
                manager.cancel(WatchNudgeNotifier.TAG, WatchNudgeNotifier.NOTIFICATION_ID)
                written.forEach { base.remove(it) }
                context.deleteSharedPreferences(fixture)
            }
        }

    @Test
    fun installedManifestRoutesDelegatedReminderToListener() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent("com.google.android.gms.wearable.REQUEST_RECEIVED", Uri.parse("wear://peer/logdate/reminders/deliver"))
                .setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(intent, 0)
        assertEquals(listOf("app.logdate.wear.sync.WearDataLayerListenerService"), services.map { it.serviceInfo.name })
    }

    @Test
    fun installedResponderRefusesUnlinkedPhone() =
        runBlocking {
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
            val claim = ReminderDayClaim(peer, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.PHONE)
            val request =
                WearJson.encodeToBytes(
                    ReminderDeliveryRequest.serializer(),
                    ReminderDeliveryRequest(
                        claim = claim,
                        trigger = ReminderTrigger.WORKOUT_ENDED,
                    ),
                )
            assertNull(KoinJavaComponent.getKoin().get<ReminderDeliveryResponder>().answer("phone", request))
        }
}
