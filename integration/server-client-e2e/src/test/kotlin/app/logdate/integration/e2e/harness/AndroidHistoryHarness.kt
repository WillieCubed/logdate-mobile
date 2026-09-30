@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.harness

import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/** Loopback-only, ephemeral account fixture for sequential managed-device installations. */
object AndroidHistoryHarness {
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            require(args.size == 1) { "Supply a private temporary control directory" }
            val control = Path.of(args.single())
            Files.createDirectories(control)
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("android_history_${Random.nextInt(100000, 999999)}").data
                val words = DesktopCryptoManager().generateRecoveryPhrase()
                val fixture =
                    buildJsonObject {
                        put("owner", account.account.id.toString())
                        put("token", account.tokens.accessToken)
                        put("refreshToken", account.tokens.refreshToken)
                        put("origin", baseUrl.removeSuffix("/api/v1").replace("127.0.0.1", "10.0.2.2"))
                        put("recovery", words.joinToString(" "))
                    }.toString().toByteArray()
                val fixtureServer = HttpServer.create(InetSocketAddress("127.0.0.1", 18879), 0)
                var sourceInstallation: String? = null
                fixtureServer.createContext("/fixture") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, fixture.size.toLong())
                    exchange.responseBody.use { it.write(fixture) }
                }
                fixtureServer.createContext("/complete") { exchange ->
                    val installation = exchange.requestBody.use { it.readNBytes(1024).toString(Charsets.UTF_8) }
                    val phase = exchange.requestURI.query
                    val valid =
                        when (phase) {
                            "source" -> sourceInstallation == null && installation.isNotBlank()
                            "restore" -> sourceInstallation != null && installation != sourceInstallation && installation.isNotBlank()
                            else -> false
                        }
                    if (valid) {
                        if (phase == "source") sourceInstallation = installation
                        Files.writeString(control.resolve("$phase.complete"), "Verified separate installation: $installation\n")
                    }
                    exchange.sendResponseHeaders(if (valid) 204 else 409, -1)
                    exchange.close()
                }
                fixtureServer.start()
                Files.writeString(control.resolve("ready"), "Loopback fixture on 18879; API $baseUrl\n")
                try {
                    val deadline = System.currentTimeMillis() + 30 * 60 * 1000
                    while (!Files.exists(control.resolve("stop")) && System.currentTimeMillis() < deadline) delay(500)
                } finally {
                    fixtureServer.stop(0)
                    Files.deleteIfExists(control.resolve("ready"))
                }
            }
        }
}
