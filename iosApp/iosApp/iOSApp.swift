import SwiftUI

#if canImport(FirebaseCore)
import FirebaseCore
#endif

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        configureCrashReporting()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
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
        #endif
    }
}
