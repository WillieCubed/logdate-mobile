package app.logdate.wear.v2.mark

import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.health.HeartRateSampler
import app.logdate.wear.v2.haptics.RecordedHaptics
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.WatchPermission
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MarkViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private val marks = FakeMarkedMomentDao()
    private val haptics = RecordedHaptics()
    private val granted = mutableSetOf(WatchPermission.HEART_RATE)
    private val asked = mutableSetOf<WatchPermission>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `marking saves at once and confirms it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.mark()
            runCurrent()

            assertEquals(MarkFeedback.MARKED, viewModel.feedback.value)
            assertEquals("momentMarked", haptics.played.first())
            assertEquals(1, marks.rows.size)
        }

    @Test
    fun `the heart rate follows on the application scope`() =
        runTest {
            val viewModel = viewModel()

            viewModel.mark()
            runCurrent()

            assertEquals(68, savedMark().heartRateBpm)
        }

    @Test
    fun `a watch that cannot read a heart rate still marks the moment`() =
        runTest {
            val viewModel = viewModel(sensorBpm = null)

            viewModel.mark()
            runCurrent()

            assertEquals(MarkFeedback.MARKED, viewModel.feedback.value)
            assertEquals(listOf("momentMarked"), haptics.played)
            assertNull(savedMark().heartRateBpm)
        }

    @Test
    fun `the first mark asks for the heart rate once and is filled in after the answer`() =
        runTest {
            granted -= WatchPermission.HEART_RATE
            val viewModel = viewModel()
            val requests = collectRequests(viewModel)

            viewModel.mark()
            runCurrent()
            assertEquals(listOf(WatchPermission.HEART_RATE), requests)
            assertNull(savedMark().heartRateBpm)

            granted += WatchPermission.HEART_RATE
            viewModel.onPermissionResult(WatchPermission.HEART_RATE)
            runCurrent()
            assertEquals(68, savedMark().heartRateBpm)

            granted -= WatchPermission.HEART_RATE
            viewModel.mark()
            runCurrent()
            assertEquals(listOf(WatchPermission.HEART_RATE), requests)
        }

    @Test
    fun `a mark that cannot be saved says so`() =
        runTest {
            marks.failure = IllegalStateException("database closed")
            val viewModel = viewModel()

            viewModel.mark()
            runCurrent()

            assertEquals(MarkFeedback.FAILED, viewModel.feedback.value)
            assertEquals(listOf("actionRejected"), haptics.played)
        }

    @Test
    fun `dismissing clears the confirmation`() =
        runTest {
            val viewModel = viewModel()
            viewModel.mark()
            runCurrent()

            viewModel.dismissFeedback()

            assertEquals(MarkFeedback.NONE, viewModel.feedback.value)
        }

    private fun savedMark() = marks.rows.values.single()

    private fun viewModel(sensorBpm: Int? = 68): MarkViewModel {
        val markMoment =
            MarkMomentUseCase(
                store = MarkedMomentStore(marks, FakePeerDeliveryDao(), mockk<SyncMetadataService>(relaxed = true)),
                deviceId = { "watch-1" },
                place = { null },
                heartRate = HeartRateSampler { if (WatchPermission.HEART_RATE in granted) sensorBpm else null },
                timeZoneId = { "UTC" },
            )
        return MarkViewModel(
            markMoment = markMoment,
            haptics = haptics,
            permissions = PermissionPrompts(checker = { it in granted }, store = InMemoryPromptStore(asked)),
            applicationScope = applicationScope,
        )
    }

    private fun TestScope.collectRequests(viewModel: MarkViewModel): List<WatchPermission> {
        val requests = mutableListOf<WatchPermission>()
        backgroundScope.launch { viewModel.permissionRequests.collect { requests += it } }
        runCurrent()
        return requests
    }

    private class InMemoryPromptStore(
        private val asked: MutableSet<WatchPermission>,
    ) : PermissionPromptStore {
        override fun hasAsked(permission: WatchPermission): Boolean = permission in asked

        override fun markAsked(permission: WatchPermission) {
            asked += permission
        }
    }
}
