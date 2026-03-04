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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.save
import logdate.client.feature.core.generated.resources.this_helps_us_personalize_your_experience
import logdate.client.feature.core.generated.resources.when_were_you_born
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A dialog for selecting the user's birthday with a date picker.
 *
 * @param initialBirthday The current birthday value
 * @param onDismiss Callback when the dialog is dismissed
 * @param onSave Callback when the user saves their birthday
 * @param birthdayUpdateState The current state of the birthday update operation
 * @param onResetBirthdayUpdateState Callback to reset the birthday update state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayPickerDialog(
    initialBirthday: Instant,
    onDismiss: () -> Unit,
    onSave: (Instant) -> Unit,
    birthdayUpdateState: BirthdayUpdateState,
    onResetBirthdayUpdateState: () -> Unit,
) {
    val resolvedInitial =
        remember {
            if (initialBirthday == Instant.DISTANT_PAST) {
                Clock.System.now().minus(7305.days)
            } else {
                initialBirthday
            }
        }

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = resolvedInitial.toEpochMilliseconds(),
        )

    val hasChanges by remember(datePickerState.selectedDateMillis) {
        derivedStateOf {
            val selectedInstant =
                datePickerState.selectedDateMillis?.let {
                    Instant.fromEpochMilliseconds(it)
                }
            selectedInstant != resolvedInitial && selectedInstant != null
        }
    }

    val selectedBirthday by remember(datePickerState.selectedDateMillis) {
        derivedStateOf {
            datePickerState.selectedDateMillis?.let { millis ->
                Instant.fromEpochMilliseconds(millis)
            }
        }
    }

    val age by remember(selectedBirthday) {
        derivedStateOf {
            selectedBirthday?.let { calculateAge(it) } ?: 0
        }
    }

    val ageMessage by remember(age) {
        derivedStateOf {
            formatAgeMessage(age)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Cake,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.when_were_you_born),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.this_helps_us_personalize_your_experience),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                DatePicker(
                    state = datePickerState,
                    title = null,
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = ageMessage.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.sm),
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onSave(Instant.fromEpochMilliseconds(millis))
                    }
                },
                enabled = hasChanges,
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
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
