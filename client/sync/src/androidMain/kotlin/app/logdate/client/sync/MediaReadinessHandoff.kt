package app.logdate.client.sync

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield

internal data class MediaReadinessWork(
    val id: String,
    val state: WorkInfo.State,
    val hasMobileDataConsent: Boolean = false,
    val recoveredAllMedia: Boolean = false,
)

internal sealed interface MediaReadinessResult {
    data object Completed : MediaReadinessResult

    data class Replace(
        val snapshot: List<MediaReadinessWork>,
    ) : MediaReadinessResult
}

/** Keep the readiness wake pending until writers and previously granted requests have yielded. */
internal suspend fun awaitMediaReadinessHandoff(work: Flow<List<MediaReadinessWork>>): MediaReadinessResult {
    val observed = mutableSetOf<String>()
    var completed = false
    val snapshot =
        work.first { requests ->
            val active = requests.filter { it.state == WorkInfo.State.RUNNING || (!it.state.isFinished && it.hasMobileDataConsent) }
            observed += active.map { it.id }
            if (active.isNotEmpty()) {
                false
            } else {
                completed = observed.isNotEmpty() &&
                    requests.none { !it.state.isFinished } &&
                    observed.all { id -> requests.any { it.id == id && it.state == WorkInfo.State.SUCCEEDED && it.recoveredAllMedia } }
                true
            }
        }
    return if (completed) MediaReadinessResult.Completed else MediaReadinessResult.Replace(snapshot)
}

internal suspend fun resumeMediaReadiness(
    work: Flow<List<MediaReadinessWork>>,
    isCurrent: suspend () -> Boolean,
    replace: suspend (List<MediaReadinessWork>) -> Boolean,
) {
    while (true) {
        check(isCurrent()) { "Media recovery scope changed" }
        val handoff = awaitMediaReadinessHandoff(work)
        check(isCurrent()) { "Media recovery scope changed" }
        when (handoff) {
            MediaReadinessResult.Completed -> return
            is MediaReadinessResult.Replace -> if (replace(handoff.snapshot)) return
        }
        yield()
    }
}
