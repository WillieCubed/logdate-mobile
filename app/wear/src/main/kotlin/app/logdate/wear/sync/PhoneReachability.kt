package app.logdate.wear.sync

import com.google.android.gms.wearable.CapabilityClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** The phone running LogDate that the watch can reach right now. */
data class ReachablePhone(
    val displayName: String,
)

/** Follows whether a phone running LogDate is in reach, so status can change without polling. */
fun interface PhoneReachability {
    /** Emits the reachable phone, or null when none is, at once and again whenever that changes. */
    fun observe(): Flow<ReachablePhone?>
}

/**
 * [PhoneReachability] from the Data Layer: Play services announces each change to
 * [WearDataLayerClient.PHONE_CAPABILITY], and the phone is looked up again on each one.
 */
class CapabilityPhoneReachability(
    private val capabilityClient: CapabilityClient,
    private val dataLayerClient: WearDataLayerClient,
) : PhoneReachability {
    override fun observe(): Flow<ReachablePhone?> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(currentPhone()) } }
            val listening = startListening(listener)
            send(currentPhone())
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, WearDataLayerClient.PHONE_CAPABILITY)
            }
        }.distinctUntilChanged()

    private suspend fun startListening(listener: CapabilityClient.OnCapabilityChangedListener): Boolean =
        try {
            capabilityClient.addListener(listener, WearDataLayerClient.PHONE_CAPABILITY).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not follow the phone coming and going; reachability is reported as of now only", e)
            false
        }

    private suspend fun currentPhone(): ReachablePhone? = dataLayerClient.getConnectedPhoneName()?.let(::ReachablePhone)
}
