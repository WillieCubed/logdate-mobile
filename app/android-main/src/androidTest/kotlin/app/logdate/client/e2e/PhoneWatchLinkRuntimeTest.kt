package app.logdate.client.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.v2.link.AndroidWatchLinkNotifier
import app.logdate.client.sync.v2.link.CloudLinkBundleSource
import app.logdate.client.sync.v2.link.LinkBundleSource
import app.logdate.client.sync.v2.link.LinkCodeMinter
import app.logdate.client.sync.v2.link.LinkTarget
import app.logdate.client.sync.v2.link.PhoneLinkFailure
import app.logdate.client.sync.v2.link.PhoneLinkInitiator
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class PhoneWatchLinkRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefix = "link-test-${Uuid.random()}"
    private val first = LinkTarget("${prefix}Aa", "First watch")
    private val second = LinkTarget("${prefix}BB", "Second watch")
    private val notifier = AndroidWatchLinkNotifier(context)

    @Before
    fun allowNotifications() {
        LogDateNotificationRegistrar(context).registerAllPhoneChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun dismiss() {
        listOf(first, second).forEach { target ->
            notifications.cancel(AndroidWatchLinkNotifier.notificationId(target))
            notifications.cancel(target.nodeId, AndroidWatchLinkNotifier.notificationId(target))
        }
    }

    @Test
    fun pairing_confirmation_stays_on_the_phone_and_opens_LogDate() {
        notifier.linked(first, LinkMode.PAIR)
        val posted = posted(first)
        assertEquals(LogDateNotificationChannelKey.WATCH_SYNC.id, posted.channelId)
        assertTrue(posted.flags and Notification.FLAG_LOCAL_ONLY != 0)
        assertEquals("LogDate is set up on First watch", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotNull(posted.contentIntent)
    }

    @Test
    fun account_confirmation_explains_independent_backup() {
        notifier.linked(first, LinkMode.CLOUD)
        assertEquals(
            "Your watch now backs up to your LogDate account on its own.",
            posted(first).extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun an_account_conflict_names_the_watch() {
        notifier.needsAttention(first, PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT)
        assertEquals(
            "First watch is set up for another LogDate account",
            posted(first).extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
    }

    @Test
    fun device_limit_explains_where_to_make_room() {
        notifier.needsAttention(first, PhoneLinkFailure.DEVICE_LIMIT_REACHED)
        val posted = posted(first)
        assertEquals("Backup is not set up on First watch", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "This account has the most linked devices it can have. Remove one in Settings, then Devices.",
            posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(posted.flags and Notification.FLAG_LOCAL_ONLY != 0)
    }

    @Test
    fun a_key_conflict_does_not_claim_another_account() {
        notifier.needsAttention(first, PhoneLinkFailure.WATCH_KEY_CONFLICT)
        assertEquals("First watch needs to be linked again", posted(first).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun two_watches_with_colliding_node_hashes_keep_both_confirmations() {
        assertEquals(first.nodeId.hashCode(), second.nodeId.hashCode())
        notifier.linked(first, LinkMode.PAIR)
        notifier.linked(second, LinkMode.PAIR)
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val titles =
                notifications.activeNotifications.map {
                    it.notification.extras
                        .getCharSequence(
                            Notification.EXTRA_TITLE,
                        ).toString()
                }
            if (titles.containsAll(listOf("LogDate is set up on First watch", "LogDate is set up on Second watch"))) return
            SystemClock.sleep(100)
        }
        error("One watch's confirmation replaced the other's")
    }

    @Test
    fun watch_capability_events_resolve_to_the_phone_listener() {
        val intent =
            Intent(
                "com.google.android.gms.wearable.CAPABILITY_CHANGED",
                "wear://watch/${WearProtocol.WATCH_CAPABILITY_V2}".toUri(),
            ).setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        assertEquals(listOf("app.logdate.client.sync.PhoneDataLayerListenerService"), services.map { it.serviceInfo.name })
    }

    @Test
    fun the_real_application_graph_supplies_the_pairing_services() {
        val koin = KoinJavaComponent.getKoin()
        assertNotNull(koin.get<PhoneLinkInitiator>())
        assertNotNull(koin.get<PhoneWatchLinkStore>())
        assertIs<CloudLinkBundleSource>(koin.get<LinkBundleSource>())
        assertNotNull(koin.get<LinkCodeMinter>())
    }

    private fun posted(target: LinkTarget): Notification {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            notifications.activeNotifications
                .firstOrNull {
                    it.id == AndroidWatchLinkNotifier.notificationId(target) && (it.tag == null || it.tag == target.nodeId)
                }?.let { return it.notification }
            SystemClock.sleep(100)
        }
        error("The watch link notification was not posted")
    }
}
