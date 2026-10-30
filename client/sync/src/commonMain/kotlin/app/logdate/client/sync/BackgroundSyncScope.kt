package app.logdate.client.sync

import app.logdate.client.util.platformIODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** The scope background sync work is launched in. One failed sync leaves it able to run the next. */
internal fun backgroundSyncScope(dispatcher: CoroutineDispatcher = platformIODispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatcher)
