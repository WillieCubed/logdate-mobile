package app.logdate.feature.onboarding.ui

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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.onboarding.R
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing

@Composable
fun OnboardingNotificationConfirmationScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    useCompactLayout: Boolean = false,
) {

    OnboardingNotificationConfirmationContent(
        onBack = onBack,
        onNext = onNext,
        useCompactLayout = useCompactLayout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingNotificationConfirmationContent(
    onBack: () -> Unit,
    onNext: () -> Unit,
    useCompactLayout: Boolean,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    AdaptiveLayout(
        useCompactLayout = useCompactLayout,
        supplementalContent = {
            Scaffold(
                modifier = Modifier.fillMaxWidth(),
                topBar = {
                    LargeTopAppBar(
                        title = { Text(stringResource(R.string.onboarding_notifications_confirmation_title)) },
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
                                stringResource(R.string.onboarding_notifications_confirmation_content),
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
                            title = { Text(stringResource(R.string.onboarding_notifications_confirmation_title)) },
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
                            .fillMaxWidth()
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
                                    stringResource(R.string.onboarding_notifications_confirmation_content),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        ActionsContainer(
                            onNext = onNext,
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
                                stringResource(R.string.onboarding_notifications_confirmation_content),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    ActionsContainer(
                        modifier = Modifier.widthIn(max = 320.dp),
                        onNext = onNext,
                    )
                }
            }
        },
    )

}

@Composable
private fun ActionsContainer(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNext,
        ) {
            Text(text = "Continue")
        }
    }
}

@Preview
@Composable
private fun OnboardingNotificationScreenPreview() {
    LogDateTheme {
        OnboardingNotificationConfirmationContent(
            onBack = {},
            onNext = {},
            useCompactLayout = true,
        )
    }
}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun OnboardingNotificationScreenPreview_Compact_Landscape() {
    LogDateTheme {
        OnboardingNotificationConfirmationContent(
            onBack = {},
            onNext = {},
            useCompactLayout = false,
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun OnboardingNotificationScreenPreview_Medium_Landscape() {
    LogDateTheme {
        OnboardingNotificationConfirmationContent(
            onBack = {},
            onNext = {},
            useCompactLayout = false,
        )
    }
}
