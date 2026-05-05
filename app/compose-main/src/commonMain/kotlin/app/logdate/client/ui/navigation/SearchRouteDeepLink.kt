package app.logdate.client.ui.navigation

/**
 * Pure parser for `logdate://search?q=...&type=...&date=...` query parameters.
 *
 * Extracted from the Android `DeepLinkResolver` so the parsing rules (empty values default to
 * "no filter", types are comma-separated, whitespace is trimmed) can be unit-tested without an
 * Android `Uri` dependency. Lives in commonMain so the test can run on JVM without the Android
 * SDK loaded.
 */
internal fun searchRouteFromParams(
    rawQuery: String?,
    rawTypes: String?,
    rawDate: String?,
): SearchRoute =
    SearchRoute(
        query = rawQuery.orEmpty(),
        typeFtsValues =
            rawTypes
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
        dateRangeName = rawDate.orEmpty(),
    )
