package app.logdate

import app.logdate.client.repository.account.PasskeyAccountRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.Foundation.NSUserDefaults

private const val CRASHLYTICS_USER_ID_KEY = "logdate.crashReportingUserId"

private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Mirrors the currently-signed-in account's ID into NSUserDefaults so the iOS shell can hand
 * it to Crashlytics on launch (and on resume). The Swift side reads the same key from
 * [iOSApp.swift] once Firebase is configured.
 */
fun startCrashReportingUserBridge() {
    val repository =
        runCatching { KoinPlatform.getKoin().get<PasskeyAccountRepository>() }.getOrElse { error ->
            Napier.w("Crashlytics user bridge: PasskeyAccountRepository not in Koin graph", error)
            return
        }
    bridgeScope.launch {
        repository.currentAccount.collect { account ->
            val defaults = NSUserDefaults.standardUserDefaults
            if (account != null) {
                defaults.setObject(account.id.toString(), forKey = CRASHLYTICS_USER_ID_KEY)
            } else {
                defaults.removeObjectForKey(CRASHLYTICS_USER_ID_KEY)
            }
        }
    }
}
