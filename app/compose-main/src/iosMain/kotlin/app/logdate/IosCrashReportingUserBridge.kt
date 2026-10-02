package app.logdate

import platform.Foundation.NSUserDefaults

private const val CRASHLYTICS_USER_ID_KEY = "logdate.crashReportingUserId"

/** Retires the account identifier persisted by older crash-reporting bridges. */
fun startCrashReportingUserBridge() {
    NSUserDefaults.standardUserDefaults.removeObjectForKey(CRASHLYTICS_USER_ID_KEY)
}
