package app.logdate.benchmark.phone

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal object PhoneBenchmarkConfig {
    const val PACKAGE_NAME = "co.reasonabletech.logdate"
    private const val MAIN_ACTIVITY = "app.logdate.client.MainActivity"
    private const val ONBOARDING_FIXTURE_EXTRA = "app.logdate.client.testing.onboarding.FIXTURE"
    private const val DEFAULT_WAIT_TIMEOUT_MS = 5_000L
    private const val SEARCH_BUTTON_ACCESSIBILITY = "logdate_home_search"
    private const val SEARCH_FIELD_ACCESSIBILITY = "search_screen_input"
    private const val NEW_ENTRY_ACCESSIBILITY = "logdate_home_new_entry"
    private const val EDITOR_TEXT_INPUT_ACCESSIBILITY = "editor_text_input"
    private const val EDITOR_SAVE_ACCESSIBILITY = "editor_save_button"
    private const val ONBOARDING_START_ACCESSIBILITY = "onboarding_start_get_started"
    private const val SEARCH_RESULT_TIMEOUT_MS = 5_000L
    private const val PROCESS_TERMINATION_TIMEOUT_MS = 2_000L
    private const val EDITOR_ENTRY_TILE_X_DIVISOR = 4
    private const val EDITOR_ENTRY_TILE_Y_PERCENT = 40

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
                Until.findObject(By.descContains(SEARCH_BUTTON_ACCESSIBILITY)),
                DEFAULT_WAIT_TIMEOUT_MS,
            )
        check(searchButton != null) {
            "Home search action did not appear"
        }
        searchButton.click()
        check(
            device.wait(
                Until.hasObject(By.descContains(SEARCH_FIELD_ACCESSIBILITY)),
                DEFAULT_WAIT_TIMEOUT_MS,
            ),
        ) {
            "Search screen did not render"
        }
    }

    fun MacrobenchmarkScope.typeSearchQuery(query: String) {
        val searchField =
            device.wait(
                Until.findObject(By.descContains(SEARCH_FIELD_ACCESSIBILITY)),
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

    fun MacrobenchmarkScope.openNewEntryFromHome() {
        val newEntryFab =
            device.wait(
                Until.findObject(By.descContains(NEW_ENTRY_ACCESSIBILITY)),
                DEFAULT_WAIT_TIMEOUT_MS,
            )
        check(newEntryFab != null) {
            "Home new-entry action did not appear"
        }
        newEntryFab.click()
        device.waitForIdle()
        if (!isEditorTextInputAvailable(DEFAULT_WAIT_TIMEOUT_MS)) {
            device.click(
                device.displayWidth / EDITOR_ENTRY_TILE_X_DIVISOR,
                (device.displayHeight * EDITOR_ENTRY_TILE_Y_PERCENT) / 100,
            )
            device.waitForIdle()
        }

        check(isEditorTextInputAvailable(DEFAULT_WAIT_TIMEOUT_MS)) { "Editor did not open" }
    }

    fun MacrobenchmarkScope.enterTextAndSaveDraft(text: String) {
        val editorField =
            device.wait(
                Until.findObject(By.descContains(EDITOR_TEXT_INPUT_ACCESSIBILITY)),
                DEFAULT_WAIT_TIMEOUT_MS,
            )
        check(editorField != null) {
            "Editor text input did not appear"
        }
        editorField.click()
        editorField.setText(text)
        device.waitForIdle()

        val saveButton =
            device.wait(
                Until.findObject(By.descContains(EDITOR_SAVE_ACCESSIBILITY)),
                DEFAULT_WAIT_TIMEOUT_MS,
            )
        check(saveButton != null) {
            "Editor save button did not appear"
        }
        saveButton.click()
        device.waitForIdle()
    }

    fun MacrobenchmarkScope.stopAppProcess() {
        stopAppProcess(device)
    }

    fun MacrobenchmarkScope.waitForFreshOnboardingStart() {
        check(
            device.wait(
                Until.findObject(By.descContains(ONBOARDING_START_ACCESSIBILITY)),
                DEFAULT_WAIT_TIMEOUT_MS,
            ) != null,
        ) {
            "Fresh onboarding did not reach the landing screen"
        }
    }

    fun stopAppProcess(device: UiDevice) {
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
        check(
            device.wait(
                Until.gone(By.pkg(PACKAGE_NAME)),
                PROCESS_TERMINATION_TIMEOUT_MS,
            ),
        ) {
            "Package $PACKAGE_NAME was still running after force-stop"
        }
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

    private fun MacrobenchmarkScope.isEditorTextInputAvailable(timeoutMs: Long): Boolean =
        device.wait(
            Until.findObject(By.descContains(EDITOR_TEXT_INPUT_ACCESSIBILITY)),
            timeoutMs,
        ) != null
}
