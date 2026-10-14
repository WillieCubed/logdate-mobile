package app.logdate.ui.timeline

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlin.time.Instant

/** Pending captures participate in the day's sequence without becoming invented notes. */
fun integratePendingMoments(
    moments: List<MomentUiState>,
    marks: List<MarkedMoment>,
): List<MomentUiState> {
    val pending = marks.filter { it.state == MarkState.OPEN }.distinctBy { it.id }
    if (pending.isEmpty()) return moments
    return (
        moments +
            pending.map { mark ->
                MomentUiState(
                    id = "mark-${mark.id}",
                    label = "",
                    capturedAt = mark.capturedAt,
                    pendingMark = mark,
                    capturedOnWatch = true,
                )
            }
    ).sortedBy { it.capturedAt ?: Instant.DISTANT_FUTURE }
}
