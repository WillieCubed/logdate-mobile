package app.logdate.client.database.dao.sync

/** Retained work includes policy pauses and formats that require an app update. */
data class DownloadRecoveryCounts(
    val retained: Int = 0,
    val retryable: Int = 0,
    /** Due or explicitly released rows are ready to try, even if their previous attempt failed. */
    val failed: Int = 0,
)
