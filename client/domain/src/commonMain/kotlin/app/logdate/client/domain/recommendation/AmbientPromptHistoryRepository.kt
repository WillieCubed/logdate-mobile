package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface AmbientPromptHistoryRepository {
    suspend fun canShow(candidate: AmbientPromptCandidate): Boolean

    suspend fun recordShown(
        candidate: AmbientPromptCandidate,
        shownAt: Instant = Clock.System.now(),
    )
}

class DefaultAmbientPromptHistoryRepository(
    private val keyValueStorage: KeyValueStorage,
    private val now: () -> Instant = { Clock.System.now() },
) : AmbientPromptHistoryRepository {
    override suspend fun canShow(candidate: AmbientPromptCandidate): Boolean {
        val currentTime = now()
        val records = loadRecords(currentTime)
        if (records.any { record -> currentTime - record.shownAt < GLOBAL_SUPPRESSION_WINDOW }) {
            return false
        }

        val policy = candidate.family.policy()
        val familyRecords = records.filter { it.family == candidate.family }
        if (familyRecords.any { record -> record.dedupeKey == candidate.dedupeKey }) {
            return false
        }
        if (familyRecords.any { record -> currentTime - record.shownAt < policy.minGap }) {
            return false
        }

        val today = currentTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val shownToday =
            familyRecords.count { record ->
                record.shownAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == today
            }
        return shownToday < policy.maxPerDay
    }

    override suspend fun recordShown(
        candidate: AmbientPromptCandidate,
        shownAt: Instant,
    ) {
        val records = loadRecords(shownAt).toMutableList()
        records += AmbientPromptHistoryRecord(candidate.family, candidate.dedupeKey, shownAt)
        persist(records)
    }

    private suspend fun loadRecords(referenceTime: Instant): List<AmbientPromptHistoryRecord> {
        val stored = keyValueStorage.getString(KEY_PROMPT_HISTORY) ?: return emptyList()
        val decoded =
            runCatching { json.decodeFromString<List<AmbientPromptHistoryRecord>>(stored) }
                .getOrDefault(emptyList())
        val cutoff = referenceTime - HISTORY_RETENTION
        return decoded.filter { it.shownAt >= cutoff }
    }

    private suspend fun persist(records: List<AmbientPromptHistoryRecord>) {
        keyValueStorage.putString(KEY_PROMPT_HISTORY, json.encodeToString(records.sortedBy(AmbientPromptHistoryRecord::shownAt)))
    }

    private fun AmbientPromptFamily.policy(): AmbientPromptPolicy =
        when (this) {
            AmbientPromptFamily.CAPTURE_NUDGES ->
                AmbientPromptPolicy(
                    maxPerDay = 2,
                    minGap = 3.hours,
                )
            AmbientPromptFamily.DRAFT_RESCUE,
            AmbientPromptFamily.MEMORY_RECALL,
            -> AmbientPromptPolicy(maxPerDay = 1, minGap = 24.hours)
            AmbientPromptFamily.EVENT_NUDGE ->
                // Up to three event nudges per day with at least six hours between them. The
                // dedupeKey is `event:<id>` so the same event can never fire twice regardless
                // of these limits.
                AmbientPromptPolicy(maxPerDay = 3, minGap = 6.hours)
        }

    companion object {
        private const val KEY_PROMPT_HISTORY = "ambient_prompt_history"
        private val json = Json { ignoreUnknownKeys = true }
        private val GLOBAL_SUPPRESSION_WINDOW: Duration = 1.hours
        private val HISTORY_RETENTION: Duration = 14.days
    }
}

private data class AmbientPromptPolicy(
    val maxPerDay: Int,
    val minGap: Duration,
)

@Serializable
private data class AmbientPromptHistoryRecord(
    val family: AmbientPromptFamily,
    val dedupeKey: String,
    @Serializable(with = InstantAsLongSerializer::class)
    val shownAt: Instant,
)
