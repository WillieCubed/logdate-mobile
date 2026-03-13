@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.profile.ui.ProfileViewModel
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import app.logdate.util.formatDateLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.birthday
import logdate.client.feature.core.generated.resources.birthday_detail_description
import logdate.client.feature.core.generated.resources.birthday_not_set
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.confirm
import logdate.client.feature.core.generated.resources.your_birthday
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Detail screen for birthday personalization.
 *
 * Explains what features the user's birthday powers (timeline origin,
 * birthday celebrations, age-aware personalization) and provides a
 * clean settings item to set or change it via a date picker dialog.
 */
@Composable
fun BirthdaySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentBirthday = uiState.localProfile.birthday

    BirthdaySettingsContent(
        currentBirthday = currentBirthday,
        onBack = onBack,
        onSave = viewModel::saveBirthday,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaySettingsContent(
    currentBirthday: Instant?,
    onBack: () -> Unit,
    onSave: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showDatePicker by remember { mutableStateOf(false) }

    val resolvedBirthday =
        currentBirthday?.takeIf { it != Instant.DISTANT_PAST }

    val hasBirthday = resolvedBirthday != null

    val formattedBirthday =
        resolvedBirthday?.let {
            formatDateLocalized(it.toLocalDateTime(TimeZone.UTC).date)
        }

    val age by remember(resolvedBirthday) {
        derivedStateOf {
            resolvedBirthday?.let { calculateAge(it) }
        }
    }

    val ageMessage by remember(age) {
        derivedStateOf {
            age?.let { formatAgeMessage(it) } ?: ""
        }
    }

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.birthday)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
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
                // Hero graphic
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(96.dp)
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
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }

                // Description
                item {
                    Text(
                        text = stringResource(Res.string.birthday_detail_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Birthday setting
                item {
                    SettingsSection(
                        title = stringResource(Res.string.your_birthday),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        val birthdayDescription =
                            formattedBirthday
                                ?: stringResource(Res.string.birthday_not_set)
                        SimpleSettingsItem(
                            title = stringResource(Res.string.birthday),
                            description = birthdayDescription,
                            onClick = { showDatePicker = true },
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                            )
                        }
                    }
                }

                // Age message (only when birthday is set)
                if (hasBirthday) {
                    item {
                        AnimatedVisibility(
                            visible = ageMessage.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(40.dp)
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
                                        imageVector = Icons.Default.Celebration,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.size(Spacing.md))

                                Text(
                                    text = ageMessage,
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = resolvedBirthday?.toEpochMilliseconds(),
            )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onSave(Instant.fromEpochMilliseconds(millis))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Calculate age in years from a birthday Instant.
 */
private fun calculateAge(birthday: Instant): Int {
    val now = Clock.System.now()
    val duration = now - birthday
    return floor(duration.inWholeDays / 365.25).toInt()
}

/**
 * Format an age message based on the age.
 */
private fun formatAgeMessage(age: Int): String =
    when {
        age < 0 -> ""
        age == 0 -> "You're less than a year old!"
        age < 3 -> "You're $age ${if (age == 1) "year" else "years"} old! Just getting started!"
        age < 13 -> "You're $age years old! So young and bright!"
        age < 20 -> "You're $age years old! The teenage years are amazing!"
        age < 30 -> "You're $age years old! Young adult adventures await!"
        age < 40 -> "You're $age years old! The prime of life!"
        age < 60 -> "You're $age years old! Wisdom and experience!"
        else -> "You're $age years old! Very wise indeed!"
    }
