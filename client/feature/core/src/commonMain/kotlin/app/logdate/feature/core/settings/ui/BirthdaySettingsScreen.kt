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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.floor
import kotlin.time.Duration.Companion.days

/**
 * A full-screen birthday selection screen with Material You styling.
 * 
 * @param onBack Callback for when the user navigates back
 * @param viewModel The settings view model
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    // Get initial birthday from userData just once
    val initialBirthday = remember {
        val currentBirthday = viewModel.uiState.value.userData.birthday
        if (currentBirthday == Instant.DISTANT_PAST) {
            // Approximately 20 years in days (365.25 days/year * 20 years)
            Clock.System.now().minus(7305.days)
        } else {
            currentBirthday
        }
    }
    
    // Setup date picker with the initial date
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialBirthday.toEpochMilliseconds()
    )
    
    // Track if the birthday has been changed from its initial value
    val hasChanges by remember(datePickerState.selectedDateMillis) {
        derivedStateOf {
            val selectedInstant = datePickerState.selectedDateMillis?.let { 
                Instant.fromEpochMilliseconds(it) 
            }
            selectedInstant != initialBirthday && selectedInstant != null
        }
    }
    
    // Calculate and format the user's age
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
    
    // Handle save action when changes are made
    val onSave = {
        // Always save the selected birthday when saving, regardless of changes
        datePickerState.selectedDateMillis?.let { millis ->
            val birthdayInstant = Instant.fromEpochMilliseconds(millis)
            Napier.d("BirthdayScreen: Save clicked with date $birthdayInstant")
            // Update the birthday and immediately navigate back
            viewModel.updateBirthday(birthdayInstant)
        } ?: run {
            Napier.w("BirthdayScreen: Save clicked but no date selected")
        }
        // Navigate back after saving
        onBack()
    }
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = Modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Birthday") },
                navigationIcon = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Save and go back")
                    }
                },
                actions = {
                    // Only show save button if changes have been made
                    if (hasChanges) {
                        IconButton(onClick = onSave) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Title with fun icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Text(
                        text = "When were you born?",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Text(
                    text = "This helps us personalize your experience",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Date picker with Material You styling - using weight(1f) to fill available space
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // This makes the card take up all available space
                        .padding(vertical = Spacing.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier
                                .fillMaxSize() // Fill the entire Card
                                .padding(Spacing.md),
                            title = null // Remove title as we have our own
                        )
                    }
                }
                
                // Age message with animation and celebration icon
                AnimatedVisibility(
                    visible = ageMessage.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Celebration,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.size(Spacing.md))
                            
                            Text(
                                text = ageMessage,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Small spacing before save button
                Spacer(modifier = Modifier.height(Spacing.lg))
                
                // Save button
                FilledTonalButton(
                    onClick = onSave,
                    enabled = hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xl)
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            "Save Birthday",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculate age in years from a birthday Instant
 */
private fun calculateAge(birthday: Instant): Int {
    val now = Clock.System.now()
    val duration = now - birthday
    return floor(duration.inWholeDays / 365.25).toInt()
}

/**
 * Format an age message based on the age
 */
private fun formatAgeMessage(age: Int): String {
    return when {
        age < 0 -> "" // Future date, don't show message
        age == 0 -> "You're less than a year old!"
        age < 3 -> "You're $age ${if (age == 1) "year" else "years"} old! Just getting started!"
        age < 13 -> "You're $age years old! So young and bright!"
        age < 20 -> "You're $age years old! The teenage years are amazing!"
        age < 30 -> "You're $age years old! Young adult adventures await!"
        age < 40 -> "You're $age years old! The prime of life!"
        age < 60 -> "You're $age years old! Wisdom and experience!"
        else -> "You're $age years old! Very wise indeed!"
    }
}