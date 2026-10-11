package app.logdate.wear.v2.capture

import app.cash.turbine.test
import app.logdate.wear.capture.Feeling
import app.logdate.wear.sync.ReachablePhone
import app.logdate.wear.v2.haptics.RecordedHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureSheetViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val phone = MutableStateFlow<ReachablePhone?>(null)
    private val savedTexts = mutableListOf<String>()
    private val savedFeelings = mutableListOf<Feeling>()
    private var saves = true
    private val haptics = RecordedHaptics()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the phone camera is offered only while the phone is in reach`() =
        runTest {
            viewModel().uiState.test {
                assertFalse(awaitItem().phoneInReach)

                phone.value = ReachablePhone("Pixel")

                assertTrue(awaitItem().phoneInReach)
            }
        }

    @Test
    fun `written text is saved and confirmed`() =
        runTest {
            val viewModel = watched(viewModel())

            viewModel.saveText("Coffee with Sam")
            runCurrent()

            assertEquals(listOf("Coffee with Sam"), savedTexts)
            assertEquals(CaptureResult.SAVED, viewModel.uiState.value.result)
            assertEquals(listOf("saveSucceeded"), haptics.played)
        }

    @Test
    fun `blank text saves nothing`() =
        runTest {
            val viewModel = watched(viewModel())

            viewModel.saveText("  ")
            runCurrent()

            assertTrue(savedTexts.isEmpty())
            assertEquals(CaptureResult.NONE, viewModel.uiState.value.result)
        }

    @Test
    fun `a feeling is saved and the feelings close`() =
        runTest {
            val viewModel = watched(viewModel())
            viewModel.toggleFeelings()
            runCurrent()
            assertTrue(viewModel.uiState.value.choosingFeeling)

            viewModel.saveFeeling(Feeling.OK)
            runCurrent()

            assertEquals(listOf(Feeling.OK), savedFeelings)
            assertFalse(viewModel.uiState.value.choosingFeeling)
            assertEquals(CaptureResult.SAVED, viewModel.uiState.value.result)
        }

    @Test
    fun `a save that fails says so and the message can be dismissed`() =
        runTest {
            saves = false
            val viewModel = watched(viewModel())

            viewModel.saveText("Coffee with Sam")
            runCurrent()
            assertEquals(CaptureResult.FAILED, viewModel.uiState.value.result)
            assertEquals(listOf("actionRejected"), haptics.played)

            viewModel.dismissResult()
            runCurrent()
            assertEquals(CaptureResult.NONE, viewModel.uiState.value.result)
        }

    private fun viewModel() =
        CaptureSheetViewModel(
            textSaver = { text ->
                if (saves) savedTexts += text
                saves
            },
            feelingSaver = { feeling ->
                if (saves) savedFeelings += feeling
                saves
            },
            phoneReachability = { phone },
            haptics = haptics,
        )

    /** Collects [viewModel]'s state, as the sheet does, so its value follows each change. */
    private fun TestScope.watched(viewModel: CaptureSheetViewModel): CaptureSheetViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}
