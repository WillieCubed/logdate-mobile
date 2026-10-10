package app.logdate.server.di

import app.logdate.server.config.RuntimeProfile
import app.logdate.server.config.profileAwareBoolEnv
import app.logdate.server.database.DatabaseConfig
import io.github.aakira.napier.Napier
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Opt-in for starting without a database, for local work that does not need persistence.
 *
 * Unset by default: an unreachable database is a startup failure, not something to route around
 * silently. See [initializeDatabase].
 */
const val ALLOW_INMEMORY_FALLBACK_ENV: String = "LOGDATE_ALLOW_INMEMORY_FALLBACK"

/**
 * Initializes the database connection and tables.
 *
 * Flyway owns the schema outright. Migrations run only when `AUTO_MIGRATE=true` (the documented
 * production policy is to apply them as a separate CI step), and that same run's
 * `beforeMigrate.sql` callback creates the legacy `sync_*` tables, their `deleted` /
 * `deleted_at` columns included. Nothing reconciles the schema at runtime, so with
 * `AUTO_MIGRATE=false` the server connects to whatever schema is already there.
 *
 * A database failure now stops startup everywhere. In production (`LOGDATE_ENV=production`) the
 * original failure is rethrown so Cloud Run's startup probe rolls the revision back. Elsewhere it
 * becomes an [IllegalStateException] naming [ALLOW_INMEMORY_FALLBACK_ENV], which is the only way
 * to reach the in-memory repositories — set it when you deliberately want a server without
 * Postgres and accept that nothing survives a restart.
 *
 * @param readEnv environment reader, overridable in tests.
 * @return true if Postgres is wired in, false if the opted-in in-memory fallback was taken.
 */
fun initializeDatabase(
    // System property first, then environment — the same order main() uses for PORT and HOST,
    // so a `-D` flag can opt in without reaching for the process environment.
    readEnv: (String) -> String? = { System.getProperty(it) ?: System.getenv(it) },
): Boolean =
    try {
        val dataSource = DatabaseConfig.createDataSource()
        val runFlyway = DatabaseConfig.shouldRunMigrations()
        val database = DatabaseConfig.initializeDatabase(dataSource, autoMigrate = runFlyway)
        // Register the application database before Koin creates entitlement services. Exposed's
        // default handle is not guaranteed when Database.connect is performed during bootstrap.
        TransactionManager.defaultDatabase = database
        Napier.i("Database repositories initialized successfully")
        true
    } catch (e: Exception) {
        val profile = RuntimeProfile.fromEnvironment(readEnv)
        if (profile.isProduction) {
            Napier.e("Production database unavailable; refusing to start with in-memory fallback", e)
            throw e
        }
        val fallbackAllowed =
            profileAwareBoolEnv(
                name = ALLOW_INMEMORY_FALLBACK_ENV,
                productionDefault = false,
                devDefault = false,
                readEnv = readEnv,
                profile = profile,
            )
        if (!fallbackAllowed) {
            Napier.e("Database unavailable; refusing to start somewhere nothing would persist", e)
            throw IllegalStateException(
                "Database unavailable, so nothing would be persisted. Fix the connection — " +
                    "DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD are all required — or set " +
                    "$ALLOW_INMEMORY_FALLBACK_ENV=true to run on in-memory repositories on purpose.",
                e,
            )
        }
        Napier.w(
            "$ALLOW_INMEMORY_FALLBACK_ENV is set: running on in-memory repositories. Nothing is " +
                "persisted and every record is lost when the process exits.",
            e,
        )
        false
    }
