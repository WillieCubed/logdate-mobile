package app.logdate.client.device.identity

private val internalDeviceName =
    Regex(
        "(?i)(^sdk[_ -]|^generic[_ -]|^aosp[_ -]|^android sdk|gphone|(?:^|[_ ])(?:x86|x86_64|arm64)(?:$|[_ ]))",
    )

fun safeDeviceDisplayName(
    candidate: String?,
    fallback: String,
): String {
    val name = candidate?.trim().orEmpty()
    return name.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) || internalDeviceName.containsMatchIn(it) }
        ?: fallback
}
