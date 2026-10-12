package app.logdate.wear.e2e

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PresenceRequest
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.Presence
import app.logdate.wear.reminders.WearPresenceResponder
import app.logdate.wear.reminders.WearPresenceSampler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class WearPresenceRuntimeTest {
    @Test
    fun installedSamplerHandlesTheEmulatorSensorInventory() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val manager = context.getSystemService(SensorManager::class.java)
            val presence = withTimeout(3_000) { KoinJavaComponent.getKoin().get<WearPresenceSampler>().sample() }
            if (manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) == null) {
                assertEquals(Presence(), presence)
            }
        }

    @Test
    fun installedResponderRefusesAnUnlinkedPhone() =
        runBlocking {
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "foreign-phone", "watch", LinkMode.PAIR)
            val request = WearJson.encodeToBytes(PresenceRequest.serializer(), PresenceRequest(context = peer))
            val responder = KoinJavaComponent.getKoin().get<WearPresenceResponder>()
            assertNull(responder.answer("foreign-phone", request))
        }
}
