package app.logdate.server.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    @Test
    fun `production encoder excludes library messages context and nested exceptions`() {
        val marker = "PRIVATE_LIBRARY_TOKEN_PATH_SENTINEL"
        val library = LoggerFactory.getLogger(marker) as Logger
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

        @Suppress("UNCHECKED_CAST")
        val stdout = root.getAppender("STDOUT") as ch.qos.logback.core.OutputStreamAppender<ILoggingEvent>
        val event =
            ch.qos.logback.classic.spi
                .LoggingEvent(
                    marker,
                    library,
                    Level.ERROR,
                    marker,
                    IllegalStateException(marker, RuntimeException(marker)),
                    arrayOf(marker),
                ).apply {
                    mdcPropertyMap = mapOf(marker to marker)
                    threadName = marker
                }
        val encoded = stdout.encoder.encode(event).decodeToString()
        assertTrue(!encoded.contains(marker))
        assertTrue(encoded.contains("SERVER_LIBRARY_EVENT"))
    }

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
    fun `arbitrary messages tags and nested causes cannot cross the logging boundary`() {
        val secret = "PRIVATE_CONTENT_TOKEN_PATH_SENTINEL"
        antilog.log(LogLevel.ERROR, secret, IllegalStateException(secret, RuntimeException(secret)), secret)
        val event = appender.list.single()
        assertTrue(!event.formattedMessage.contains(secret))
        assertTrue(!event.loggerName.contains(secret))
        assertEquals(null, event.throwableProxy)
    }

    @Test
    fun `warnings reach slf4j so the in-memory fallback is visible`() {
        antilog.log(LogLevel.WARNING, null, null, "Database not available, using in-memory repositories")

        val event = appender.list.single()
        assertEquals(Level.WARN, event.level)
        assertEquals("SERVER_DATABASE_IN_MEMORY", event.formattedMessage)
    }

    @Test
    fun `current database fallback warning remains actionable after sanitization`() {
        antilog.log(
            LogLevel.WARNING,
            null,
            RuntimeException("private connection detail"),
            "LOGDATE_ALLOW_INMEMORY_FALLBACK is set: running on in-memory repositories. Nothing is " +
                "persisted and every record is lost when the process exits.",
        )
        assertEquals("SERVER_DATABASE_IN_MEMORY", appender.list.single().formattedMessage)
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
    fun `throwables are excluded before downstream appenders`() {
        val cause = IllegalStateException("Database credential missing")

        antilog.log(LogLevel.ERROR, null, cause, "Production database unavailable")

        val event = appender.list.single()
        assertEquals(Level.ERROR, event.level)
        assertEquals(null, event.throwableProxy)
    }

    @Test
    fun `a record with no message uses a finite failure code`() {
        antilog.log(LogLevel.ERROR, null, IllegalStateException("only on the throwable"), null)

        assertEquals("SERVER_ERROR", appender.list.single().formattedMessage)
    }

    @Test
    fun `a record with neither message nor throwable is dropped`() {
        antilog.log(LogLevel.ERROR, null, null, null)

        assertTrue(appender.list.isEmpty())
    }

    @Test
    fun `arbitrary tags cannot become logger names`() {
        antilog.log(LogLevel.INFO, "$SERVER_LOG_NAME.sync", null, "tagged")

        assertEquals(SERVER_LOG_NAME, appender.list.single().loggerName)
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
