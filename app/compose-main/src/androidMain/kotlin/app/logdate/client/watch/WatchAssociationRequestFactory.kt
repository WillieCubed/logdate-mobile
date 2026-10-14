package app.logdate.client.watch

import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import java.util.regex.Pattern

interface WatchAssociationRequestFactory {
    fun createRequest(target: WatchNodeSnapshot? = null): AssociationRequest
}

class DefaultWatchAssociationRequestFactory(
    private val builder: () -> AssociationRequest.Builder = { AssociationRequest.Builder() },
    private val filter: (String) -> BluetoothDeviceFilter = { name ->
        BluetoothDeviceFilter.Builder().setNamePattern(Pattern.compile(Pattern.quote(name))).build()
    },
) : WatchAssociationRequestFactory {
    override fun createRequest(target: WatchNodeSnapshot?): AssociationRequest {
        requireNotNull(target) { "A connected Wear watch is required" }
        val request = builder().setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
        request
            .addDeviceFilter(
                filter(target.displayName),
            ).setSingleDevice(true)
        return request.build()
    }
}
