package app.logdate.client.sync.peer.state

import kotlinx.serialization.Serializable

/** Phone-owned local waking times, without any underlying sleep records. */
@Serializable
data class ReminderHours(
    val wakeSecond: Int,
    val sleepSecond: Int,
) {
    val valid: Boolean get() = wakeSecond in 0 until 86_400 && sleepSecond in 0 until 86_400 && wakeSecond != sleepSecond
}
