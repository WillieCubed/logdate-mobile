package app.logdate.client.data.restore

internal suspend fun importReceiptPinnedCameraDrafts(
    coverageComplete: Boolean,
    import: suspend () -> Boolean,
    admitSource: suspend () -> Unit,
): Boolean {
    if (!import()) return false
    if (coverageComplete) admitSource()
    return true
}
