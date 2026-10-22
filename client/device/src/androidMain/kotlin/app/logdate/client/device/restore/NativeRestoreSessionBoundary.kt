package app.logdate.client.device.restore

internal class NativeRestoreSessionBoundary(
    private val begin: () -> Unit,
) {
    private val lock = Any()
    private var started = false

    fun beginIfNeeded(resume: () -> Boolean = { false }) =
        synchronized(lock) {
            if (!started) {
                if (!resume()) begin()
                started = true
            }
        }

    fun finish() =
        synchronized(lock) {
            started = false
        }

    fun isStarted(): Boolean = synchronized(lock) { started }
}
