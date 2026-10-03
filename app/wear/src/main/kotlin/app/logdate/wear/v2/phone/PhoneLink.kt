package app.logdate.wear.v2.phone

import androidx.wear.phone.interactions.PhoneTypeHelper
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Where the watch stands with the phone it is paired with. */
sealed interface PhoneLink {
    /** A phone running LogDate is in reach. */
    data class Connected(
        val nodeId: String,
        val phoneName: String,
    ) : PhoneLink

    /** No phone running LogDate is in reach, or the watch cannot tell. */
    data object NotConnected : PhoneLink

    /** The connected phone runs Android but not LogDate. */
    data class AppMissing(
        val nodeId: String,
    ) : PhoneLink

    /** The paired phone is an iPhone, which LogDate does not run on. */
    data object Unsupported : PhoneLink
}

/**
 * Follows [PhoneLink] without polling: Play services announces each change to the phone's
 * [WearDataLayerClient.PHONE_CAPABILITY], and the link is looked up again on each one.
 * [phoneType] is `PhoneTypeHelper.getPhoneDeviceType(context)` outside tests.
 */
class PhoneLinkRepository(
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
    private val phoneType: () -> Int,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observe(): Flow<PhoneLink> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(current()) } }
            val listening = startListening(listener)
            send(current())
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, WearDataLayerClient.PHONE_CAPABILITY)
            }
        }.distinctUntilChanged()

    suspend fun current(): PhoneLink =
        withContext(ioDispatcher) {
            try {
                lookUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't look up the paired phone; reporting it as not connected", e)
                PhoneLink.NotConnected
            }
        }

    private suspend fun lookUp(): PhoneLink {
        val type = phoneType()
        if (type == PhoneTypeHelper.DEVICE_TYPE_IOS) return PhoneLink.Unsupported
        val withLogDate =
            capabilityClient
                .getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        val phone = withLogDate.firstOrNull(Node::isNearby) ?: withLogDate.firstOrNull()
        if (phone != null) return PhoneLink.Connected(nodeId = phone.id, phoneName = phone.displayName)
        val connected = nodeClient.connectedNodes.await()
        val paired = connected.firstOrNull(Node::isNearby) ?: connected.firstOrNull() ?: return PhoneLink.NotConnected
        if (type != PhoneTypeHelper.DEVICE_TYPE_ANDROID) return PhoneLink.NotConnected
        return PhoneLink.AppMissing(nodeId = paired.id)
    }

    private suspend fun startListening(listener: CapabilityClient.OnCapabilityChangedListener): Boolean =
        try {
            capabilityClient.addListener(listener, WearDataLayerClient.PHONE_CAPABILITY).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't follow the phone's LogDate install; the phone link is reported as of now only", e)
            false
        }
}
