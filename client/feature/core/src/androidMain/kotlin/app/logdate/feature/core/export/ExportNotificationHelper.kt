package app.logdate.feature.core.export

import android.content.Context
import androidx.work.ForegroundInfo
import app.logdate.client.feature.core.R
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
    override val channelId = "export_progress_channel"
    override val notificationId = 1001
    override val channelNameResId = R.string.export_channel_name
    override val channelDescriptionResId = R.string.export_channel_description
    override val progressTitleResId = R.string.export_title_progress
    override val completeTitleResId = R.string.export_title_complete
    override val failedTitleResId = R.string.export_title_failed

    override fun createCompletionInfo(filePath: String): ForegroundInfo =
        super.createCompletionInfo(context.getString(R.string.export_text_path, filePath))
}
