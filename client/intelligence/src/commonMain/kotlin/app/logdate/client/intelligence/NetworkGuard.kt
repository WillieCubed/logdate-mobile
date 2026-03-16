package app.logdate.client.intelligence

import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.shouldAllowAICalls

internal fun NetworkAvailabilityMonitor.unavailableReason(): AIUnavailableReason? =
    if (isNetworkAvailable()) null else AIUnavailableReason.NoNetwork

internal suspend fun unavailableReason(
    networkMonitor: NetworkAvailabilityMonitor,
    dataUsagePolicy: DataUsagePolicy,
): AIUnavailableReason? {
    if (!networkMonitor.isNetworkAvailable()) return AIUnavailableReason.NoNetwork
    if (!dataUsagePolicy.currentMode().shouldAllowAICalls()) return AIUnavailableReason.DataSaverActive
    return null
}
