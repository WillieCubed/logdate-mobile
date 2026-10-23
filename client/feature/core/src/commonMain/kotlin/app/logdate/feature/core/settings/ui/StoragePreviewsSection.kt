@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.storage_preview_move_description
import logdate.client.feature.core.generated.resources.storage_preview_move_title
import logdate.client.feature.core.generated.resources.storage_preview_personal_space_description
import logdate.client.feature.core.generated.resources.storage_preview_personal_space_title
import logdate.client.feature.core.generated.resources.storage_previews
import org.jetbrains.compose.resources.stringResource

/**
 * Turns on sealed storage for a test device before it ships: syncing accounts stored in their
 * personal spaces, and upgrading this account's existing journal into one. The upgrade needs both,
 * so turning on the upgrade turns on personal-space sync too, and turning off personal-space sync
 * turns off the upgrade.
 */
@Composable
fun StoragePreviewsSection(
    store: FeatureFlagStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val personalSpace by store.observe(FeatureFlag.PERSONAL_SPACE).collectAsState(initial = FeatureFlag.PERSONAL_SPACE.defaultEnabled)
    val move by store
        .observe(FeatureFlag.PERSONAL_SPACE_MIGRATION)
        .collectAsState(initial = FeatureFlag.PERSONAL_SPACE_MIGRATION.defaultEnabled)
    StoragePreviewsSection(
        personalSpace = personalSpace,
        move = move && personalSpace,
        onPersonalSpaceChange = { enabled ->
            scope.launch {
                store.setEnabled(FeatureFlag.PERSONAL_SPACE, enabled)
                if (!enabled) store.setEnabled(FeatureFlag.PERSONAL_SPACE_MIGRATION, false)
            }
        },
        onMoveChange = { enabled ->
            scope.launch {
                if (enabled) store.setEnabled(FeatureFlag.PERSONAL_SPACE, true)
                store.setEnabled(FeatureFlag.PERSONAL_SPACE_MIGRATION, enabled)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun StoragePreviewsSection(
    personalSpace: Boolean,
    move: Boolean,
    onPersonalSpaceChange: (Boolean) -> Unit,
    onMoveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(Res.string.storage_previews), modifier = modifier) {
        ToggleSettingsItem(
            title = stringResource(Res.string.storage_preview_personal_space_title),
            description = stringResource(Res.string.storage_preview_personal_space_description),
            checked = personalSpace,
            onCheckedChange = onPersonalSpaceChange,
        )
        ToggleSettingsItem(
            title = stringResource(Res.string.storage_preview_move_title),
            description = stringResource(Res.string.storage_preview_move_description),
            checked = move,
            onCheckedChange = onMoveChange,
        )
    }
}
