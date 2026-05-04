import SwiftUI

#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseCrashlytics)
import FirebaseCrashlytics
#endif

private let crashReportingUserIdKey = "logdate.crashReportingUserId"

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        configureCrashReporting()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                    syncCrashReportingUserId()
                }
        }
    }

    private func configureCrashReporting() {
        #if canImport(FirebaseCore)
        // FirebaseApp.configure() reads GoogleService-Info.plist from the bundle. The plist
        // is gitignored — drop the iOS Firebase project's plist into iosApp/iosApp/ before
        // shipping a build that needs Crashlytics. Without it, configure() logs a warning
        // and Crashlytics stays disabled, so this is safe to call unconditionally.
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        syncCrashReportingUserId()
        #endif
    }

    private func syncCrashReportingUserId() {
        #if canImport(FirebaseCrashlytics)
        // The Kotlin side mirrors the signed-in account ID into UserDefaults under this key
        // (see IosCrashReportingUserBridge in compose-main). Read it back here so crash
        // reports are tagged with the same anonymized account ID as on Android.
        if let id = UserDefaults.standard.string(forKey: crashReportingUserIdKey) {
            Crashlytics.crashlytics().setUserID(id)
        } else {
            Crashlytics.crashlytics().setUserID("")
        }
        #endif
    }
}
