package app.logdate.client.database

/** The retained journal requires an app update; its files and keys must remain untouched. */
class NewerDatabaseVersionException(
    val retainedVersion: Int,
    val supportedVersion: Int,
) : IllegalStateException("Journal schema $retainedVersion requires a newer LogDate version than schema $supportedVersion")
