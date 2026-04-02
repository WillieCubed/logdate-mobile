package app.logdate.benchmark.phone

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal object PhoneBenchmarkConfig {
    const val PACKAGE_NAME = "co.reasonabletech.logdate"
    private const val MAIN_ACTIVITY = "app.logdate.client.MainActivity"
    private const val ONBOARDING_FIXTURE_EXTRA = "app.logdate.client.testing.onboarding.FIXTURE"
    private const val DEFAULT_WAIT_TIMEOUT_MS = 5_000L
    private const val SEARCH_BUTTON_DESCRIPTION = "Search"
    private const val SEARCH_PLACEHOLDER = "Search entries"
    private const val SEARCH_RESULT_TIMEOUT_MS = 3_000L

    fun MacrobenchmarkScope.startFromLauncher(fixture: String? = null) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(PACKAGE_NAME, MAIN_ACTIVITY)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (fixture != null) {
                    putExtra(ONBOARDING_FIXTURE_EXTRA, fixture)
                }
            },
        )
    }

    fun MacrobenchmarkScope.startFromDeepLink(fixture: String? = null) {
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW, Uri.parse("logdate://rewind")).apply {
                setPackage(PACKAGE_NAME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (fixture != null) {
                    putExtra(ONBOARDING_FIXTURE_EXTRA, fixture)
                }
            },
        )
    }

    fun MacrobenchmarkScope.openSearchFromHome() {
        val searchButton =
            device.wait(
                Until.findObject(By.desc(SEARCH_BUTTON_DESCRIPTION)),
                DEFAULT_WAIT_TIMEOUT_MS,
            )
        check(searchButton != null) {
            "Home search action did not appear"
        }
        searchButton.click()
        check(
            device.wait(
                Until.hasObject(By.textContains(SEARCH_PLACEHOLDER)),
                DEFAULT_WAIT_TIMEOUT_MS,
            ),
        ) {
            "Search screen did not render"
        }
    }

    fun MacrobenchmarkScope.typeSearchQuery(query: String) {
        val searchField =
            device.wait(
                Until.findObject(By.textContains(SEARCH_PLACEHOLDER)),
                DEFAULT_WAIT_TIMEOUT_MS,
            )
        check(searchField != null) {
            "Search field did not appear"
        }
        searchField.click()
        searchField.setText(query)
        device.wait(
            Until.hasObject(By.textContains(query)),
            SEARCH_RESULT_TIMEOUT_MS,
        )
        device.waitForIdle()
    }

    fun freshOnboardingFixtureJson(): String =
        fixtureJson(isOnboarded = false)

    fun onboardedHomeFixtureJson(): String =
        fixtureJson(isOnboarded = true)

    private fun fixtureJson(isOnboarded: Boolean): String =
        """
        {
          "isOnboarded": $isOnboarded,
          "entryMode": "FRESH",
          "hasPersonalIntro": false,
          "hasBirthday": false,
          "hasCloudAccount": false,
          "notificationsHandledOnThisDevice": false,
          "recommendationsEnabled": true,
          "locationTrackingEnabled": false,
          "sleepBasedDayBoundariesEnabled": false
        }
        """.trimIndent()
}
