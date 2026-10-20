package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.identity.DeviceIdProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** One successful enrollment per unchanged authenticated installation. */
internal class SyncDeviceEnrollment(
    private val sessions: SessionStorage,
    private val devices: DeviceIdProvider?,
    private val isAuthenticated: suspend () -> Boolean,
    private val enroll: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var enrolled: Scope? = null

    suspend fun ensureEnrolled() {
        if (!isAuthenticated()) return
        val session = sessions.getOriginBoundSession() ?: return
        val selected = Scope(session.origin, session.session.accountId, devices?.getDeviceId()?.value)
        mutex.withLock {
            if (enrolled == selected) return@withLock
            enroll()
            if (sessions.getOriginBoundSession() == session) enrolled = selected
        }
    }

    private data class Scope(
        val origin: String,
        val accountId: String,
        val deviceId: Uuid?,
    )
}
