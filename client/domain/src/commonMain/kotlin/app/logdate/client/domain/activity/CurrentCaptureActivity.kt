package app.logdate.client.domain.activity

import app.logdate.client.repository.journals.CaptureActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What a capture made now records as the person's activity. */
fun interface CaptureActivitySource {
    suspend fun now(): CaptureActivity?

    companion object {
        /** For devices and builds without activity awareness. */
        val None = CaptureActivitySource { null }
    }
}

/**
 * The activity a capture made now records: the walk, ride or trip under way, or the one whose
 * suggestion the person acted on in the last [SUGGESTED_CAPTURE_WINDOW]. Sleep is never recorded,
 * and nothing is while activity awareness is off.
 *
 * A capture never waits on this for long: when the agreed activity has not been worked out within
 * [LOOKUP_LIMIT], the capture records none.
 */
class CurrentCaptureActivity(
    private val agreed: Flow<AgreedActivity>,
    private val enabled: Flow<Boolean>,
    private val outcomes: Flow<List<SuggestionOutcome>> = flowOf(emptyList()),
    private val clock: Clock = Clock.System,
) : CaptureActivitySource {
    override suspend fun now(): CaptureActivity? =
        withTimeoutOrNull(LOOKUP_LIMIT) {
            if (enabled.firstOrNull() != true) return@withTimeoutOrNull null
            agreed
                .firstOrNull()
                ?.current
                ?.kind
                ?.toCaptureActivity() ?: actedOnSuggestion()
        }

    private suspend fun actedOnSuggestion(): CaptureActivity? {
        val acted =
            outcomes.firstOrNull().orEmpty().lastOrNull {
                it.kind == SuggestionKind.AFTER_ACTIVITY && it.result == SuggestionResult.ACTED_ON
            }
                ?: return null
        if (clock.now() - acted.at > SUGGESTED_CAPTURE_WINDOW) return null
        return acted.activityKind.toCaptureActivity()
    }

    companion object {
        val SUGGESTED_CAPTURE_WINDOW = 30.minutes
        val LOOKUP_LIMIT = 2.seconds
    }
}

private fun ActivityKind.toCaptureActivity(): CaptureActivity? =
    when (this) {
        ActivityKind.MOVING -> CaptureActivity.MOVING
        ActivityKind.IN_VEHICLE -> CaptureActivity.IN_VEHICLE
        ActivityKind.SLEEP -> null
    }
