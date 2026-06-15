@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import logdate.client.ui.generated.resources.Res as UiRes

const val ONBOARDING_BIRTHDAY_ROOT_TAG = "onboarding_birthday_root"
const val ONBOARDING_BIRTHDAY_SET_TAG = "onboarding_birthday_set"
const val ONBOARDING_BIRTHDAY_CONFIRM_TAG = "onboarding_birthday_confirm"

@Composable
fun OnboardingBirthdayScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            isSaving = false
        }
    }

    OnboardingBirthdayContent(
        onBack = onBack,
        onBirthdaySelected = { birthday ->
            coroutineScope.launch {
                isSaving = true
                errorMessage = null
                viewModel
                    .persistBirthday(birthday)
                    .onSuccess {
                        isSaving = false
                        onNext()
                    }.onFailure {
                        errorMessage = getString(Res.string.onboarding_error_save_birthday)
                    }
            }
        },
        isSaving = isSaving,
        errorMessage = errorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingBirthdayContent(
    onBack: () -> Unit,
    onBirthdaySelected: (Instant) -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    BirthdayAdaptiveContent(
        onBack = onBack,
        onOpenDatePicker = { showDatePicker = true },
        isSaving = isSaving,
        errorMessage = errorMessage,
    )

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
                    modifier = Modifier.testTag(ONBOARDING_BIRTHDAY_CONFIRM_TAG),
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

@Composable
private fun BirthdayAdaptiveContent(
    onBack: () -> Unit,
    onOpenDatePicker: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
) {
    FoldableTabletopLayout(
        modifier = Modifier.fillMaxSize().testTag(ONBOARDING_BIRTHDAY_ROOT_TAG),
        minPaneHeight = 260.dp,
        topPane = {
            BirthdayInfoPane(
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        },
        bottomPane = {
            BirthdayActionPane(
                onOpenDatePicker = onOpenDatePicker,
                isSaving = isSaving,
                errorMessage = errorMessage,
                modifier = Modifier.fillMaxSize(),
            )
        },
        fallback = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize().testTag(ONBOARDING_BIRTHDAY_ROOT_TAG),
                minPaneWidth = 320.dp,
                startPane = {
                    BirthdayInfoPane(
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                endPane = {
                    BirthdayActionPane(
                        onOpenDatePicker = onOpenDatePicker,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    BirthdayCompactContent(
                        onBack = onBack,
                        onOpenDatePicker = onOpenDatePicker,
                        isSaving = isSaving,
                        errorMessage = errorMessage,
                    )
                },
            )
        },
    )
}

@Composable
private fun BirthdayInfoPane(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = stringResource(UiRes.string.common_back),
                )
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 444.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(Res.string.onboarding_birthday_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = Spacing.md),
            )

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
                        ).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Cake,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                stringResource(Res.string.onboarding_birthday_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            OverviewItem(
                title = stringResource(Res.string.onboarding_birthday_card_rewind_title),
                description = stringResource(Res.string.onboarding_birthday_card_rewind_description),
                icon = {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                },
            )
            OverviewItem(
                title = stringResource(Res.string.onboarding_birthday_card_origin_title),
                description = stringResource(Res.string.onboarding_birthday_card_origin_description),
                icon = {
                    Icon(Icons.Rounded.Timeline, contentDescription = null)
                },
            )
        }
    }
}

@Composable
private fun BirthdayActionPane(
    onOpenDatePicker: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onOpenDatePicker,
            modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_BIRTHDAY_SET_TAG),
            enabled = !isSaving,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(Res.string.onboarding_birthday_set))
            }
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BirthdayCompactContent(
    onBack: () -> Unit,
    onOpenDatePicker: () -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
) {
    Scaffold { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .testTag(ONBOARDING_BIRTHDAY_ROOT_TAG)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(UiRes.string.common_back),
                        )
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 444.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_birthday_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
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
                                ).align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cake,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        stringResource(Res.string.onboarding_birthday_body),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OverviewItem(
                        title = stringResource(Res.string.onboarding_birthday_card_rewind_title),
                        description = stringResource(Res.string.onboarding_birthday_card_rewind_description),
                        icon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        },
                    )
                    OverviewItem(
                        title = stringResource(Res.string.onboarding_birthday_card_origin_title),
                        description = stringResource(Res.string.onboarding_birthday_card_origin_description),
                        icon = {
                            Icon(Icons.Rounded.Timeline, contentDescription = null)
                        },
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Button(
                            onClick = onOpenDatePicker,
                            modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_BIRTHDAY_SET_TAG),
                            enabled = !isSaving,
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(Res.string.onboarding_birthday_set))
                            }
                        }
                        errorMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
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
        )
    }
}
