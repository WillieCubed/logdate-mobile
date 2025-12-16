package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.feature.location.timeline.ui.LocationTimelineBottomSheet
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for managing location tracking settings.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param viewModel The view model that manages location settings
 */
@Composable
fun LocationSettingsScreen(
    onBack: () -> Unit,
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    // Detect if we're in a large screen layout where this might be a detail pane
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isPotentialDetailPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val uiState by viewModel.uiState.collectAsState()
    var showLocationTimeline by remember { mutableStateOf(false) }

    LocationSettingsContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleBackgroundTracking = viewModel::toggleBackgroundTracking,
        onToggleJournalTracking = viewModel::toggleJournalTracking,
        onToggleTimelineTracking = viewModel::toggleTimelineTracking,
        onUpdateTrackingInterval = viewModel::updateTrackingInterval,
        onShowLocationTimeline = { showLocationTimeline = true },
        isPotentialDetailPane = isPotentialDetailPane,
        modifier = modifier.applyScreenStyles()
    )

    // Location Timeline Bottom Sheet
    LocationTimelineBottomSheet(
        isVisible = showLocationTimeline,
        onDismiss = { showLocationTimeline = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSettingsContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onToggleBackgroundTracking: (Boolean) -> Unit,
    onToggleJournalTracking: (Boolean) -> Unit,
    onToggleTimelineTracking: (Boolean) -> Unit,
    onUpdateTrackingInterval: (Long) -> Unit,
    onShowLocationTimeline: () -> Unit,
    isPotentialDetailPane: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text("Location Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section title for two-pane mode
                if (isPotentialDetailPane) {
                    item {
                        Text(
                            text = "Location Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
            // Location Services Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Location Services",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = Spacing.sm)
                    )

                    MaterialContainer {
                        ToggleSettingsItem(
                            title = "Enable Background Location Tracking",
                            description = "Allow the app to track your location in the background for better context and features.",
                            checked = settings.backgroundTrackingEnabled,
                            onCheckedChange = onToggleBackgroundTracking,
                        )
                        ToggleSettingsItem(
                            title = "Track Location for Journal Entries",
                            description = "Automatically attach your location to new journal entries.",
                            checked = settings.autoTrackForJournalEntries,
                            onCheckedChange = onToggleJournalTracking,
                        )
                        ToggleSettingsItem(
                            title = "Track Location for Timeline Review",
                            description = "Record location when viewing your timeline to improve context.",
                            checked = settings.autoTrackForTimelineReview,
                            onCheckedChange = onToggleTimelineTracking,
                        )
                    }
                }
            }

            // Tracking Interval Section (only visible if background tracking is enabled)
            if (settings.backgroundTrackingEnabled) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = "Tracking Interval",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = Spacing.sm)
                        )

                        MaterialContainer {
                            SurfaceItem {
                                Column(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Text(
                                        text = "Update Frequency: ${settings.trackingIntervalMinutes} minutes",
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    // Calculate the step value for the current interval
                                    // 15 minutes = step 0, 30 minutes = step 1, etc.
                                    val stepValue =
                                        ((settings.trackingIntervalMinutes - 15) / 15f).coerceIn(
                                            0f,
                                            7f
                                        )

                                    // Use steps parameter for discrete slider with 15-minute intervals
                                    // From 15 to 120 minutes in 15-minute steps = 8 steps (0 to 7)
                                    Slider(
                                        value = stepValue,
                                        onValueChange = { newValue ->
                                            // Convert from step value to minutes (15-120)
                                            val step = newValue.toInt()
                                            val minutes = 15 + (step * 15)
                                            onUpdateTrackingInterval(minutes.toLong())
                                        },
                                        valueRange = 0f..7f,
                                        steps = 6, // 8 positions (0-7) means 7 spaces, so 6 steps
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("15 min", style = MaterialTheme.typography.labelMedium)
                                        Text("30 min", style = MaterialTheme.typography.labelMedium)
                                        Text("60 min", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            "120 min",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }

                                    Text(
                                        "Note: More frequent updates use more battery. The minimum interval is 15 minutes due to system limitations.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Location Timeline Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Location Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = Spacing.sm)
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onShowLocationTimeline
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = "View Timeline",
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "View Location Timeline",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Text(
                                    "See your location history and current location",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Location Privacy Notes
            item {
                LocationSettingsNotes()
            }
            }
        }
    }
}

@Composable
private fun LocationSettingsNotes() {
    Surface {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Location data is stored on your device",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                "Your location data is only stored locally on your device. If you enable cloud sync, your location data will be encrypted before being sent to the cloud.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * A block of related settings items used to group options together.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.sm)
        )

        MaterialContainer {
            content()
        }
    }
}


/**
 * A simple settings item with a slot for title and description and optional actions.
 *
 * @param title The title of the settings item
 * @param description The description text for the settings item. This should provide a great amount of context to the user.
 * @param overline Optional overline text for additional context or grouping. Meant to be a brief label.
 * @param onClick Callback when the item is clicked
 */
@Composable
fun SimpleSettingsItem(
    title: String,
    description: String,
    overline: String? = null,
    onClick: () -> Unit = {},
    action: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        overlineContent = overline?.let { { Text(it) } },
        trailingContent = action,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    )
}

/**
 * A [SimpleSettingsItem] that has a toggleable switch.
 */
@Composable
fun ToggleSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    overline: String? = null,
    onClick: () -> Unit = {},
) {
    SimpleSettingsItem(
        title = title,
        description = description,
        overline = overline,
        onClick = onClick,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}