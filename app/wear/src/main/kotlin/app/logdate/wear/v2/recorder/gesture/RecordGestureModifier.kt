package app.logdate.wear.v2.recorder.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** The measurements of Record's gesture. */
object RecordGestureDefaults {
    /** How far up the finger slides while holding to lock the recording. */
    val LockDistance: Dp = 56.dp
}

/**
 * Hold to talk, slide up to lock, and tap for a recording that is locked from the start.
 *
 * While [armed], the touch is watched on the Initial pass, before the element's own click and
 * before the pager above it, and the down is consumed so the click never fires. Until the system
 * long-press timeout the gesture only watches: a drag yields, whether a list or pager takes it or it
 * only travels past the touch slop, so a swipe across the button never records. After the timeout it
 * consumes every event, so nothing scrolls, and a finger that slides sideways off the element does
 * not end the recording until it lifts.
 *
 * While not [armed], touches pass through and the element's click does what the button shows, such
 * as Stop. Either way this modifier owns the element's accessibility: TalkBack announces
 * [description], a click runs [onClick], and while armed an action named [startLabel] starts a
 * locked recording.
 */
fun Modifier.recordGesture(
    armed: Boolean,
    listener: RecordGestureListener,
    description: String,
    startLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    lockDistance: Dp = RecordGestureDefaults.LockDistance,
): Modifier = this then RecordGestureElement(armed, listener, description, startLabel, onClick, enabled, lockDistance)

private data class RecordGestureElement(
    val armed: Boolean,
    val listener: RecordGestureListener,
    val description: String,
    val startLabel: String,
    val onClick: () -> Unit,
    val enabled: Boolean,
    val lockDistance: Dp,
) : ModifierNodeElement<RecordGestureNode>() {
    override fun create(): RecordGestureNode = RecordGestureNode(armed, listener, description, startLabel, onClick, enabled, lockDistance)

    override fun update(node: RecordGestureNode) {
        node.update(armed, listener, description, startLabel, onClick, enabled, lockDistance)
    }
}

private class RecordGestureNode(
    private var armed: Boolean,
    private var listener: RecordGestureListener,
    private var description: String,
    private var startLabel: String,
    private var clickAction: () -> Unit,
    private var enabled: Boolean,
    private var lockDistance: Dp,
) : DelegatingNode(),
    PointerInputModifierNode,
    SemanticsModifierNode {
    private val pointerInput = delegate(SuspendingPointerInputModifierNode { detectRecordGesture() })

    override val shouldClearDescendantSemantics: Boolean
        get() = true

    fun update(
        armed: Boolean,
        listener: RecordGestureListener,
        description: String,
        startLabel: String,
        onClick: () -> Unit,
        enabled: Boolean,
        lockDistance: Dp,
    ) {
        val semanticsChanged =
            armed != this.armed || description != this.description || startLabel != this.startLabel || enabled != this.enabled
        this.armed = armed
        this.listener = listener
        this.description = description
        this.startLabel = startLabel
        this.clickAction = onClick
        this.enabled = enabled
        this.lockDistance = lockDistance
        if (semanticsChanged) invalidateSemantics()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        pointerInput.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        pointerInput.onCancelPointerInput()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = Role.Button
        contentDescription = description
        if (!enabled) disabled()
        onClick(label = if (armed) startLabel else null) {
            clickAction()
            true
        }
        if (!armed) return
        customActions =
            listOf(
                CustomAccessibilityAction(startLabel) {
                    listener.startLocked()
                    true
                },
            )
    }

    private suspend fun PointerInputScope.detectRecordGesture() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!armed || !enabled) return@awaitEachGesture
            down.consume()
            val tracker = GestureTracker(RecordGestureMachine(lockDistance.toPx()), listener)
            tracker.send(RecordGestureEvent.Down(down.position.y))
            val beforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitDecision(down.id, down.position) }
            tracker.send(beforeTimeout ?: RecordGestureEvent.Timeout)
            if (tracker.phase !is RecordGesturePhase.Holding) return@awaitEachGesture
            followAfterTimeout(down.id, tracker)
        }
    }
}

/** The phase of one gesture, stepped through [machine], with every signal passed to [listener]. */
private class GestureTracker(
    private val machine: RecordGestureMachine,
    private val listener: RecordGestureListener,
) {
    var phase: RecordGesturePhase = RecordGesturePhase.Idle
        private set

    fun send(event: RecordGestureEvent) {
        val step = machine.step(phase, event)
        phase = step.phase
        step.signals.forEach(listener::deliver)
    }
}

/**
 * Before the timeout: an up ends the wait (a cancelled touch arrives as an up that is already
 * consumed). A move a list or pager consumed on its Main pass shows up consumed on the Final pass,
 * and a move past the touch slop from [downAt] is a drag even when nothing scrolls; both yield.
 */
private suspend fun AwaitPointerEventScope.awaitDecision(
    pointer: PointerId,
    downAt: Offset,
): RecordGestureEvent {
    while (true) {
        val initial = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointer } ?: continue
        if (!initial.pressed) return if (initial.isConsumed) RecordGestureEvent.Cancel else RecordGestureEvent.Up
        val final = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == pointer } ?: continue
        if (final.isConsumed) return RecordGestureEvent.Claimed
        if ((final.position - downAt).getDistance() > viewConfiguration.touchSlop) return RecordGestureEvent.Claimed
    }
}

/** After the timeout: every event is consumed here, so nothing below or above sees the touch. */
private suspend fun AwaitPointerEventScope.followAfterTimeout(
    pointer: PointerId,
    tracker: GestureTracker,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointer }
        if (change == null || !change.pressed) {
            val cancelled = change?.isConsumed == true
            event.changes.forEach { it.consume() }
            tracker.send(if (cancelled) RecordGestureEvent.Cancel else RecordGestureEvent.Up)
            return
        }
        event.changes.forEach { it.consume() }
        tracker.send(RecordGestureEvent.Move(change.position.y))
    }
}
