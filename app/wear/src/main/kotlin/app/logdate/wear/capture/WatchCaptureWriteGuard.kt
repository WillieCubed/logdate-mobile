package app.logdate.wear.capture

/** Serializes local capture persistence with erasure of a removed watch. */
interface WatchCaptureWriteGuard {
    suspend fun <T> write(block: suspend () -> T): T

    object Unrestricted : WatchCaptureWriteGuard {
        override suspend fun <T> write(block: suspend () -> T): T = block()
    }
}
