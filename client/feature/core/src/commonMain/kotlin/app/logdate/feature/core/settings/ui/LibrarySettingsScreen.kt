@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.adaptive.FoldableBookLayout
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
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Your library",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                Text(
                    text =
                        if (isLibraryEnabled) {
                            "Library tab is visible in navigation"
                        } else {
                            "Library tab is hidden from navigation"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
            ) {
                LibraryGeneralSection(
                    isLibraryEnabled = isLibraryEnabled,
                    onSetLibraryEnabled = onSetLibraryEnabled,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        },
        standardContent = {
            SettingsScaffold(
                title = "Your library",
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    LibraryGeneralSection(
                        isLibraryEnabled = isLibraryEnabled,
                        onSetLibraryEnabled = onSetLibraryEnabled,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
    )
}

@Composable
private fun LibraryGeneralSection(
    isLibraryEnabled: Boolean,
    onSetLibraryEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = "General",
        modifier = modifier,
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
