package app.logdate.client.launch

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/** Credential storage stays unavailable until the platform confirms the first unlock after boot. */
internal suspend fun awaitUserUnlock(
    isUnlocked: () -> Boolean,
    register: (onChanged: () -> Unit) -> AutoCloseable,
) {
    if (isUnlocked()) return
    callbackFlow {
        val registration = register { if (isUnlocked()) trySend(Unit) }
        // Unlock can occur before the receiver is registered, without another broadcast afterward.
        if (isUnlocked()) trySend(Unit)
        awaitClose { registration.close() }
    }.first()
}
