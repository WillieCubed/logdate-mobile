package app.logdate

import io.github.aakira.napier.Napier

/**
 * Entry points for iOS push notification callbacks. Swift's
 * `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` and
 * `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)` forward into these so all
 * handling lives on the Kotlin side.
 *
 * Token registration currently logs the token; the server-registration call lands when the
 * notification server's device-token endpoint is in place (§S3 / §S4 in the launch plan).
 */
@Suppress("ktlint:standard:function-naming")
fun HandleApnsTokenRegistered(tokenHex: String) {
    Napier.i("APNs token registered: $tokenHex")
}

@Suppress("ktlint:standard:function-naming")
fun HandleApnsRegistrationFailed(localizedDescription: String) {
    Napier.w("APNs registration failed: $localizedDescription")
}

/**
 * Called from Swift's silent-push handler. Triggers the same background sync the
 * BGTaskScheduler refresh path uses.
 *
 * @return true if the sync completed and produced new data, false on failure or no-op.
 */
@Suppress("ktlint:standard:function-naming")
fun HandleSilentPushSync(completion: (Boolean) -> Unit) {
    val runner = BackgroundSyncRunner()
    runner.run(completion)
}
