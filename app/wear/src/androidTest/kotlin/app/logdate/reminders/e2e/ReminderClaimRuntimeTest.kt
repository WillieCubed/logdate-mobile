package app.logdate.reminders.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.domain.reminders.ReminderClaimRequest
import app.logdate.client.domain.reminders.ReminderClaimResponder
import app.logdate.client.domain.reminders.ReminderClaimResponse
import app.logdate.client.domain.reminders.ReminderDayClaim
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class ReminderClaimRuntimeTest {
    @Test
    fun installedManifestRoutesReminderClaimToListener() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(
                "com.google.android.gms.wearable.REQUEST_RECEIVED",
                Uri.parse("wear://peer/logdate/reminders/claim"),
            ).setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(intent, 0)
        assertEquals(listOf("app.logdate.wear.sync.WearDataLayerListenerService"), services.map { it.serviceInfo.name })
    }

    @Test
    fun installedResponderRefusesAnUnlinkedExchange() =
        runBlocking {
            val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
            val claim = ReminderDayClaim(context, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.PHONE)
            val request = WearJson.encodeToBytes(ReminderClaimRequest.serializer(), ReminderClaimRequest(claim = claim))
            val bytes = assertNotNull(KoinJavaComponent.getKoin().get<ReminderClaimResponder>().answer("phone", request))
            val response =
                assertIs<Decoded.Supported<ReminderClaimResponse>>(
                    WearJson.decode(bytes, 1, ReminderClaimResponse.serializer()),
                ).value
            assertEquals(claim, response.claim)
            assertFalse(response.granted)
        }
}
