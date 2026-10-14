package app.logdate.client.watch.presence

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build

/** OS presence is a scheduling hint; the worker checks verified current links before any publication. */
class LogDateCompanionDeviceService : CompanionDeviceService() {
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= 33 && associationInfo.deviceProfile == AssociationRequest.DEVICE_PROFILE_WATCH) {
            WatchCatchUpWorker.enqueue(this)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT in 33..35) WatchCatchUpWorker.enqueue(this)
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (Build.VERSION.SDK_INT >= 36 &&
            event.event in
            setOf(
                DevicePresenceEvent.EVENT_BLE_APPEARED,
                DevicePresenceEvent.EVENT_BT_CONNECTED,
                DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED,
            )
        ) {
            WatchCatchUpWorker.enqueue(this)
        }
    }
}
