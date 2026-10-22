package app.logdate.client.feature.widgets

internal fun retainWidgetLifecycleCustody(
    persist: () -> Unit,
    schedule: (includePayload: Boolean) -> Boolean,
    persistedFallbackSupported: Boolean,
    onFailure: (Exception) -> Unit,
): Boolean {
    try {
        persist()
    } catch (error: Exception) {
        onFailure(error)
        val accepted = schedule(true)
        return persistedFallbackSupported && accepted
    }
    return schedule(false)
}
