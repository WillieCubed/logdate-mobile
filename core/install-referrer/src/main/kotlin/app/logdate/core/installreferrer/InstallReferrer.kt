package app.logdate.core.installreferrer

import app.logdate.core.installreferrer.model.InstallReferrerData
import app.logdate.core.installreferrer.model.ReferrerState

/**
 * Interface for fetching install referrer data.
 *
 * This can be used to determine how users found and installed the app.
 */
interface InstallReferrer {
    /**
     * Fetches the state of the install referrer service.
     *
     * This can be used to determine if the service is available on the device.
     */
    suspend fun getReferrerState(): ReferrerState

    /**
     * Fetches the install referrer data.
     *
     * Before calling this method, ensure that the install referrer service is available by calling
     * [getReferrerState].
     */
    suspend fun getReferrerData(): InstallReferrerData
}