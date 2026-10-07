@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.streak.CampfireViewModel
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.screen_title_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

/**
 * Main settings overview screen that displays navigation options to different settings sections.
 * This is the entry point for the settings flow and serves as the list pane in list-detail layouts.
 *
 * Groups: Personal / Privacy & Security / Data & Storage
 */
@Composable
fun SettingsOverviewScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToWatch: (() -> Unit)? = null,
    onNavigateToReset: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToVoiceNotes: () -> Unit = {},
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToStreaks: () -> Unit = {},
    onNavigateToRewindSettings: () -> Unit = {},
    onNavigateToEventsSettings: () -> Unit = {},
    onNavigateToPeopleSettings: () -> Unit = {},
    onNavigateToTimeline: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsOverviewViewModel = koinViewModel(),
    campfireViewModel: CampfireViewModel = koinViewModel(),
) {
    val identity by viewModel.resolvedIdentity.collectAsState()
    val streakData by viewModel.streakData.collectAsState()
    val campfire by campfireViewModel.presentation.collectAsState()
    val isCampfireEnabled by campfireViewModel.isCampfireEnabled.collectAsState()

    SettingsOverviewContent(
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToWatch = onNavigateToWatch,
        onNavigateToReset = onNavigateToReset,
        onNavigateToLocation = onNavigateToLocation,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToLibrarySettings = onNavigateToLibrarySettings,
        onNavigateToMemories = onNavigateToMemories,
        onNavigateToVoiceNotes = onNavigateToVoiceNotes,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToStreaks = onNavigateToStreaks,
        onNavigateToRewindSettings = onNavigateToRewindSettings,
        onNavigateToEventsSettings = onNavigateToEventsSettings,
        onNavigateToPeopleSettings = onNavigateToPeopleSettings,
        onNavigateToTimeline = onNavigateToTimeline,
        onNavigateToSync = onNavigateToSync,
        onNavigateToExport = onNavigateToExport,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
        onNavigateToAbout = onNavigateToAbout,
        userProfile =
            UserProfile(
                name = identity.displayName,
                username = identity.username ?: "",
                isAuthenticated = identity.isAuthenticated,
            ),
        onboardedDate = identity.onboardedDate ?: Instant.DISTANT_PAST,
        // The old streak badge only shows once the campfire flag is known to be off.
        streakCount = if (isCampfireEnabled == false && streakData.isEnabled) streakData.currentStreak else null,
        campfire = campfire,
        modifier = modifier,
    )
}

@Composable
fun SettingsOverviewContent(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToWatch: (() -> Unit)? = null,
    onNavigateToReset: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToVoiceNotes: () -> Unit = {},
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToStreaks: () -> Unit = {},
    onNavigateToRewindSettings: () -> Unit = {},
    onNavigateToEventsSettings: () -> Unit = {},
    onNavigateToPeopleSettings: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToLibrarySettings: () -> Unit = {},
    userProfile: UserProfile,
    onboardedDate: Instant = Instant.DISTANT_PAST,
    streakCount: Int? = null,
    campfire: CampfirePresentation? = null,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier,
        minPaneWidth = 320.dp,
        startPane = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsIdentityCard(
                    userProfile = userProfile,
                    onboardedDate = onboardedDate,
                    streakCount = streakCount,
                    campfire = campfire,
                    onEditProfile = onNavigateToProfile,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )

                SettingsPersonalSection(
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToMemories = onNavigateToMemories,
                    onNavigateToVoiceNotes = onNavigateToVoiceNotes,
                    onNavigateToTimeline = onNavigateToTimeline,
                    onNavigateToStreaks = onNavigateToStreaks,
                    onNavigateToRewindSettings = onNavigateToRewindSettings,
                    onNavigateToEventsSettings = onNavigateToEventsSettings,
                    onNavigateToPeopleSettings = onNavigateToPeopleSettings,
                    onNavigateToLibrarySettings = onNavigateToLibrarySettings,
                )
            }
        },
        endPane = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsPrivacySection(
                    userProfile = userProfile,
                    onNavigateToPrivacy = onNavigateToPrivacy,
                    onNavigateToAccount = onNavigateToAccount,
                    onNavigateToDevices = onNavigateToDevices,
                    onNavigateToLocation = onNavigateToLocation,
                    onNavigateToWatch = onNavigateToWatch,
                    onNavigateToNotifications = onNavigateToNotifications,
                )

                SettingsDataSection(
                    userProfile = userProfile,
                    onNavigateToSync = onNavigateToSync,
                    onNavigateToExport = onNavigateToExport,
                    onNavigateToReset = onNavigateToReset,
                    onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
                    onNavigateToSignIn = onNavigateToSignIn,
                )
                SettingsAboutItem(onNavigateToAbout)
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.screen_title_settings),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    SettingsIdentityCard(
                        userProfile = userProfile,
                        onboardedDate = onboardedDate,
                        streakCount = streakCount,
                        campfire = campfire,
                        onEditProfile = onNavigateToProfile,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Personal group
                item {
                    SettingsPersonalSection(
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToMemories = onNavigateToMemories,
                        onNavigateToVoiceNotes = onNavigateToVoiceNotes,
                        onNavigateToTimeline = onNavigateToTimeline,
                        onNavigateToStreaks = onNavigateToStreaks,
                        onNavigateToRewindSettings = onNavigateToRewindSettings,
                        onNavigateToEventsSettings = onNavigateToEventsSettings,
                        onNavigateToPeopleSettings = onNavigateToPeopleSettings,
                        onNavigateToLibrarySettings = onNavigateToLibrarySettings,
                    )
                }

                // Privacy & Security group
                item {
                    SettingsPrivacySection(
                        userProfile = userProfile,
                        onNavigateToPrivacy = onNavigateToPrivacy,
                        onNavigateToAccount = onNavigateToAccount,
                        onNavigateToDevices = onNavigateToDevices,
                        onNavigateToLocation = onNavigateToLocation,
                        onNavigateToWatch = onNavigateToWatch,
                        onNavigateToNotifications = onNavigateToNotifications,
                    )
                }

                // Data & Storage group
                item {
                    SettingsDataSection(
                        userProfile = userProfile,
                        onNavigateToSync = onNavigateToSync,
                        onNavigateToExport = onNavigateToExport,
                        onNavigateToReset = onNavigateToReset,
                        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
                        onNavigateToSignIn = onNavigateToSignIn,
                    )
                }
                item { SettingsAboutItem(onNavigateToAbout) }
            }
        },
    )
}

@Preview
@Composable
private fun SettingsOverviewScreenPreview() {
    SettingsOverviewContent(
        onBack = {},
        onNavigateToProfile = {},
        onNavigateToAccount = {},
        onNavigateToDevices = {},
        onNavigateToReset = {},
        onNavigateToLocation = {},
        onNavigateToPrivacy = {},
        onNavigateToMemories = {},
        onNavigateToSync = {},
        onNavigateToExport = {},
        userProfile =
            UserProfile(
                name = "John Doe",
                username = "johndoe",
                isAuthenticated = true,
            ),
    )
}

@Preview
@Composable
private fun SettingsOverviewScreenPreviewNotSignedIn() {
    SettingsOverviewContent(
        onBack = {},
        onNavigateToProfile = {},
        onNavigateToAccount = {},
        onNavigateToDevices = {},
        onNavigateToReset = {},
        onNavigateToLocation = {},
        onNavigateToPrivacy = {},
        onNavigateToMemories = {},
        onNavigateToSync = {},
        onNavigateToExport = {},
        userProfile =
            UserProfile(
                name = "",
                username = "",
                isAuthenticated = false,
            ),
    )
}
