package app.logdate.client.watch.presence

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.os.Build
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WatchPresenceRegistration(
    context: Context,
    private val enabled: Flow<Boolean>,
) {
    private val supported = context.packageManager.hasSystemFeature("android.software.companion_device_setup")
    private val manager = context.getSystemService(CompanionDeviceManager::class.java)

    fun start(scope: CoroutineScope) {
        scope.launch { enabled.distinctUntilChanged().collect { apply(it) } }
    }

    suspend fun registerCurrent() = apply(enabled.first())

    private fun apply(active: Boolean) {
        if (Build.VERSION.SDK_INT < 33 || !supported) return
        try {
            manager?.myAssociations?.filter { it.deviceProfile == AssociationRequest.DEVICE_PROFILE_WATCH }?.forEach {
                register(it, active)
            }
        } catch (e: Exception) {
            Napier.w("Could not inspect watch presence associations", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun register(
        association: AssociationInfo,
        active: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < 33 || association.deviceProfile != AssociationRequest.DEVICE_PROFILE_WATCH) return
        val platform = manager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                val request = ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build()
                if (active) platform.startObservingDevicePresence(request) else platform.stopObservingDevicePresence(request)
            } else {
                association.deviceMacAddress?.let {
                    if (active) {
                        platform.startObservingDevicePresence(
                            it.toString(),
                        )
                    } else {
                        platform.stopObservingDevicePresence(it.toString())
                    }
                }
            }
        } catch (e: Exception) {
            // A revoked association or missing OS permission must never turn a presence hint into a link.
            Napier.w("Could not observe watch presence", e)
        }
    }
}
