package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.MemoryRecallData
import app.logdate.client.repository.journals.EntryDraft
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Existing draft and recall nudges, evaluated only inside the shared reminder consent gate. */
class SupplementalReminderSource(
    private val latestDraft: suspend () -> EntryDraft?,
    private val savedSince: suspend (Instant) -> Boolean,
    private val recall: suspend () -> MemoryRecallData?,
    private val history: AmbientPromptHistoryRepository,
) {
    suspend fun read(now: Instant): AmbientPromptCandidate? {
        val draft = latestDraft()
        if (draft != null && now - draft.updatedAt >= 6.hours && !savedSince(draft.updatedAt)) {
            val candidate =
                AmbientPromptCandidate(
                    AmbientPromptFamily.DRAFT_RESCUE,
                    100,
                    "draft:${draft.id}:${draft.updatedAt.toEpochMilliseconds()}",
                    AmbientPromptPayload.DraftRescue(draft.id),
                )
            if (history.canShow(candidate)) return candidate
        }
        val memory = recall() ?: return null
        val candidate =
            AmbientPromptCandidate(
                AmbientPromptFamily.MEMORY_RECALL,
                80,
                "recall:${memory.date}",
                AmbientPromptPayload.MemoryRecall(memory.date, memory.summary),
            )
        return candidate.takeIf { history.canShow(it) }
    }
}

fun AmbientPromptCandidate.reminderTrigger(): ReminderTrigger? =
    when {
        family == AmbientPromptFamily.DRAFT_RESCUE && payload is AmbientPromptPayload.DraftRescue -> ReminderTrigger.DRAFT_RESCUE
        family == AmbientPromptFamily.MEMORY_RECALL && payload is AmbientPromptPayload.MemoryRecall -> ReminderTrigger.MEMORY_RECALL
        else -> null
    }
