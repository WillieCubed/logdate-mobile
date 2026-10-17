package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object DeviceLexicon {
    public const val ID: String = "studio.hypertext.logdate.device"
}

@Serializable
public data class Device(
    val appVersion: String? = null,
    val capabilities: List<String>? = null,
    val createdAt: Long,
    val deviceId: String,
    val deviceName: String? = null,
    val lastSeenAt: Long,
    val osVersion: String? = null,
    val platform: String,
)
