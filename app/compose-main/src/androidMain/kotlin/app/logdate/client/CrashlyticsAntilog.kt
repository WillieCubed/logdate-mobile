package app.logdate.client

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * Forwards Napier logs into Firebase Crashlytics so each crash report includes the recent
 * log timeline as breadcrumbs and any error-level throwable is recorded as a non-fatal event.
 */
class CrashlyticsAntilog : Antilog() {
    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        if (priority < LogLevel.INFO) return
        val text = message ?: throwable?.message ?: return
        val tagged = if (tag != null) "[$tag] $text" else text
        crashlytics.log("${priority.name.first()}/$tagged")
        if (priority >= LogLevel.ERROR && throwable != null) {
            crashlytics.recordException(throwable)
        }
    }
}
