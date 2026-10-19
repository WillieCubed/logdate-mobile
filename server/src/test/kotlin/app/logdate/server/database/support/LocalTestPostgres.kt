package app.logdate.server.database.support

import app.logdate.server.database.DatabaseConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.Assume.assumeTrue
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

private const val CONNECT_TIMEOUT_MILLIS = 1_000

/**
 * The URL of the local test PostgreSQL database: `DATABASE_URL`, with `DATABASE_USER` and
 * `DATABASE_PASSWORD`, the same variables the server reads. CI sets them for its throwaway database
 * container, and `./run` sets them for the database `./run dev` starts.
 *
 * The calling test is skipped, with the reason, when `DATABASE_URL` is unset, or when nothing answers
 * at its address, except in CI (`CI` is set), where both fail instead so that a job that loses its
 * database can't pass by skipping every test. It also fails for a database that isn't on this
 * machine, so a test can never write to a shared or production server.
 *
 * Other tests use the same database at the same time, so a test must not assume it's empty and must
 * delete the rows it adds. A test that can't do that runs in its own database with [withOwnPostgresDatabase].
 */
fun localTestPostgresUrl(): String {
    val url = System.getenv("DATABASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
    check(url != null || !runsInCi()) {
        "CI runs the PostgreSQL tests, so it must set DATABASE_URL; a job without it would skip them and still pass"
    }
    assumeTrue("Set DATABASE_URL to a PostgreSQL database on this machine, as ./run and CI do, to run this test", url != null)
    val local = requireNotNull(url)
    val uri = postgresUri(local)
    require(uri.host in LOOPBACK_HOSTS) {
        "Database tests write to the database they're given, so they only run against one on localhost or 127.0.0.1, " +
            "not ${uri.host}"
    }
    if (!runsInCi()) {
        val port = uri.port.takeIf { it > 0 } ?: 5432
        assumeTrue("Nothing answers at ${uri.host}:$port from DATABASE_URL; start it with ./run dev", answers(uri.host, port))
    }
    return local
}

private fun runsInCi(): Boolean = System.getenv("CI") != null

private fun answers(
    host: String,
    port: Int,
): Boolean =
    try {
        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS) }
        true
    } catch (_: IOException) {
        false
    }

/**
 * A connection pool for [url]. Credentials come from `DATABASE_USER` and `DATABASE_PASSWORD`, as the
 * server reads them, or else from [url] itself.
 */
fun localTestPostgresDataSource(url: String = localTestPostgresUrl()): HikariDataSource =
    DatabaseConfig.createDataSource(databaseUrl = url) as HikariDataSource

/**
 * Creates a database named [prefix] and a random suffix on the local test server, runs [block] with
 * its URL, and drops it afterwards, even when [block] fails. For tests that leave rows behind or that
 * need a database no other test writes to.
 */
@OptIn(ExperimentalUuidApi::class)
fun <T> withOwnPostgresDatabase(
    prefix: String,
    block: (url: String) -> T,
): T {
    require(prefix.matches(Regex("[a-z_]+"))) { "A database prefix is lowercase letters and underscores" }
    val shared = localTestPostgresUrl()
    val name = prefix + Uuid.random().toHexString().take(12)
    localTestPostgresDataSource(shared).use { admin ->
        admin.execute("CREATE DATABASE $name")
        try {
            return block(withDatabaseName(shared, name))
        } finally {
            admin.execute("DROP DATABASE IF EXISTS $name WITH (FORCE)")
        }
    }
}

/** Runs [sql] outside a transaction, as `CREATE DATABASE` and `DROP DATABASE` require. */
private fun HikariDataSource.execute(sql: String) {
    connection.use { connection ->
        connection.autoCommit = true
        connection.createStatement().use { it.execute(sql) }
    }
}

private fun postgresUri(url: String): URI = URI(url.removePrefix("jdbc:"))

private fun withDatabaseName(
    url: String,
    name: String,
): String {
    val uri = postgresUri(url)
    val renamed = URI(uri.scheme, uri.userInfo, uri.host, uri.port, "/$name", uri.query, uri.fragment).toString()
    return if (url.startsWith("jdbc:")) "jdbc:$renamed" else renamed
}
