@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_data_clear_action
import logdate.client.feature.core.generated.resources.clear_all_your_data_while_keeping_your_account
import logdate.client.feature.core.generated.resources.navigate_to_title
import logdate.client.feature.core.generated.resources.reset
import logdate.client.feature.core.generated.resources.reset_options
import logdate.client.feature.core.generated.resources.settings_reset_app_description
import logdate.client.feature.core.generated.resources.settings_reset_app_title
import org.jetbrains.compose.resources.stringResource

/**
 * Reset settings hub screen.
 *
 * Navigates to dedicated detail screens for each destructive action.
 */
@Composable
fun ResetSettingsScreen(
    onBack: () -> Unit,
    onNavigateToClearData: () -> Unit,
    onNavigateToResetApp: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(Res.string.reset),
        onBack = onBack,
    ) {
        item {
            SettingsSection(
                title = stringResource(Res.string.reset_options),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(Res.string.account_data_clear_action))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(Res.string.clear_all_your_data_while_keeping_your_account),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription =
                                stringResource(
                                    Res.string.navigate_to_title,
                                    stringResource(Res.string.account_data_clear_action),
                                ),
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToClearData),
                )
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(Res.string.settings_reset_app_title))
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(Res.string.settings_reset_app_description),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription =
                                stringResource(
                                    Res.string.navigate_to_title,
                                    stringResource(Res.string.settings_reset_app_title),
                                ),
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToResetApp),
                )
            }
        }
    }
}
