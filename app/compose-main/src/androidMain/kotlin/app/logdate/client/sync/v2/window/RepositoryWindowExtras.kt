package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.WatchWindowExtrasSource
import app.logdate.client.domain.watch.WindowExtraIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RepositoryWindowExtras(
    private val source: WatchWindowExtrasSource,
) : WindowExtras {
    override val changes: Flow<Unit> = source.observe().map { }

    override suspend fun ids(): WindowExtraIds = source.observe().first()
}
