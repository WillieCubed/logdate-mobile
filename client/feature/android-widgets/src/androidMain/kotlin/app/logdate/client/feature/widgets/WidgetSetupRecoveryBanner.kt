@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.logdate.ui.common.BannerContent
import app.logdate.ui.common.MessageBanner
import app.logdate.ui.theme.Spacing

@Composable
internal fun WidgetSetupRecoveryBanner(
    state: WidgetSetupSaveState,
    onManageStorage: () -> Unit,
) {
    val waiting = state == WidgetSetupSaveState.WaitingForStorage
    MessageBanner(
        content =
            if (state == WidgetSetupSaveState.Idle) {
                null
            } else {
                BannerContent(
                    message = stringResource(if (waiting) R.string.widget_config_storage_pending else R.string.widget_config_save_pending),
                    icon = if (waiting) Icons.Rounded.Storage else Icons.Rounded.Sync,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    action = if (waiting) stringResource(R.string.widget_config_manage_storage) to onManageStorage else null,
                )
            },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        outerPadding = PaddingValues(vertical = Spacing.sm),
    )
}
