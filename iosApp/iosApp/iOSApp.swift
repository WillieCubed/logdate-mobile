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
        // FirebaseApp.configure() reads GoogleService-Info.plist from the bundle and raises
        // an uncaught NSException if the plist is absent. The plist is gitignored — drop the
        // iOS Firebase project's plist into iosApp/iosApp/ before shipping a build that needs
        // Crashlytics. Skip configuration entirely when the plist is missing so dev builds
        // (and CI without secrets) launch normally.
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            return
        }
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        syncCrashReportingUserId()
        #endif
    }

    private func syncCrashReportingUserId() {
        UserDefaults.standard.removeObject(forKey: crashReportingUserIdKey)
        #if canImport(FirebaseCrashlytics) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else { return }
        Crashlytics.crashlytics().setUserID("")
        #endif
    }
}
