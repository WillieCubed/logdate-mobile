package app.logdate.client.sync

internal enum class SyncNotificationPhase { CHECKING, BACKING_UP }

internal fun syncNotificationPhase(total: Int?): SyncNotificationPhase =
    if (total != null && total > 0) SyncNotificationPhase.BACKING_UP else SyncNotificationPhase.CHECKING
