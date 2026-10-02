package app.logdate.client

import app.logdate.client.sync.diagnostics.PrivateCrashAntilog
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/** Only finite severity categories cross the crash-report boundary. */
class CrashlyticsAntilog : Antilog() {
    private val safeSink = PrivateCrashAntilog { category -> FirebaseCrashlytics.getInstance().log(category) }

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        safeSink.log(priority, tag, throwable, message)
    }
}
