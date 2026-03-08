package app.logdate.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.MigrateResult
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseConfigTest {
    @AfterTest
    fun cleanupMocks() {
        unmockkAll()
    }

    @Test
    fun `parse and normalize database URLs`() {
        val parsed = invokePrivate("parseDatabaseUrl", "postgres://dbuser:dbpass@localhost:5544/logdate?sslmode=require")
        assertNotNull(parsed)
        assertEquals("jdbc:postgresql://localhost:5544/logdate?sslmode=require", readField(parsed, "jdbcUrl"))
        assertEquals("dbuser", readField(parsed, "username"))
        assertEquals("dbpass", readField(parsed, "password"))
        assertEquals("dbuser", invokeGetter(parsed, "getUsername"))
        assertEquals("dbpass", invokeGetter(parsed, "getPassword"))

        val parsedJdbc = invokePrivate("parseDatabaseUrl", "jdbc:postgresql://localhost:5432/logdate")
        assertNotNull(parsedJdbc)
        assertEquals("jdbc:postgresql://localhost:5432/logdate", readField(parsedJdbc, "jdbcUrl"))

        val parsedInvalid = invokePrivate("parseDatabaseUrl", "not-a-valid-uri")
        assertNull(parsedInvalid)
        val parsedException = invokePrivate("parseDatabaseUrl", "postgres://%zz")
        assertNull(parsedException)

        val normalizedPostgres = invokePrivateString("normalizeJdbcUrl", "postgres://localhost:5432/logdate")
        assertEquals("jdbc:postgresql://localhost:5432/logdate", normalizedPostgres)

        val normalizedPostgresql = invokePrivateString("normalizeJdbcUrl", "postgresql://localhost:5432/logdate")
        assertEquals("jdbc:postgresql://localhost:5432/logdate", normalizedPostgresql)

        val normalizedJdbc = invokePrivateString("normalizeJdbcUrl", "jdbc:postgresql://localhost:5432/logdate")
        assertEquals("jdbc:postgresql://localhost:5432/logdate", normalizedJdbc)

        val normalizedOther = invokePrivateString("normalizeJdbcUrl", "mysql://localhost/logdate")
        assertEquals("mysql://localhost/logdate", normalizedOther)
    }

    @Test
    fun `buildConfig sets expected pool and driver properties`() {
        val config =
            invokePrivate("buildConfig", "jdbc:postgresql://localhost:5432/logdate", "user", "pass") as HikariConfig

        assertEquals("jdbc:postgresql://localhost:5432/logdate", config.jdbcUrl)
        assertEquals("user", config.username)
        assertEquals("pass", config.password)
        assertEquals("org.postgresql.Driver", config.driverClassName)
        assertEquals(20, config.maximumPoolSize)
        assertEquals(5, config.minimumIdle)
        assertEquals(30000, config.connectionTimeout)
        assertEquals(600000, config.idleTimeout)
        assertEquals(1800000, config.maxLifetime)
        assertTrue(!config.isAutoCommit)
        assertEquals("TRANSACTION_READ_COMMITTED", config.transactionIsolation)
    }

    @Test
    fun `createDataSource and createTestDataSource create hikari data sources`() {
        val createDefaultFromEnv = runCatching { DatabaseConfig.createDataSource() }
        assertTrue(createDefaultFromEnv.isSuccess)
        (createDefaultFromEnv.getOrNull() as? HikariDataSource)?.close()

        val createDefault =
            runCatching {
                DatabaseConfig.createDataSource(
                    host = "localhost",
                    port = 5432,
                    database = "logdate",
                    username = "logdate",
                    password = "logdate",
                    databaseUrl = null,
                )
            }
        assertTrue(createDefault.isSuccess)
        (createDefault.getOrNull() as? HikariDataSource)?.close()

        val createFromUrl =
            runCatching {
                DatabaseConfig.createDataSource(
                    username = "explicit",
                    password = "secret",
                    databaseUrl = "postgres://dbuser:dbpass@localhost:5432/logdate",
                )
            }
        assertTrue(createFromUrl.isSuccess)
        (createFromUrl.getOrNull() as? HikariDataSource)?.close()

        val createTest = runCatching { DatabaseConfig.createTestDataSource() }
        assertTrue(createTest.isSuccess)
        (createTest.getOrNull() as? HikariDataSource)?.close()
    }

    @Test
    fun `initialize database rethrows migration errors from datasource failures`() {
        val dataSource =
            object : DataSource {
                override fun getConnection(): Connection = throw SQLException("expected-test-failure")

                override fun getConnection(
                    username: String?,
                    password: String?,
                ): Connection = throw SQLException("expected-test-failure")

                override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLException("unsupported")

                override fun isWrapperFor(iface: Class<*>?): Boolean = false

                override fun getLogWriter() = null

                override fun setLogWriter(out: java.io.PrintWriter?) = Unit

                override fun setLoginTimeout(seconds: Int) = Unit

                override fun getLoginTimeout(): Int = 0

                override fun getParentLogger(): Logger = Logger.getGlobal()
            }

        assertFailsWith<Exception> {
            DatabaseConfig.initializeDatabase(dataSource)
        }
    }

    @Test
    fun `initializeDatabase and runMigrations cover success and failure branches`() {
        val dataSource = mockk<DataSource>(relaxed = true)
        val database = mockk<org.jetbrains.exposed.sql.Database>()

        var migrated = false
        val initialized =
            DatabaseConfig.initializeDatabase(
                dataSource = dataSource,
                migrate = { migrated = true },
                connect = { database },
            )
        assertTrue(migrated)
        assertEquals(database, initialized)

        assertFailsWith<IllegalStateException> {
            DatabaseConfig.initializeDatabase(
                dataSource = dataSource,
                migrate = { throw IllegalStateException("migration-failure") },
                connect = { database },
            )
        }

        val configuration = mockk<FluentConfiguration>()
        val flyway = mockk<Flyway>()
        val migrateResult = MigrateResult().apply { migrationsExecuted = 1 }

        mockkStatic(Flyway::class)

        every { Flyway.configure() } returns configuration
        every { configuration.dataSource(dataSource) } returns configuration
        every { configuration.locations("classpath:db/migration") } returns configuration
        every { configuration.load() } returns flyway
        every { flyway.migrate() } returns migrateResult

        val runMigrations =
            DatabaseConfig::class.java.getDeclaredMethod(
                "runMigrations",
                DataSource::class.java,
            )
        runMigrations.isAccessible = true
        runMigrations.invoke(DatabaseConfig, dataSource)

        every { flyway.migrate() } throws IllegalStateException("migration-boom")
        val ex =
            assertFailsWith<java.lang.reflect.InvocationTargetException> {
                runMigrations.invoke(DatabaseConfig, dataSource)
            }
        assertTrue(ex.cause is IllegalStateException)
    }

    private fun invokePrivate(
        methodName: String,
        vararg args: Any,
    ): Any? {
        val argTypes = args.map { it::class.java }.toTypedArray()
        val method = DatabaseConfig::class.java.getDeclaredMethod(methodName, *argTypes)
        method.isAccessible = true
        return method.invoke(DatabaseConfig, *args)
    }

    private fun invokePrivateString(
        methodName: String,
        arg: String,
    ): String = invokePrivate(methodName, arg) as String

    private fun readField(
        target: Any,
        fieldName: String,
    ): String? {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as String?
    }

    private fun invokeGetter(
        target: Any,
        methodName: String,
    ): String? = target::class.java.getDeclaredMethod(methodName).invoke(target) as String?
}
