package app.logdate.client.feature.widgets

/** Reuses queued work; expedited quota rejection must still leave an accepted regular job. */
internal fun <T : Any> enqueueWidgetLifecycleWork(
    canExpedite: Boolean,
    pending: () -> T?,
    create: (expedited: Boolean) -> T,
    enqueue: (T) -> Boolean,
) {
    val existing = pending()
    val preferred = existing ?: create(canExpedite)
    if (enqueue(preferred)) return
    check(canExpedite) { "Widget work scheduling remains pending" }
    check(enqueue(pending() ?: create(false))) { "Widget fallback scheduling remains pending" }
}
