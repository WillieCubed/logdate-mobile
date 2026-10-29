package app.logdate.client.data.restore

import java.io.File

internal data class NativeSourceOutcome(
    val mediaPending: Boolean,
    val coverageComplete: Boolean,
)

internal data class RetainedOpenedSource(
    val copy: app.logdate.client.database.RetainedDatabaseCopy,
    val keyFile: File,
)
