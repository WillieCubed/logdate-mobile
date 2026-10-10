package app.logdate.wear.v2.recorder.gesture

import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Cancel
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Claimed
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Down
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Move
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Timeout
import app.logdate.wear.v2.recorder.gesture.RecordGestureEvent.Up
import app.logdate.wear.v2.recorder.gesture.RecordGesturePhase.Holding
import app.logdate.wear.v2.recorder.gesture.RecordGesturePhase.Idle
import app.logdate.wear.v2.recorder.gesture.RecordGesturePhase.Pressed
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Every phase of the record gesture against every event, with a lock 100 pixels above a finger
 * that went down at y = 200.
 */
class RecordGestureMachineTest {
    private val machine = RecordGestureMachine(lockDistancePx = 100f)
    private val pressed = Pressed(downY = 200f)
    private val holding = Holding(downY = 200f, lockProgress = 0f)

    @Test
    fun `a finger down presses`() = assertStep(Idle, Down(200f), pressed, RecordGestureSignal.Pressed)

    @Test
    fun `only a finger down leaves idle`() {
        listOf(Move(10f), Claimed, Timeout, Up, Cancel).forEach { event -> assertStep(Idle, event, Idle) }
    }

    @Test
    fun `lifting before the timeout is a tap`() = assertStep(pressed, Up, Idle, RecordGestureSignal.Tapped)

    @Test
    fun `the pager claiming the touch yields`() = assertStep(pressed, Claimed, Idle, RecordGestureSignal.Yielded)

    @Test
    fun `a touch the system cancels before the timeout yields`() = assertStep(pressed, Cancel, Idle, RecordGestureSignal.Yielded)

    @Test
    fun `the timeout commits to hold to talk`() = assertStep(pressed, Timeout, holding, RecordGestureSignal.Committed)

    @Test
    fun `moving before the timeout changes nothing`() = assertStep(pressed, Move(120f), pressed)

    @Test
    fun `a second finger before the timeout changes nothing`() = assertStep(pressed, Down(10f), pressed)

    @Test
    fun `sliding up reports how far the lock is`() =
        assertStep(holding, Move(150f), holding.copy(lockProgress = 0.5f), RecordGestureSignal.LockProgress(0.5f))

    @Test
    fun `sliding down reports nothing`() = assertStep(holding, Move(260f), holding)

    @Test
    fun `progress follows the finger back down`() =
        assertStep(holding.copy(lockProgress = 0.5f), Move(180f), holding.copy(lockProgress = 0.2f), RecordGestureSignal.LockProgress(0.2f))

    @Test
    fun `sliding the whole distance locks`() =
        assertStep(holding, Move(100f), RecordGesturePhase.Locked, RecordGestureSignal.LockProgress(1f), RecordGestureSignal.Locked)

    @Test
    fun `sliding past the lock locks once`() =
        assertStep(holding, Move(-50f), RecordGesturePhase.Locked, RecordGestureSignal.LockProgress(1f), RecordGestureSignal.Locked)

    @Test
    fun `lifting the finger while holding releases`() = assertStep(holding, Up, Idle, RecordGestureSignal.Released)

    @Test
    fun `a touch the system cancels while holding releases so the recording is saved`() =
        assertStep(holding, Cancel, Idle, RecordGestureSignal.Released)

    @Test
    fun `a hold ignores claims and timeouts and second fingers`() {
        listOf(Claimed, Timeout, Down(0f)).forEach { event -> assertStep(holding, event, holding) }
    }

    @Test
    fun `lifting after the lock lets the recording go on`() = assertStep(RecordGesturePhase.Locked, Up, Idle)

    @Test
    fun `a cancel after the lock lets the recording go on`() = assertStep(RecordGesturePhase.Locked, Cancel, Idle)

    @Test
    fun `a locked gesture ignores moves and claims and timeouts`() {
        listOf(Move(500f), Claimed, Timeout, Down(0f)).forEach { event ->
            assertStep(RecordGesturePhase.Locked, event, RecordGesturePhase.Locked)
        }
    }

    @Test
    fun `every phase answers every event`() {
        val phases = listOf(Idle, pressed, holding, holding.copy(lockProgress = 0.7f), RecordGesturePhase.Locked)
        val events = listOf(Down(200f), Move(150f), Claimed, Timeout, Up, Cancel)
        phases.forEach { phase -> events.forEach { event -> machine.step(phase, event) } }
    }

    @Test
    fun `the lock distance must be positive`() {
        assertFailsWith<IllegalArgumentException> { RecordGestureMachine(lockDistancePx = 0f) }
    }

    private fun assertStep(
        from: RecordGesturePhase,
        event: RecordGestureEvent,
        to: RecordGesturePhase,
        vararg signals: RecordGestureSignal,
    ) {
        assertEquals(RecordGestureStep(to, signals.toList()), machine.step(from, event))
    }
}
