package app.logdate.client.domain.recommendation

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class AmbientPromptFamily {
    CAPTURE_NUDGES,
    DRAFT_RESCUE,
    MEMORY_RECALL,
    EVENT_NUDGE,
}

enum class AmbientPromptTriggerContext {
    PERIODIC,
    MORNING_SCHEDULE,
    EVENING_SCHEDULE,
}

enum class AmbientCaptureNudgeStyle {
    MORNING,
    EVENING,
    NOVEL_PLACE,
}

sealed interface AmbientPromptPayload {
    data class CaptureNudge(
        val style: AmbientCaptureNudgeStyle,
        val placeName: String? = null,
    ) : AmbientPromptPayload

    data class DraftRescue(
        val draftId: Uuid,
    ) : AmbientPromptPayload

    data class MemoryRecall(
        val date: LocalDate,
        val summary: String,
    ) : AmbientPromptPayload

    /**
     * A nudge for an event that's about to start. The user is encouraged to capture something
     * for the event before it begins.
     *
     * @property eventId The id of the event the nudge points at.
     * @property title The event's display title, used as the notification title.
     * @property startTime When the event starts; the notification body shows this in the
     *   user's local time.
     */
    data class EventNudge(
        val eventId: Uuid,
        val title: String,
        val startTime: Instant,
    ) : AmbientPromptPayload
}

data class AmbientPromptCandidate(
    val family: AmbientPromptFamily,
    val score: Int,
    val dedupeKey: String,
    val payload: AmbientPromptPayload,
)
