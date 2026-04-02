package app.logdate.feature.core.export

import android.content.Context
import androidx.work.ForegroundInfo
import app.logdate.client.feature.core.R
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.feature.core.notifications.DataTransferNotificationHelper

/**
 * Export-specific notification helper. Delegates all notification building
 * to [DataTransferNotificationHelper] and adds export-specific completion
 * content (file path).
 */
class ExportNotificationHelper(
    context: Context,
    workId: java.util.UUID,
) : DataTransferNotificationHelper(context, workId) {
    override val channelId = LogDateNotificationChannelKey.DATA_EXPORT.id
    override val notificationId = LogDateNotificationChannelKey.DATA_EXPORT.notificationId ?: 1003
    override val progressTitleResId = R.string.export_title_progress
    override val completeTitleResId = R.string.export_title_complete
    override val failedTitleResId = R.string.export_title_failed

    override fun createCompletionInfo(contentText: String): ForegroundInfo =
        super.createCompletionInfo(context.getString(R.string.export_text_path, contentText))
}
