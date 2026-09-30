package app.logdate.client.location.tracking

import java.security.MessageDigest

internal fun activityEvidenceId(
    deviceId: String,
    elapsedNanos: Long,
    activityType: Int,
    transitionType: Int,
): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest("$deviceId:$elapsedNanos:$activityType:$transitionType".encodeToByteArray())
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
