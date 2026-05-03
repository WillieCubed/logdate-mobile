@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.client.notifications

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Instant

/**
 * iOS [NotificationScheduler] backed by `UNUserNotificationCenter`.
 *
 * Each scheduled request is identified by a `<channelKey.id>:<uuid>` string so cancelling all
 * notifications on a channel can be done by listing pending requests and filtering.
 */
class IosLocalNotificationScheduler : NotificationScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun schedule(
        channelKey: LogDateNotificationChannelKey,
        title: String,
        body: String,
        deliverAt: Instant,
        deepLink: String?,
    ): String {
        val identifier = "${channelKey.id}:${kotlin.uuid.Uuid.random()}"
        val content =
            UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(body)
                if (deepLink != null) {
                    setUserInfo(mapOf<Any?, Any>("logdate.deeplink" to deepLink))
                }
                setThreadIdentifier(channelKey.id)
            }

        val components = deliverAt.toCalendarComponents()
        val trigger =
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = components,
                repeats = false,
            )
        val request =
            UNNotificationRequest.requestWithIdentifier(
                identifier = identifier,
                content = content,
                trigger = trigger,
            )

        suspendCancellableCoroutine<Unit> { continuation ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    Napier.w("addNotificationRequest failed: ${error.localizedDescription}")
                }
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        return identifier
    }

    override suspend fun cancel(requestId: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(requestId))
    }

    override suspend fun cancelChannel(channelKey: LogDateNotificationChannelKey) {
        val prefix = "${channelKey.id}:"
        val matching =
            suspendCancellableCoroutine<List<String>> { continuation ->
                center.getPendingNotificationRequestsWithCompletionHandler { rawList ->
                    @Suppress("UNCHECKED_CAST")
                    val ids =
                        (rawList as? List<UNNotificationRequest>)
                            .orEmpty()
                            .map { it.identifier }
                            .filter { it.startsWith(prefix) }
                    if (continuation.isActive) continuation.resume(ids)
                }
            }
        if (matching.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(matching)
        }
    }
}

private fun Instant.toCalendarComponents(): NSDateComponents {
    val nsDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    val units =
        NSCalendarUnitYear or
            NSCalendarUnitMonth or
            NSCalendarUnitDay or
            NSCalendarUnitHour or
            NSCalendarUnitMinute or
            NSCalendarUnitSecond
    return NSCalendar.currentCalendar.components(unitFlags = units, fromDate = nsDate)
}
