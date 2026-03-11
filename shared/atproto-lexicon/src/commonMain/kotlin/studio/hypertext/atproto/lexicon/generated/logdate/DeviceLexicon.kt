package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object DeviceLexicon {
    public const val ID: String = "studio.hypertext.logdate.device"
}

@Serializable
public data class Device(
    val appVersion: String?,
    val capabilities: List<String>?,
    val createdAt: Long,
    val deviceId: String,
    val deviceName: String?,
    val lastSeenAt: Long,
    val osVersion: String?,
    val platform: String,
)
