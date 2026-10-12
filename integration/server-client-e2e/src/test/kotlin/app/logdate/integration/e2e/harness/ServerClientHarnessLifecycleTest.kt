package app.logdate.integration.e2e.harness

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerClientHarnessLifecycleTest {
    @Test
    fun `stopping a test server releases its database connections`() =
        runBlocking {
            assumeTrue(!System.getenv("DATABASE_URL").isNullOrBlank())
            val initialConnections = connectionCount()
            repeat(3) {
                withServerClientHarness {
                    assertTrue(connectionCount() > initialConnections)
                }
                assertEquals(initialConnections, connectionCount(), "The stopped harness leaked database connections")
            }
        }

    @Test
    fun `a failed test block releases its database connections`() =
        runBlocking {
            assumeTrue(!System.getenv("DATABASE_URL").isNullOrBlank())
            val initialConnections = connectionCount()
            assertFailsWith<IllegalStateException> {
                withServerClientHarness { error("test failure") }
            }
            assertEquals(initialConnections, connectionCount())
        }

    @Test
    fun `a failed server startup releases its database connections`() =
        runBlocking {
            assumeTrue(!System.getenv("DATABASE_URL").isNullOrBlank())
            val initialConnections = connectionCount()
            ServerSocket().use { occupied ->
                occupied.reuseAddress = false
                occupied.bind(InetSocketAddress("127.0.0.1", 0))
                assertFailsWith<BindException> {
                    withServerClientHarness(serverPort = occupied.localPort) {
                        error("The occupied port must prevent startup")
                    }
                }
            }
            assertEquals(initialConnections, connectionCount())
        }

    private fun connectionCount(): Int =
        DriverManager
            .getConnection(
                System.getenv("DATABASE_URL"),
                System.getenv("DATABASE_USER"),
                System.getenv("DATABASE_PASSWORD"),
            ).use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT count(*) FROM pg_stat_activity " +
                                "WHERE datname = current_database() AND pid <> pg_backend_pid()",
                        ).use { result ->
                            check(result.next())
                            result.getInt(1)
                        }
                }
            }
}
