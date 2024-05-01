package app.logdate.core.installreferrer

/**
 * An exception that is thrown when the install referrer service is disconnected.
 *
 * Receivers of this exception should attempt to reconnect to the install referrer service.
 */
class ReferrerServiceDisconnectedException :
    IllegalStateException("Install referrer service disconnected")