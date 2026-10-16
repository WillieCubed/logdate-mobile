@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import app.logdate.ui.common.LinkedToggleSettingsItem
import app.logdate.ui.common.MasterFeatureToggle
import app.logdate.ui.common.SettingsChoiceItem
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing

/** Populated examples keep equivalent descriptions visible together in every matrix variant. */
internal val settingsRowScenes =
    listOf(
        sharedScene(SharedScreenshotSceneId.SettingsRows, ScreenshotSceneGroup.SETTINGS, osIntegrationVariants) {
            SettingsScaffold(title = "Settings components", onBack = {}) {
                item {
                    MasterFeatureToggle(
                        "Use recommendations",
                        checked = true,
                        onCheckedChange = {},
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                item {
                    SettingsSection("Shared descriptions", modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        SimpleSettingsItem("Automatic backup", "Keep your entries available across your devices.", action = {})
                        SettingsNavigationItem("Backup details", "Keep your entries available across your devices.", onClick = {})
                        ToggleSettingsItem(
                            "Back up automatically",
                            "Keep your entries available across your devices.",
                            checked = true,
                            onCheckedChange = {},
                        )
                        LinkedToggleSettingsItem(
                            "Backup preferences",
                            "Keep your entries available across your devices.",
                            checked = true,
                            onCheckedChange = {},
                            onNavigate = {},
                        )
                        SettingsChoiceItem(
                            title = "Back up on Wi-Fi",
                            description = "Keep your entries available across your devices.",
                            selected = true,
                            onClick = {},
                        )
                        ToggleSettingsItem(
                            "Mobile data",
                            "Wait for Wi-Fi while this preference is disabled.",
                            checked = false,
                            enabled = false,
                            onCheckedChange = {},
                        )
                    }
                }
            }
        },
    )
