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
        username: String = System.getenv("DB_USER") ?: "logdate",
        password: String = System.getenv("DB_PASSWORD") ?: "logdate"
    ): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://$host:$port/$database"
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
            
            // Connection pool settings
            maximumPoolSize = 20
            minimumIdle = 5
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            
            // Validation settings
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            
            // Additional PostgreSQL optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        
        return HikariDataSource(config)
    }
    
    fun initializeDatabase(dataSource: DataSource): Database {
        // Run Flyway migrations
        runMigrations(dataSource)
        
        // Connect Exposed to the database
        return Database.connect(dataSource)
    }
    
    private fun runMigrations(dataSource: DataSource) {
        try {
            Napier.i("Running database migrations...")
            
            val flyway = Flyway.configure()
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
    
    fun createTestDataSource(): DataSource {
        return createDataSource(
            host = "localhost",
            port = 5432,
            database = "logdate_test",
            username = "logdate",
            password = "logdate"
        )
    }
}