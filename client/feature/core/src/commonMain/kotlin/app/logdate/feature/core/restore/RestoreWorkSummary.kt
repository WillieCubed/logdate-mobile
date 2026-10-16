package app.logdate.feature.core.restore

/** WorkManager has a 10 KiB limit; the private recovery record retains the full summary. */
internal fun RestoreSummary.forWorkData(): RestoreSummary =
    copy(
        source = source.take(256),
        appVersion = appVersion?.take(64),
        deviceId = deviceId?.take(64),
        warnings = warnings.take(4).map { it.take(128) },
        integrityMismatches = integrityMismatches.take(8),
    )
