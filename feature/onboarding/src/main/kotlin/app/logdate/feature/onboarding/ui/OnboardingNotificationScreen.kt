package app.logdate.feature.onboarding.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingNotificationScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    useCompactLayout: Boolean = false,
) {

    // Camera permission state
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        DummyPermissionState
    }

    fun handleNotificationPermission() {
        if (notificationPermissionState.status.isGranted) {
            onNext()
        } else {
            notificationPermissionState.launchPermissionRequest()
        }
    }

    OnboardingNotificationContent(
        onBack = onBack,
        useCompactLayout = useCompactLayout,
        onEnableNotifications = ::handleNotificationPermission,
        onSkipNotifications = onNext,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingNotificationContent(
    onBack: () -> Unit,
    useCompactLayout: Boolean,
    onEnableNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    AdaptiveLayout(
        useCompactLayout = useCompactLayout,
        supplementalContent = {
            Scaffold(
                modifier = Modifier.fillMaxWidth(),
                topBar = {
                    LargeTopAppBar(
                        title = { Text("Journal notifications") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors().copy(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier.then(if (useCompactLayout) Modifier.fillMaxHeight() else Modifier),
                        scrollBehavior = scrollBehavior,
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(Spacing.lg)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        item {
                            Text(
                                "LogDate works best when you write about what's going on in the moment. Enable notifications to get smart reminders when to write.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                }
            }
        },
        mainContent = {
            if (useCompactLayout) {
                Scaffold(
                    modifier = Modifier.fillMaxWidth(),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Journal notifications") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { contentPadding ->
                    Column(
                        modifier = Modifier
                            .padding(contentPadding)
                            .widthIn(max = 444.dp)
                            .padding(Spacing.lg)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            item {
                                Text(
                                    "LogDate works best when you write about what's going on in the moment. Enable notifications to get smart reminders when to write.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        ActionsContainer(
                            onEnableNotifications = onEnableNotifications,
                            onSkipNotifications = onSkipNotifications,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg)
                        .padding(top = 96.dp)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .widthIn(max = 444.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        item {
                            Text(
                                "LogDate works best when you write about what's going on in the moment. Enable notifications to get smart reminders when to write.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    ActionsContainer(
                        modifier = Modifier.widthIn(max = 320.dp),
                        onEnableNotifications = onEnableNotifications,
                        onSkipNotifications = onSkipNotifications,
                    )
                }
            }
        },
    )

}

@Composable
private fun ActionsContainer(
    onEnableNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onEnableNotifications,
        ) {
            Text(text = "Enable")
        }
        TextButton(
            onClick = onSkipNotifications,
        ) {
            Text(text = "Continue without notifications")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private object DummyPermissionState : PermissionState {
    override val permission: String
        get() = "android.permission.POST_NOTIFICATIONS"
    override val status: PermissionStatus
        get() = PermissionStatus.Granted

    override fun launchPermissionRequest() {}
}

@Preview
@Composable
private fun OnboardingNotificationScreenPreview() {
    LogDateTheme {
        OnboardingNotificationContent(
            onBack = {},
            onEnableNotifications = {},
            onSkipNotifications = {},
            useCompactLayout = true,
        )
    }
}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun OnboardingNotificationScreenPreview_Compact_Landscape() {
    LogDateTheme {
        OnboardingNotificationContent(
            onBack = {},
            onEnableNotifications = {},
            onSkipNotifications = {},
            useCompactLayout = false,
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun OnboardingNotificationScreenPreview_Medium_Landscape() {
    LogDateTheme {
        OnboardingNotificationContent(
            onBack = {},
            onEnableNotifications = {},
            onSkipNotifications = {},
            useCompactLayout = false,
        )
    }
}
