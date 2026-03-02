package app.logdate.feature.editor.ui.audio.iosbackground

import io.github.aakira.napier.Napier
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSDateComponentsFormatter
import platform.Foundation.NSDateComponentsFormatterUnitsStylePositional
import platform.Foundation.NSDateComponentsFormatterZeroFormattingBehaviorPad
import platform.Foundation.NSNumber
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Controller for iOS recording notifications and Live Activity management.
 *
 * Uses UNNotifications as a fallback on iOS versions without Live Activity support.
 * On newer iOS versions, this would be extended to use ActivityKit for Live Activities.
 */
class LiveActivityController {
    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    // IDs for notifications
    private val recordingNotificationId = "app.logdate.recording.notification"
    private val recordingUpdateId = "app.logdate.recording.update"

    init {
        Napier.d("LiveActivityController initialized")
    }

    /**
     * Shows a notification for the current recording
     */
    fun showRecordingNotification() {
        try {
            // Create notification content
            val content =
                UNMutableNotificationContent().apply {
                    setTitle("Recording in progress")
                    setBody("Tap to return to LogDate")
                    setSound(null)
                    setCategoryIdentifier("recording")

                    // Add a badge
                    setBadge(NSNumber(1))
                }

            // Create a repeating trigger (fires every minute to keep notification updated)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(60.0, false)

            // Create notification request
            val request =
                UNNotificationRequest.requestWithIdentifier(
                    recordingNotificationId,
                    content,
                    trigger,
                )

            // Add the notification request
            notificationCenter.addNotificationRequest(request, null)

            Napier.d("Recording notification posted")
        } catch (e: Exception) {
            Napier.e("Error showing recording notification: ${e.message}", e)
        }
    }

    /**
     * Updates the recording notification with elapsed time
     */
    fun updateRecordingNotification(
        elapsedTimeSeconds: Long,
        isPaused: Boolean,
    ) {
        try {
            val timeString = formatElapsedTime(elapsedTimeSeconds)

            // Create notification content
            val content =
                UNMutableNotificationContent().apply {
                    setTitle(if (isPaused) "Recording paused" else "Recording in progress")
                    setBody("Duration: $timeString - Tap to return to LogDate")
                    setSound(null)
                    setCategoryIdentifier("recording")
                }

            // Create a trigger that fires immediately
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)

            // Create notification request
            val request =
                UNNotificationRequest.requestWithIdentifier(
                    recordingUpdateId,
                    content,
                    trigger,
                )

            // Add the notification request
            notificationCenter.addNotificationRequest(request, null)
        } catch (e: Exception) {
            Napier.e("Error updating recording notification: ${e.message}", e)
        }
    }

    /**
     * Dismisses all recording notifications
     */
    fun dismissRecordingNotifications() {
        try {
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(
                listOf(recordingNotificationId, recordingUpdateId),
            )
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                listOf(recordingNotificationId, recordingUpdateId),
            )

            Napier.d("Recording notifications dismissed")
        } catch (e: Exception) {
            Napier.e("Error dismissing recording notifications: ${e.message}", e)
        }
    }

    /**
     * Checks if the app is running in the foreground
     */
    fun isAppInForeground(): Boolean = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive

    private fun formatElapsedTime(elapsedTimeSeconds: Long): String {
        val formatter =
            NSDateComponentsFormatter().apply {
                allowedUnits = NSCalendarUnitMinute or NSCalendarUnitSecond
                unitsStyle = NSDateComponentsFormatterUnitsStylePositional
                zeroFormattingBehavior = NSDateComponentsFormatterZeroFormattingBehaviorPad
            }

        return formatter.stringFromTimeInterval(elapsedTimeSeconds.toDouble()) ?: "0:00"
    }
}
