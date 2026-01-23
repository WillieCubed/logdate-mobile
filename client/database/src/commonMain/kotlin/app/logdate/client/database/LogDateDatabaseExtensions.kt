package app.logdate.client.database

/**
 * Clears all LogDate database tables on the current platform.
 */
expect suspend fun LogDateDatabase.clearAllLogDateTables()
