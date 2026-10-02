package app.logdate.client

import android.util.Log
import app.logdate.client.sync.diagnostics.PrivateLocalAntilog
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * Writes Napier logs to logcat in every build, including minified ones.
 *
 * Napier's own [io.github.aakira.napier.DebugAntilog] derives its tag by walking the stack trace
 * at a fixed depth, which R8 inlining breaks - in a release build that index runs off the end and
 * the logging call itself throws. This deliberately does no stack introspection, so it is safe
 * under minification.
 *
 * Warnings and errors are kept in release builds because a release install that fails is
 * otherwise undiagnosable on-device: Crashlytics receives the breadcrumb, but nothing reaches
 * `adb logcat`. Lower levels stay debug-only so release logging stays quiet.
 */
class LogcatAntilog(
    private val isDebuggable: Boolean,
) : Antilog() {
    private val privateSink = PrivateLocalAntilog { priority, text -> writeSafeLog(priority, text) }

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        privateSink.log(priority, tag, throwable, message)
    }

    private fun writeSafeLog(
        priority: LogLevel,
        text: String,
    ) {
        if (!isDebuggable && priority < LogLevel.WARNING) return
        when (priority) {
            LogLevel.VERBOSE -> Log.v(DEFAULT_TAG, text)
            LogLevel.DEBUG -> Log.d(DEFAULT_TAG, text)
            LogLevel.INFO -> Log.i(DEFAULT_TAG, text)
            LogLevel.WARNING -> Log.w(DEFAULT_TAG, text)
            LogLevel.ERROR -> Log.e(DEFAULT_TAG, text)
            LogLevel.ASSERT -> Log.wtf(DEFAULT_TAG, text)
        }
    }

    private companion object {
        const val DEFAULT_TAG = "LogDate"
    }
}
