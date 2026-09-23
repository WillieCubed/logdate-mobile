package app.logdate.feature.core.settings.account.hosting

import app.logdate.feature.core.settings.account.ConnectedServer
import app.logdate.feature.core.settings.account.ConnectedServerInfo
import app.logdate.feature.core.settings.account.ServerHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HostingViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val cloud =
        ConnectedServerInfo(
            origin = "https://cloud.logdate.app",
            displayName = "LogDate Cloud",
            isLogDateCloud = true,
            publishesIdentityChanges = false,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a reachable server shows its version`() =
        runTest {
            val viewModel = HostingViewModel(FakeServer(cloud, ServerHealth.Reachable("1.4.0")))

            assertEquals(HostingUiState(server = cloud, health = ServerHealth.Reachable("1.4.0")), viewModel.state.value)
        }

    @Test
    fun `an unreachable server says so and can be checked again`() =
        runTest {
            val server = FakeServer(cloud, ServerHealth.Unreachable)
            val viewModel = HostingViewModel(server)

            assertEquals(ServerHealth.Unreachable, viewModel.state.value.health)

            server.health = ServerHealth.Reachable(null)
            viewModel.checkAgain()

            assertEquals(ServerHealth.Reachable(null), viewModel.state.value.health)
            assertEquals(2, server.checks)
        }

    private class FakeServer(
        info: ConnectedServerInfo,
        var health: ServerHealth,
    ) : ConnectedServer {
        var checks = 0
        override val info: Flow<ConnectedServerInfo> = MutableStateFlow(info)

        override suspend fun refresh(): ServerHealth {
            checks++
            return health
        }
    }
}
