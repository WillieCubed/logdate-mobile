package app.logdate.client.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val roomDatabaseDispatcher: CoroutineDispatcher = Dispatchers.Default
