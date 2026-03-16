@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings screen for the Library feature.
 *
 * Allows enabling/disabling the Library tab and will host additional
 * library-related settings (scan frequency, storage, multi-display) in the future.
 */
@Composable
fun LibrarySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val isLibraryEnabled by viewModel.isLibraryEnabled.collectAsStateWithLifecycle()

    LibrarySettingsContent(
        onBack = onBack,
        isLibraryEnabled = isLibraryEnabled,
        onSetLibraryEnabled = viewModel::setLibraryEnabled,
        modifier = modifier,
    )
}

@Composable
fun LibrarySettingsContent(
    onBack: () -> Unit,
    isLibraryEnabled: Boolean,
    onSetLibraryEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = "Your library",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsSection(
                title = "General",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                ToggleSettingsItem(
                    title = "Enable Library",
                    description =
                        if (isLibraryEnabled) {
                            "Library tab is visible in navigation"
                        } else {
                            "Library tab is hidden from navigation"
                        },
                    checked = isLibraryEnabled,
                    onCheckedChange = onSetLibraryEnabled,
                )
            }
        }
    }
}
