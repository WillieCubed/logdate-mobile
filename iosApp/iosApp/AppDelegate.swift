import UIKit
import BackgroundTasks
import ComposeApp

final class AppDelegate: NSObject, UIApplicationDelegate {
    private let syncTaskIdentifier = "app.logdate.sync.refresh"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        KoinIosKt.initKoinIos()
        registerBackgroundTasks()
        scheduleAppRefresh()
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        scheduleAppRefresh()
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        return IosDeepLinksKt.HandleIosDeepLink(urlString: url.absoluteString)
    }

    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL else {
            return false
        }
        return IosDeepLinksKt.HandleIosDeepLink(urlString: url.absoluteString)
    }

    private func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: syncTaskIdentifier, using: nil) { task in
            self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
    }

    private func handleAppRefresh(task: BGAppRefreshTask) {
        scheduleAppRefresh()

        let runner = BackgroundSyncRunner()

        task.expirationHandler = {
            runner.cancel()
            task.setTaskCompleted(success: false)
        }

        runner.run { success in
            task.setTaskCompleted(success: success)
        }
    }

    private func scheduleAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: syncTaskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            NSLog("Failed to schedule sync refresh: %@", "\(error)")
        }
    }
}
