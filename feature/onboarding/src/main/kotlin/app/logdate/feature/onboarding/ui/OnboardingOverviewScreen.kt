package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.core.ui.R as coreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingOverviewScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    useSplitScreen: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Here's how this works.") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.then(if (useSplitScreen) Modifier.fillMaxHeight() else Modifier),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (!useSplitScreen) {
            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxHeight()
                    .widthIn(max = 444.dp)
                    .padding(Spacing.lg)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    OverviewItem(
                        title = "Add things to your Log",
                        description = "Your Log is your personal timeline. Write things and add photos or even voice memos. Everything is private to you by default.",
                        icon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null)
                        },
                    )
                }
                item {
                    OverviewItem(
                        title = "Make journals for your memories",
                        description = "Create a journal for the people you care about - whether your climbing buddies, your crochet club, or your family. Even if they don’t use LogDate, they can still join in and add stuff.",
                        icon = {
                            Icon(
                                painterResource(coreR.drawable.book_open),
                                contentDescription = null
                            )
                        },
                    )
                }
                item {
                    OverviewItem(
                        title = "Get weekly recaps",
                        description = "Every week, we’ll give you a Wrapped for your everyday life. We’ll even give you a year in review if you keep it up!",
                        icon = {
                            Icon(Icons.Rounded.History, contentDescription = null)
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onNext,
                        ) {
                            Text(text = "Continue")
                        }
                    }
                }
            }
        } else {
            // Keep blank
        }
    }
}

@Composable
internal fun OverviewItem(
    title: String, description: String, icon: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.Start),
    ) {
        InfoIcon {
            icon()
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.Top),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoIcon(icon: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        icon()
    }
}

@Preview
@Composable
private fun OnboardingOverviewScreenPreview() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun OnboardingOverviewScreenPreview_Compact_Landscape() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun OnboardingOverviewScreenPreview_Medium_Landscape() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}