package app.logdate.server.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The server makes well over a hundred Napier calls but, unlike the Android, Wear, and desktop
 * entry points, never installed an antilog — so every one of them was discarded. That hid the
 * in-memory database fallback, and because logback also feeds a `SentryAppender`, it kept
 * server-side `Napier.e` calls out of Sentry entirely.
 *
 * These assert against [Slf4jAntilog] directly rather than through [Napier], whose registry is
 * process-global and cannot be torn down: `Napier.takeLogarithm(antilog)` throws
 * `IllegalArgumentException: Illegal Capacity: -1` in 2.7.1, because `AtomicMutableList.remove`
 * sizes its copy with `-1`.
 */
class ServerLoggingTest {
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val antilog = Slf4jAntilog()

    @BeforeTest
    fun setUp() {
        logger = LoggerFactory.getLogger(SERVER_LOG_NAME) as Logger
        logger.level = Level.TRACE
        // Keep these events off the root appenders so the suite's own output stays readable.
        logger.isAdditive = false
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
        logger.isAdditive = true
    }

    @Test
    fun `warnings reach slf4j so the in-memory fallback is visible`() {
        antilog.log(LogLevel.WARNING, null, null, "Database not available, using in-memory repositories")

        val event = appender.list.single()
        assertEquals(Level.WARN, event.level)
        assertEquals("Database not available, using in-memory repositories", event.formattedMessage)
    }

    @Test
    fun `napier levels map onto slf4j levels`() {
        LogLevel.entries.forEach { antilog.log(it, null, null, it.name) }

        assertEquals(
            listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.ERROR),
            appender.list.map { it.level },
        )
    }

    @Test
    fun `throwables are forwarded so the sentry appender can capture them`() {
        val cause = IllegalStateException("Database credential missing")

        antilog.log(LogLevel.ERROR, null, cause, "Production database unavailable")

        val event = appender.list.single()
        assertEquals(Level.ERROR, event.level)
        assertSame(cause, (event.throwableProxy as ThrowableProxy).throwable)
    }

    @Test
    fun `a record with no message falls back to the throwable`() {
        antilog.log(LogLevel.ERROR, null, IllegalStateException("only on the throwable"), null)

        assertEquals("only on the throwable", appender.list.single().formattedMessage)
    }

    @Test
    fun `a record with neither message nor throwable is dropped`() {
        antilog.log(LogLevel.ERROR, null, null, null)

        assertTrue(appender.list.isEmpty())
    }

    @Test
    fun `a tag selects the logger so logback config can target a subsystem`() {
        antilog.log(LogLevel.INFO, "$SERVER_LOG_NAME.sync", null, "tagged")

        assertEquals("$SERVER_LOG_NAME.sync", appender.list.single().loggerName)
    }

    @Test
    fun `a blank tag falls back to the default logger`() {
        antilog.log(LogLevel.INFO, "   ", null, "untagged")

        assertEquals(SERVER_LOG_NAME, appender.list.single().loggerName)
    }

    @Test
    fun `installing twice does not duplicate output`() {
        installServerLogging()
        installServerLogging()

        Napier.w("once")

        assertEquals(1, appender.list.size, "A second install must not add a second antilog")
    }
}
