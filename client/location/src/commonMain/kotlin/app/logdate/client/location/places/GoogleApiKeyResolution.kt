package app.logdate.client.location.places

internal fun selectResolvedGoogleMapsApiKey(
    explicitApiKey: String,
    manifestApiKey: String?,
    googleServicesApiKey: String,
): String =
    explicitApiKey.takeUnless(String::isBlank)
        ?: manifestApiKey?.takeUnless(String::isBlank)
        ?: googleServicesApiKey
