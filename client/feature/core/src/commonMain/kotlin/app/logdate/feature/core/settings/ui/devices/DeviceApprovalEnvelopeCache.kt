package app.logdate.feature.core.settings.ui.devices

internal class DeviceApprovalEnvelopeCache {
    private var pending: Pair<String, String>? = null

    suspend fun envelopeFor(
        requestId: String,
        create: suspend () -> String,
    ): String {
        pending?.takeIf { it.first == requestId }?.let { return it.second }
        return create().also { pending = requestId to it }
    }

    fun clear() {
        pending = null
    }
}
