package app.logdate.server.logging

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logger name used for Napier calls that carry no tag of their own.
 */
const val SERVER_LOG_NAME: String = "app.logdate.server"

private val installed = AtomicBoolean(false)

/**
 * Routes Napier through SLF4J so server logging actually goes somewhere.
 *
 * Napier discards every call until an [Antilog] is registered. The Android, Wear, and desktop
 * entry points each install one at startup; the server never did, so all of its Napier calls
 * were dropped — including the warning that the database was unavailable and the process had
 * fallen back to in-memory repositories. Because `logback.xml` also feeds a `SentryAppender`,
 * the same gap kept server-side [Napier.e] calls out of Sentry.
 *
 * Call this before anything that logs. Repeat calls are ignored rather than stacking a second
 * antilog, which would double every line.
 */
fun installServerLogging() {
    if (installed.compareAndSet(false, true)) {
        Napier.base(Slf4jAntilog())
    }
}

/**
 * Forwards Napier records to SLF4J, which `logback.xml` fans out to stdout and Sentry.
 *
 * A Napier tag selects the logger, so `logback.xml` can raise or lower a single subsystem the
 * same way it already does for `io.netty`. Untagged calls — the overwhelming majority — land on
 * [SERVER_LOG_NAME]. The tag is never derived by walking the stack the way Napier's own
 * `DebugAntilog` does: that costs a stack capture on every call and is fragile under
 * minification.
 */
internal class Slf4jAntilog : Antilog() {
    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        val text = message ?: throwable?.message ?: return
        val logger = LoggerFactory.getLogger(tag?.takeIf(String::isNotBlank) ?: SERVER_LOG_NAME)
        when (priority) {
            LogLevel.VERBOSE -> logger.trace(text, throwable)
            LogLevel.DEBUG -> logger.debug(text, throwable)
            LogLevel.INFO -> logger.info(text, throwable)
            LogLevel.WARNING -> logger.warn(text, throwable)
            LogLevel.ERROR, LogLevel.ASSERT -> logger.error(text, throwable)
        }
    }
}
