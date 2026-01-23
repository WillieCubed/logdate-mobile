package app.logdate.client.database

/**
 * Clears all LogDate database tables using Room's Android APIs.
 */
actual suspend fun LogDateDatabase.clearAllLogDateTables() {
    clearAllTables()
}
