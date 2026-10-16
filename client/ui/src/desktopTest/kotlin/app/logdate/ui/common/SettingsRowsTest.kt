package app.logdate.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsRowsTest {
    @Test
    fun supportingTextUsesTheSharedBodyMediumRole() =
        runDesktopComposeUiTest {
            var expected: TextStyle? = null
            var actual: TextStyle? = null
            setContent {
                LogDateTheme {
                    expected = MaterialTheme.typography.bodyMedium
                    SettingsRow(
                        headlineContent = { Text("Title") },
                        supportingContent = {
                            actual = LocalTextStyle.current
                            Text("Description")
                        },
                    )
                }
            }
            runOnIdle {
                assertEquals(expected?.fontSize, actual?.fontSize)
                assertEquals(expected?.lineHeight, actual?.lineHeight)
            }
        }

    @Test
    fun passiveRowsHaveNoClickAction() =
        runDesktopComposeUiTest {
            setContent {
                LogDateTheme { SimpleSettingsItem("Coverage", "Automatic", action = {}) }
            }
            onNodeWithText("Coverage").assertHasNoClickAction()
        }

    @Test
    fun disabledRowsCannotChangeTheirPreference() =
        runDesktopComposeUiTest {
            var changes = 0
            setContent {
                LogDateTheme {
                    ToggleSettingsItem("Preference", "Description", false, { changes++ }, enabled = false)
                }
            }
            onNodeWithText("Preference").assertIsNotEnabled().performClick()
            runOnIdle { assertEquals(0, changes) }
        }

    @Test
    fun linkedNavigationDoesNotChangeThePreference() =
        runDesktopComposeUiTest {
            var navigations = 0
            var changes = 0
            setContent {
                LogDateTheme {
                    Column {
                        LinkedToggleSettingsItem("Linked", "Description", false, { changes++ }, { navigations++ })
                    }
                }
            }
            onNodeWithText("Linked").performClick()
            runOnIdle {
                assertEquals(1, navigations)
                assertEquals(0, changes)
            }
        }

    @Test
    fun linkedSwitchChangesThePreferenceWithoutNavigating() =
        runDesktopComposeUiTest {
            var navigations = 0
            var changes = 0
            setContent {
                LogDateTheme { LinkedToggleSettingsItem("Linked", "Description", false, { changes++ }, { navigations++ }) }
            }
            onNodeWithContentDescription("Linked").performClick()
            runOnIdle {
                assertEquals(0, navigations)
                assertEquals(1, changes)
            }
        }

    @Test
    fun wholeMasterRowChangesThePreferenceOnce() =
        runDesktopComposeUiTest {
            var changes = 0
            setContent {
                LogDateTheme { MasterFeatureToggle("Use recommendations", false, { changes++ }) }
            }
            onNodeWithText("Use recommendations").performClick()
            runOnIdle { assertEquals(1, changes) }
        }

    @Test
    fun choiceRowOwnsSelectionAndChangesItOnce() =
        runDesktopComposeUiTest {
            var changes = 0
            setContent {
                LogDateTheme { SettingsChoiceItem("On this day", selected = true, onClick = { changes++ }) }
            }
            onNodeWithText("On this day").assertIsSelected().performClick()
            runOnIdle { assertEquals(1, changes) }
        }
}
