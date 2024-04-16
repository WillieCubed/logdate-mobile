package app.logdate.core.installreferrer.model

/**
 * The state of the install referrer service.
 */
enum class ReferrerState {
    /**
     * A connection to the install referrer service was successfully established.
     */
    CONNECTED,

    /**
     * The install referrer service is not supported on this device.
     *
     * A client should not attempt to connect to the install referrer service on this device.
     */
    NOT_SUPPORTED,

    /**
     * A connection to the install referrer service could not be established.
     *
     * This may indicate that the referrer service was updating. Clients should retry the connection
     * after a short delay.
     */
    UNAVAILABLE,
}