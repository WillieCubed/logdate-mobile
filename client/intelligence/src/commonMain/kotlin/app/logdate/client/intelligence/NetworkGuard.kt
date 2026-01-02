package app.logdate.client.intelligence

import app.logdate.client.networking.NetworkAvailabilityMonitor

internal fun NetworkAvailabilityMonitor.unavailableReason(): AIUnavailableReason? {
    return if (isNetworkAvailable()) null else AIUnavailableReason.NoNetwork
}
