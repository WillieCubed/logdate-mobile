package app.logdate.client

import android.util.Log
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
    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        if (!isDebuggable && priority < LogLevel.WARNING) return
        val text = message ?: throwable?.message ?: return
        val resolvedTag = tag ?: DEFAULT_TAG
        when (priority) {
            LogLevel.VERBOSE -> Log.v(resolvedTag, text, throwable)
            LogLevel.DEBUG -> Log.d(resolvedTag, text, throwable)
            LogLevel.INFO -> Log.i(resolvedTag, text, throwable)
            LogLevel.WARNING -> Log.w(resolvedTag, text, throwable)
            LogLevel.ERROR -> Log.e(resolvedTag, text, throwable)
            LogLevel.ASSERT -> Log.wtf(resolvedTag, text, throwable)
        }
    }

    private companion object {
        const val DEFAULT_TAG = "LogDate"
    }
}
