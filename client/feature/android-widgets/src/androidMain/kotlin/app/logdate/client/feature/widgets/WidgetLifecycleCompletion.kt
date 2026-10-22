package app.logdate.client.feature.widgets

internal suspend fun completeWidgetLifecycle(
    persistRefresh: suspend () -> Unit,
    dispatch: suspend () -> Unit,
    acknowledge: () -> Unit,
) {
    persistRefresh()
    dispatch()
    acknowledge()
}
