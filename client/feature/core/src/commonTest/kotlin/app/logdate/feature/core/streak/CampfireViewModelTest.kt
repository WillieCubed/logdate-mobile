package app.logdate.feature.core.streak

import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CampfireViewModelTest {
    private val flagEnabled = MutableStateFlow(true)
    private val campfire = MutableStateFlow<CampfireState?>(null)

    private val burningState =
        CampfireState(
            phase = FirePhase.BURNING,
            loggedToday = true,
            runDays = 12,
            size = FireSize.CAMPFIRE,
            longestRunDays = 40,
            totalDaysJournaled = 210,
            isRekindled = true,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CampfireViewModel(campfireFlow = campfire, campfireEnabledFlow = flagEnabled)

    @Test
    fun `the campfire is hidden while its flag is off`() =
        runTest {
            flagEnabled.value = false
            campfire.value = burningState
            val viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.presentation.collect {} }

            assertNull(viewModel.presentation.value)
        }

    @Test
    fun `the flag state is exposed for surfaces that show the campfire without a fire`() =
        runTest {
            flagEnabled.value = false
            val viewModel = createViewModel()
            assertNull(viewModel.isCampfireEnabled.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isCampfireEnabled.collect {} }
            assertEquals(false, viewModel.isCampfireEnabled.value)

            flagEnabled.value = true

            assertEquals(true, viewModel.isCampfireEnabled.value)
        }

    @Test
    fun `the campfire is hidden when there is no fire to show`() =
        runTest {
            val viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.presentation.collect {} }

            assertNull(viewModel.presentation.value)
        }

    @Test
    fun `the campfire state maps onto its presentation`() =
        runTest {
            campfire.value = burningState
            val viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.presentation.collect {} }

            assertEquals(
                CampfirePresentation(
                    phase = CampfirePhase.BURNING,
                    loggedToday = true,
                    runDays = 12,
                    size = CampfireSize.CAMPFIRE,
                    longestRunDays = 40,
                    totalDaysJournaled = 210,
                    isRekindled = true,
                ),
                viewModel.presentation.value,
            )
        }

    @Test
    fun `every fire phase and size has a presentation`() {
        FirePhase.entries.forEach { phase ->
            assertEquals(
                phase.name,
                burningState
                    .copy(phase = phase)
                    .toPresentation()
                    .phase.name,
            )
        }
        FireSize.entries.forEach { size ->
            assertEquals(
                size.name,
                burningState
                    .copy(size = size)
                    .toPresentation()
                    .size
                    ?.name,
            )
        }
    }
}
