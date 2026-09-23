package app.logdate.feature.core.settings.ui

import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.client.networking.ServerHealthInfo
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerPasskeyConfig
import app.logdate.shared.model.ServerProtocolFeature
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerConfigurationCoordinatorTest {
    @Test
    fun `validating and saving local server persists address`() =
        runTest {
            val configRepository = DefaultLogDateConfigRepository()
            val coordinator =
                ServerConfigurationCoordinator(
                    serverHealthChecker = FakeServerHealthChecker(),
                    serverDiscoveryClient = FakeServerDiscoveryClient(),
                    configRepository = configRepository,
                )

            val result = coordinator.validateAndSaveCustomServer("http://10.0.2.2:8765")

            assertTrue(result.isSuccess)
            assertTrue(configRepository.backendUrl.value.startsWith("http://10.0.2.2:8765"))
            assertEquals("http://10.0.2.2:8765", configRepository.serverDescriptor.value?.serverOrigin)
        }

    @Test
    fun `custom server without canonical owner binding is not persisted`() =
        runTest {
            val configRepository = DefaultLogDateConfigRepository()
            val coordinator =
                ServerConfigurationCoordinator(
                    serverHealthChecker = FakeServerHealthChecker(),
                    serverDiscoveryClient = FakeServerDiscoveryClient(protocolFeatures = emptyList()),
                    configRepository = configRepository,
                )

            val result = coordinator.validateAndSaveCustomServer("https://example.test")

            assertTrue(result.isFailure)
            assertEquals(DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL, configRepository.backendUrl.value)
            assertEquals(null, configRepository.serverDescriptor.value)
        }

    @Test
    fun `checking a usable server changes nothing`() =
        runTest {
            val configRepository = DefaultLogDateConfigRepository()
            val coordinator = coordinator(configRepository = configRepository)

            val check = assertIs<ServerCheck.Usable>(coordinator.check("journal.example.com/"))

            assertEquals("https://journal.example.com", check.server.origin)
            assertEquals("1.0.0", check.server.version)
            assertEquals(DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL, configRepository.backendUrl.value)
            assertEquals(null, configRepository.serverDescriptor.value)
        }

    @Test
    fun `a server that does not answer is unreachable`() =
        runTest {
            val check = coordinator(healthChecker = FakeServerHealthChecker(reachable = false)).check("https://journal.example.com")

            assertEquals(ServerCheck.Unusable(ServerProblem.UNREACHABLE), check)
        }

    @Test
    fun `a server that answers but does not describe itself is not a LogDate server`() =
        runTest {
            val check = coordinator(discoveryClient = FakeServerDiscoveryClient(describesItself = false)).check("https://example.com")

            assertEquals(ServerCheck.Unusable(ServerProblem.NOT_LOGDATE), check)
        }

    @Test
    fun `a server without single-identity accounts is out of date`() =
        runTest {
            val check =
                coordinator(
                    discoveryClient = FakeServerDiscoveryClient(protocolFeatures = emptyList()),
                ).check("https://old.example.com")

            assertEquals(ServerCheck.Unusable(ServerProblem.OUT_OF_DATE), check)
        }

    @Test
    fun `a server without passkey sign-in or sync is out of date`() =
        runTest {
            val check =
                coordinator(discoveryClient = FakeServerDiscoveryClient(capabilities = listOf(ServerCapability.AUTH_PASSKEY)))
                    .check("https://old.example.com")

            assertEquals(ServerCheck.Unusable(ServerProblem.OUT_OF_DATE), check)
        }

    @Test
    fun `a server whose passkeys this device cannot use is reported as such`() =
        runTest {
            val check = coordinator(passkeysWorkWith = { rpId -> rpId.endsWith("logdate.app") }).check("https://journal.example.com")

            assertEquals(ServerCheck.Unusable(ServerProblem.PASSKEYS_UNAVAILABLE_HERE), check)
        }

    @Test
    fun `an address that is not a web address is rejected`() =
        runTest {
            assertEquals(ServerCheck.Unusable(ServerProblem.INVALID_ADDRESS), coordinator().check("   "))
            assertEquals(ServerCheck.Unusable(ServerProblem.INVALID_ADDRESS), coordinator().check("not a server"))
        }

    private fun coordinator(
        configRepository: DefaultLogDateConfigRepository = DefaultLogDateConfigRepository(),
        healthChecker: ServerHealthChecker = FakeServerHealthChecker(),
        discoveryClient: ServerDiscoveryClient = FakeServerDiscoveryClient(),
        passkeysWorkWith: (String) -> Boolean = { true },
    ) = ServerConfigurationCoordinator(
        serverHealthChecker = healthChecker,
        serverDiscoveryClient = discoveryClient,
        configRepository = configRepository,
        passkeysWorkWith = passkeysWorkWith,
    )

    private class FakeServerHealthChecker(
        private val reachable: Boolean = true,
    ) : ServerHealthChecker {
        override suspend fun checkServerHealth(baseUrl: String): Result<ServerHealthInfo> =
            if (reachable) {
                Result.success(ServerHealthInfo(status = "healthy", version = "1.0.0"))
            } else {
                Result.failure(IllegalStateException("Connection refused"))
            }
    }

    private class FakeServerDiscoveryClient(
        private val protocolFeatures: List<String> = listOf(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1),
        private val capabilities: List<ServerCapability> =
            listOf(ServerCapability.AUTH_PASSKEY, ServerCapability.SYNC_CONTENT, ServerCapability.SYNC_MEDIA),
        private val describesItself: Boolean = true,
    ) : ServerDiscoveryClient {
        override suspend fun discoverServer(serverOrigin: String): Result<ServerDescriptor> {
            if (!describesItself) return Result.failure(IllegalStateException("404 Not Found"))
            return Result.success(
                ServerDescriptor(
                    serverOrigin = serverOrigin,
                    apiBaseUrl = "${serverOrigin.trimEnd('/')}/api/v1",
                    deploymentKind = DeploymentKind.SELF_HOSTED,
                    displayName = "Test Server",
                    handleDomain = "example.com",
                    passkey = ServerPasskeyConfig(rpId = serverOrigin.substringAfter("://").substringBefore(':'), rpName = "Test Server"),
                    capabilities = capabilities,
                    protocolFeatures = protocolFeatures,
                ),
            )
        }
    }
}
