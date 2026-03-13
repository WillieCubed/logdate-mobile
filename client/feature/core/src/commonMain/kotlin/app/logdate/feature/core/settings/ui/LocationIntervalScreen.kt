@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.location_tracking_battery_note
import logdate.client.feature.core.generated.resources.location_update_frequency
import logdate.client.feature.core.generated.resources.location_update_interval
import logdate.client.feature.core.generated.resources.tracking_interval
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocationIntervalScreen(
    onBack: () -> Unit,
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LocationIntervalContent(
        settings = uiState.settings,
        onBack = onBack,
        onUpdateTrackingInterval = viewModel::updateTrackingInterval,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationIntervalContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onUpdateTrackingInterval: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val intervalOptions = listOf(2L, 5L, 10L, 15L, 30L, 60L, 120L)
    val selectedIntervalIndex =
        intervalOptions.indexOfLast { option -> option <= settings.minimumPersistIntervalMinutes }.coerceAtLeast(0)

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.location_update_interval)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item {
                    SettingsSection(
                        title = stringResource(Res.string.tracking_interval),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        Res.string.location_update_frequency,
                                        settings.minimumPersistIntervalMinutes,
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            Slider(
                                value = selectedIntervalIndex.toFloat(),
                                onValueChange = { newValue ->
                                    val step = newValue.toInt().coerceIn(0, intervalOptions.lastIndex)
                                    val minutes = intervalOptions[step]
                                    onUpdateTrackingInterval(minutes)
                                },
                                valueRange = 0f..intervalOptions.lastIndex.toFloat(),
                                steps = intervalOptions.size - 2,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Text(
                                stringResource(Res.string.location_tracking_battery_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
