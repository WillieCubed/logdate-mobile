package app.logdate.client.domain.moments

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.timeline.GroupNotesByDayBoundsUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTimelineMarksUseCase(
    private val marks: MarkedMomentRepository,
    private val flags: FeatureFlagStore,
    private val groupDays: GroupNotesByDayBoundsUseCase,
) {
    operator fun invoke(): Flow<Map<LocalDate, List<MarkedMoment>>> =
        flags.observe(FeatureFlag.WATCH_EXPERIENCE_V2).flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(emptyMap())
            } else {
                marks.observeOpenMarks().map { open ->
                    val byId = open.associateBy { it.id }
                    val timestamps =
                        open.map {
                            JournalNote.Text(
                                uid = it.id,
                                creationTimestamp = it.capturedAt,
                                lastUpdated = it.lastUpdated,
                                content = "",
                            )
                        }
                    groupDays(timestamps).mapValues { (_, notes) -> notes.mapNotNull { byId[it.uid] } }
                }
            }
        }
}
