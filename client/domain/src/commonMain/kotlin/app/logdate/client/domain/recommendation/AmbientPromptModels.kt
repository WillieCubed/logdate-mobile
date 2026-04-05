package app.logdate.client.domain.recommendation

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

enum class AmbientPromptFamily {
    CAPTURE_NUDGES,
    DRAFT_RESCUE,
    MEMORY_RECALL,
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
}

data class AmbientPromptCandidate(
    val family: AmbientPromptFamily,
    val score: Int,
    val dedupeKey: String,
    val payload: AmbientPromptPayload,
)
