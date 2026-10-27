package app.logdate.client.database.dao.sync

/** Retained work includes policy pauses and formats that require an app update. */
data class DownloadRecoveryCounts(
    val retained: Int = 0,
    /** Supported work that can complete on this version, including rows waiting through retry backoff. */
    val retryable: Int = 0,
    /** Actual failures stay visible until applied or explicitly released to the waiting state. */
    val failed: Int = 0,
)
