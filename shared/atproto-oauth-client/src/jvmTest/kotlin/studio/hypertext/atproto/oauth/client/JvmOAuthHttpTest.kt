package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.SocketTimeoutException
import javax.net.SocketFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmOAuthHttpTest {
    @Test
    fun refusesNonPublicAddresses() {
        val refused =
            listOf(
                "0.0.0.0",
                "10.1.2.3",
                "100.64.0.1",
                "127.0.0.1",
                "169.254.169.254",
                "172.16.0.1",
                "172.31.255.255",
                "192.0.0.1",
                "192.0.2.1",
                "192.168.1.1",
                "198.18.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "224.0.0.1",
                "255.255.255.255",
                "::",
                "::1",
                "fc00::1",
                "fd12:3456::1",
                "fe80::1",
                "fec0::1",
                "ff02::1",
                "2001:db8::1",
                "::ffff:127.0.0.1",
                "::ffff:169.254.169.254",
                "::10.0.0.1",
                "64:ff9b::a9fe:a9fe",
                "2002:0a00:0001::1",
                "192.88.99.1",
                "64:ff9b:1::808:808",
                "::ffff:0:a9fe:a9fe",
                "::ffff:0:7f00:1",
                "2001:0:4136:e378:8000:63bf:3fff:fdd2",
                "100::1",
                "2001:2::1",
            )

        refused.forEach { address ->
            assertFalse(JvmOAuthHttp.isPublicAddress(InetAddress.getByName(address)), "$address should be refused")
        }
    }

    @Test
    fun allowsPublicAddresses() {
        listOf(
            "1.1.1.1",
            "8.8.8.8",
            "172.32.0.1",
            "100.128.0.1",
            "2606:4700:4700::1111",
            "64:ff9b::808:808",
            "2002:0808:0808::1",
            "::ffff:0:808:808",
            "2001:4860:4860::8888",
        ).forEach { address ->
            assertTrue(JvmOAuthHttp.isPublicAddress(InetAddress.getByName(address)), "$address should be allowed")
        }
    }

    @Test
    fun publicInternetClientNeverConnectsToLoopback() = assertNeverConnectsToLoopback { JvmOAuthHttp.publicInternetClient() }

    @Test
    fun callerConfigurationCannotTurnTheFilterOff() =
        assertNeverConnectsToLoopback { server ->
            JvmOAuthHttp.publicInternetClient {
                engine {
                    // Both would route around the filter if they were applied last.
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort))
                    config { socketFactory(SocketFactory.getDefault()) }
                }
            }
        }

    private fun assertNeverConnectsToLoopback(newClient: (ServerSocket) -> HttpClient) =
        runTest {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = 200
                val client = newClient(server)

                listOf("http://127.0.0.1:${server.localPort}/", "http://localhost:${server.localPort}/").forEach { url ->
                    val error = assertFailsWith<Exception> { client.get(url) }

                    val messages = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.toList()
                    assertTrue(messages.any { "non-public address" in it }, "unexpected failure for $url: $messages")
                }
                assertFailsWith<SocketTimeoutException>("no connection reached the server") { server.accept() }
                client.close()
            }
        }
}
