package app.logdate.client.sync.v2.window

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMomentRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Resolved and dismissed marks must keep publishing after leaving the open-mark list. */
class PhoneWindowMoments(
    dao: MarkedMomentDao,
    repository: MarkedMomentRepository,
) {
    val deletions = dao.observeDeleted().map { rows -> rows.associate { it.id to checkNotNull(it.deletedAt) } }

    val changes =
        combine(MarkState.entries.map { dao.observeByState(it.name) }) { snapshots ->
            snapshots
                .flatMap { it }
                .map { it.id }
                .distinct()
                .mapNotNull { repository.getMark(it) }
        }
}
