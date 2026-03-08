package app.logdate.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.aakira.napier.Napier
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

object DatabaseConfig {
    fun createDataSource(
        host: String = System.getenv("DB_HOST") ?: "localhost",
        port: Int = System.getenv("DB_PORT")?.toIntOrNull() ?: 5432,
        database: String = System.getenv("DB_NAME") ?: "logdate",
        username: String? = System.getenv("DATABASE_USER") ?: System.getenv("DB_USER"),
        password: String? = System.getenv("DATABASE_PASSWORD") ?: System.getenv("DB_PASSWORD"),
        databaseUrl: String? = System.getenv("DATABASE_URL"),
    ): DataSource {
        val urlFromEnv = databaseUrl?.trim().takeIf { !it.isNullOrEmpty() }
        val dataSourceConfig =
            if (urlFromEnv != null) {
                val parsedUrl = parseDatabaseUrl(urlFromEnv)
                val jdbcUrl = parsedUrl?.jdbcUrl ?: normalizeJdbcUrl(urlFromEnv)
                val resolvedUsername = username ?: parsedUrl?.username ?: "logdate"
                val resolvedPassword = password ?: parsedUrl?.password ?: "logdate"
                buildConfig(jdbcUrl, resolvedUsername, resolvedPassword)
            } else {
                val jdbcUrl = "jdbc:postgresql://$host:$port/$database"
                val resolvedUsername = username ?: "logdate"
                val resolvedPassword = password ?: "logdate"
                buildConfig(jdbcUrl, resolvedUsername, resolvedPassword)
            }

        return HikariDataSource(dataSourceConfig)
    }

    fun initializeDatabase(
        dataSource: DataSource,
        migrate: (DataSource) -> Unit = ::runMigrations,
        connect: (DataSource) -> Database = Database::connect,
    ): Database {
        migrate(dataSource)
        return connect(dataSource)
    }

    private fun runMigrations(dataSource: DataSource) {
        try {
            Napier.i("Running database migrations...")

            val flyway =
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()

            val migrationResult = flyway.migrate()

            Napier.i("Database migrations completed. Applied ${migrationResult.migrationsExecuted} migrations.")
        } catch (e: Exception) {
            Napier.e("Failed to run database migrations", e)
            throw e
        }
    }

    fun createTestDataSource(): DataSource =
        createDataSource(
            host = "localhost",
            port = 5432,
            database = "logdate_test",
            username = "logdate",
            password = "logdate",
            databaseUrl = null,
        )

    private fun buildConfig(
        jdbcUrl: String,
        username: String,
        password: String,
    ): HikariConfig =
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"

            // Connection pool settings
            maximumPoolSize = 20
            minimumIdle = 5
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            initializationFailTimeout = -1

            // Validation settings
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // Additional PostgreSQL optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

    private fun normalizeJdbcUrl(rawUrl: String): String =
        when {
            rawUrl.startsWith("jdbc:") -> rawUrl
            rawUrl.startsWith("postgres://") -> "jdbc:postgresql://${rawUrl.removePrefix("postgres://")}"
            rawUrl.startsWith("postgresql://") -> "jdbc:$rawUrl"
            else -> rawUrl
        }

    private data class ParsedDatabaseUrl(
        val jdbcUrl: String,
        val username: String?,
        val password: String?,
    )

    private fun parseDatabaseUrl(rawUrl: String): ParsedDatabaseUrl? {
        return try {
            val sanitized = rawUrl.removePrefix("jdbc:")
            val uri = java.net.URI(sanitized)
            val scheme = if (uri.scheme == "postgres") "postgresql" else uri.scheme
            val host = uri.host ?: return null
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val path = uri.path ?: ""
            val query = if (uri.query.isNullOrBlank()) "" else "?${uri.query}"
            val userInfo = uri.userInfo?.split(":", limit = 2)
            val username = userInfo?.getOrNull(0)
            val password = userInfo?.getOrNull(1)
            val jdbcUrl = "jdbc:$scheme://$host$port$path$query"
            ParsedDatabaseUrl(jdbcUrl, username, password)
        } catch (_: Exception) {
            null
        }
    }
}
