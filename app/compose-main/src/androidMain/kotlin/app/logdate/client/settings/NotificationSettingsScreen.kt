@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.logdate.client.notifications.AndroidLogDateNotificationCatalog
import app.logdate.client.notifications.openAppNotificationSettings
import app.logdate.client.notifications.openChannelNotificationSettings
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.navigate_to_title
import androidx.compose.ui.res.stringResource as androidStringResource
import app.logdate.client.notifications.R as NotificationResources
import org.jetbrains.compose.resources.stringResource as composeStringResource

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    SettingsScaffold(
        title = androidStringResource(NotificationResources.string.notification_settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsSection(
                title = androidStringResource(NotificationResources.string.notification_settings_android_section),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                NotificationSettingsRow(
                    title = androidStringResource(NotificationResources.string.notification_settings_app_title),
                    description = androidStringResource(NotificationResources.string.notification_settings_app_description),
                    onClick = context::openAppNotificationSettings,
                )
            }
        }

        AndroidLogDateNotificationCatalog.groups.forEach { group ->
            item(key = group.key.id) {
                SettingsSection(
                    title = androidStringResource(group.nameResId),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    AndroidLogDateNotificationCatalog.phoneChannels
                        .filter { it.key.groupKey == group.key }
                        .forEach { channel ->
                            NotificationSettingsRow(
                                title = androidStringResource(channel.nameResId),
                                description = androidStringResource(channel.descriptionResId),
                                onClick = { context.openChannelNotificationSettings(channel.key) },
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    SimpleSettingsItem(
        title = title,
        description = description,
        onClick = onClick,
        action = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = composeStringResource(Res.string.navigate_to_title, title),
            )
        },
    )
}
