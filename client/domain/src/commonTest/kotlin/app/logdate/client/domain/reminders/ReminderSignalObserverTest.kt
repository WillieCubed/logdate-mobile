package app.logdate.client.domain.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSignalObserverTest {
    private val enabled = MutableStateFlow(false)
    private val places = MutableStateFlow(true)
    private val marks = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val locations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private var evaluations = 0

    @Test fun disabledRemindersDoNotCollectPrivateSignals() =
        runTest {
            ReminderSignalObserver(enabled, places, { marks }, { locations }, { evaluations++ }, backgroundScope).start()
            runCurrent()
            assertEquals(0, marks.subscriptionCount.value)
            assertEquals(0, locations.subscriptionCount.value)
            marks.emit(Unit)
            locations.emit(Unit)
            advanceTimeBy(60_001)
            assertEquals(0, evaluations)
        }

    @Test fun freshSignalBurstsQueueOneEvaluationWithoutRepeatingIdleData() =
        runTest {
            ReminderSignalObserver(enabled, places, { marks }, { locations }, { evaluations++ }, backgroundScope).start()
            enabled.value = true
            runCurrent()
            assertEquals(1, marks.subscriptionCount.value)
            assertEquals(1, locations.subscriptionCount.value)
            repeat(3) {
                marks.emit(Unit)
                locations.emit(Unit)
            }
            runCurrent()
            advanceTimeBy(30_001)
            assertEquals(1, evaluations)
            advanceTimeBy(60_000)
            assertEquals(1, evaluations)
            locations.emit(Unit)
            runCurrent()
            advanceTimeBy(30_000)
            assertEquals(2, evaluations)
        }

    @Test fun placesOptOutStopsLocationCollectionButKeepsMarkedMomentUpdates() =
        runTest {
            enabled.value = true
            ReminderSignalObserver(enabled, places, { marks }, { locations }, { evaluations++ }, backgroundScope).start()
            runCurrent()
            places.value = false
            runCurrent()
            assertEquals(0, locations.subscriptionCount.value)
            assertEquals(1, marks.subscriptionCount.value)
            locations.emit(Unit)
            advanceTimeBy(30_001)
            assertEquals(0, evaluations)
            marks.emit(Unit)
            runCurrent()
            advanceTimeBy(30_000)
            assertEquals(1, evaluations)
        }

    @Test fun optingOutDiscardsPendingChangesAndStopsBothSubscriptions() =
        runTest {
            enabled.value = true
            ReminderSignalObserver(enabled, places, { marks }, { locations }, { evaluations++ }, backgroundScope).start()
            runCurrent()
            marks.emit(Unit)
            runCurrent()
            enabled.value = false
            runCurrent()
            advanceTimeBy(30_001)
            assertEquals(0, evaluations)
            assertEquals(0, marks.subscriptionCount.value)
            assertEquals(0, locations.subscriptionCount.value)
        }

    @Test fun repeatedStartDoesNotDoubleSubscribeOrQueue() =
        runTest {
            enabled.value = true
            val observer = ReminderSignalObserver(enabled, places, { marks }, { locations }, { evaluations++ }, backgroundScope)
            observer.start()
            observer.start()
            runCurrent()
            assertEquals(1, marks.subscriptionCount.value)
            marks.emit(Unit)
            runCurrent()
            advanceTimeBy(30_001)
            assertEquals(1, evaluations)
        }

    @Test fun aFailedSignalStreamIsReopenedWithoutLosingTheOtherStream() =
        runTest {
            enabled.value = true
            var attempts = 0
            ReminderSignalObserver(enabled, places, {
                flow {
                    attempts++
                    if (attempts == 1) error("Temporary database read failure")
                    emit(Unit)
                }
            }, { locations }, { evaluations++ }, backgroundScope).start()
            runCurrent()
            locations.emit(Unit)
            runCurrent()
            advanceTimeBy(30_001)
            assertEquals(1, evaluations)
            advanceTimeBy(60_000)
            assertEquals(2, attempts)
            assertEquals(2, evaluations)
        }

    @Test fun cancellationIsNotRetriedAsADatabaseFailure() =
        runTest {
            enabled.value = true
            var attempts = 0
            ReminderSignalObserver(enabled, places, {
                flow {
                    attempts++
                    throw CancellationException("Signal collection cancelled")
                }
            }, { locations }, { evaluations++ }, backgroundScope).start()
            advanceTimeBy(120_001)
            assertEquals(1, attempts)
            assertEquals(0, evaluations)
        }
}
