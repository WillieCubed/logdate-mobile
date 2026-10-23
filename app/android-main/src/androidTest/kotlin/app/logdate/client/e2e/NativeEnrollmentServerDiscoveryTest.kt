package app.logdate.client.e2e

import app.logdate.client.networking.DiscoveryFailure
import app.logdate.client.networking.ServerDiscoveryException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.ConnectException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeEnrollmentServerDiscoveryTest {
    @Test
    fun deadlineRetainsSanitizedLastDiscoveryFailure() =
        runBlocking {
            val last =
                ServerDiscoveryException(
                    DiscoveryFailure.UNREACHABLE,
                    "private token",
                    ConnectException("private origin: ECONNREFUSED"),
                )
            val failure =
                assertFailsWith<TimeoutCancellationException> {
                    awaitNativeEnrollmentServer<String>(timeoutMillis = 25) { Result.failure(last) }
                }
            val diagnostic =
                failure.suppressed
                    .single()
                    .message
                    .orEmpty()
            assertTrue(diagnostic.contains("kind=UNREACHABLE"))
            assertTrue(diagnostic.contains("java.net.ConnectException"))
            assertTrue(diagnostic.contains("reason=ECONNREFUSED"))
            assertFalse(diagnostic.contains("private"))
            assertTrue(diagnostic.length < 1024)
        }

    @Test
    fun unreadableFailureRemainsExactOriginal() =
        runBlocking {
            val original = ServerDiscoveryException(DiscoveryFailure.UNREADABLE, "private response")
            val actual =
                assertFailsWith<ServerDiscoveryException> {
                    awaitNativeEnrollmentServer<String> { Result.failure(original) }
                }
            assertSame(original, actual)
            assertTrue(actual.suppressed.isEmpty())
        }

    @Test
    fun successfulDiscoveryReturnsDescriptor() =
        runBlocking {
            assertEquals("descriptor", awaitNativeEnrollmentServer { Result.success("descriptor") })
        }
}
