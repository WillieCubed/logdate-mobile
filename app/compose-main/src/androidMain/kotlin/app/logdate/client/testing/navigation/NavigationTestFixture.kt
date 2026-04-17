package app.logdate.client.testing.navigation

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import app.logdate.client.testing.launch.ActivityLaunchTestOverrides
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.WatchSettingsRoute
import app.logdate.navigation.routes.core.WatchTroubleshootingRoute

/**
 * Optional test-only starting destination for instrumented flows that need to
 * exercise a specific app screen without replaying the entire manual path.
 */
enum class NavigationTestDestination {
    EntryEditor,
    SettingsOverview,
    WatchSettings,
    WatchTroubleshooting,
}

private const val NAVIGATION_TEST_DESTINATION_EXTRA = "app.logdate.client.testing.navigation.TEST_DESTINATION"

/** Writes a test-only starting destination into the launch intent. */
fun Intent.putNavigationTestDestination(destination: NavigationTestDestination): Intent =
    apply {
        putExtra(NAVIGATION_TEST_DESTINATION_EXTRA, destination.name)
    }

/** Maps the test-only destination extra to the matching navigation key. */
fun Intent.readNavigationTestDestination(): NavKey? =
    when (getStringExtra(NAVIGATION_TEST_DESTINATION_EXTRA) ?: ActivityLaunchTestOverrides.navigationDestination?.name) {
        NavigationTestDestination.EntryEditor.name -> EntryEditor()
        NavigationTestDestination.SettingsOverview.name -> SettingsOverviewRoute
        NavigationTestDestination.WatchSettings.name -> WatchSettingsRoute
        NavigationTestDestination.WatchTroubleshooting.name -> WatchTroubleshootingRoute
        else -> null
    }
