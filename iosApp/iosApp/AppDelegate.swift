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

    private func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: syncTaskIdentifier, using: nil) { task in
            self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
    }

    private func handleAppRefresh(task: BGAppRefreshTask) {
        scheduleAppRefresh()

        task.expirationHandler = {
            // No cancellation hook yet; next refresh will retry.
        }

        BackgroundSyncRunner().run { success in
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
