@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun OnboardingBirthdayScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    OnboardingBirthdayContent(
        onBack = onBack,
        onBirthdaySelected = { birthday ->
            viewModel.updateBirthday(birthday)
            onNext()
        },
        onSkip = onNext,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingBirthdayContent(
    onBack: () -> Unit,
    onBirthdaySelected: (Instant) -> Unit,
    onSkip: () -> Unit,
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        if (scrollState.collapsedFraction > 0.6f) {
                            stringResource(Res.string.onboarding_birthday_set_label)
                        } else {
                            stringResource(Res.string.onboarding_birthday_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 444.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = contentPadding.calculateTopPadding() + Spacing.lg,
                    bottom = contentPadding.calculateBottomPadding() + Spacing.lg,
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr) + Spacing.lg,
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr) + Spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                // Hero icon
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = Spacing.md)
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            item {
                Text(
                    stringResource(Res.string.onboarding_birthday_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OverviewItem(
                    title = stringResource(Res.string.onboarding_birthday_card_rewind_title),
                    description = stringResource(Res.string.onboarding_birthday_card_rewind_description),
                    icon = {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    },
                )
            }
            item {
                OverviewItem(
                    title = stringResource(Res.string.onboarding_birthday_card_origin_title),
                    description = stringResource(Res.string.onboarding_birthday_card_origin_description),
                    icon = {
                        Icon(Icons.Rounded.Timeline, contentDescription = null)
                    },
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.onboarding_birthday_set))
                    }
                    TextButton(onClick = onSkip) {
                        Text(stringResource(Res.string.onboarding_birthday_skip))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onBirthdaySelected(Instant.fromEpochMilliseconds(millis))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(Res.string.onboarding_birthday_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.onboarding_birthday_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Preview
@Composable
private fun OnboardingBirthdayScreenPreview() {
    LogDateTheme {
        OnboardingBirthdayContent(
            onBack = {},
            onBirthdaySelected = {},
            onSkip = {},
        )
    }
}
