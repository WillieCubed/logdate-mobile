package app.logdate.server.routes.sync

import java.nio.file.Path
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the verified plaintext until streaming ends or the request ends without streaming it. */
internal class BackupResponseFileLease(
    private val path: Path,
    maxLifetimeMillis: Long = MAX_RESPONSE_LEASE_MILLIS,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val expiry = responseLeaseCleaner.schedule({ close() }, maxLifetimeMillis, TimeUnit.MILLISECONDS)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            expiry.cancel(false)
            runCatching { deletePrivateBackupTempFile(path) }
        }
    }
}

private const val MAX_RESPONSE_LEASE_MILLIS = 24L * 60 * 60 * 1000
private val responseLeaseCleaner =
    ScheduledThreadPoolExecutor(1) { command ->
        Thread(command, "logdate-backup-response-cleanup").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
    }
