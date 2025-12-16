package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Personal introduction screen that asks users to share their name and bio
 * in a playful, two-step flow with LLM-powered friendly responses.
 */
@Composable
fun PersonalIntroScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonalIntroViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    
    // Auto-advance when LLM processing and saving is complete
    LaunchedEffect(uiState.canFinish) {
        if (uiState.canFinish && !uiState.isLoading) {
            // Brief delay to let user read the LLM response
            delay(2000)
            viewModel.completeIntroduction()
            if (!uiState.isLoading && uiState.errorMessage == null) {
                onNext()
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PersonalIntroContent(
            uiState = uiState,
            onNameChanged = viewModel::onNameChanged,
            onBioChanged = viewModel::onBioChanged,
            onProceedToBio = viewModel::proceedToBio,
            onGoBackToName = viewModel::goBackToName,
            onProcessWithLlm = viewModel::processWithLlm,
            onBack = onBack,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun PersonalIntroContent(
    uiState: PersonalIntroUiState,
    onNameChanged: (String) -> Unit,
    onBioChanged: (String) -> Unit,
    onProceedToBio: () -> Unit,
    onGoBackToName: () -> Unit,
    onProcessWithLlm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Spacing.xl))
        
        // Progress indicator
        AnimatedVisibility(
            visible = uiState.isProcessingLlm || uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.xl))
        
        // Cookie-shaped profile placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile photo",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.xl))
        
        // Animated step content
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            },
            label = "Step Content"
        ) { step ->
            when (step) {
                PersonalIntroStep.Name -> NameStep(
                    name = uiState.name,
                    nameError = uiState.nameError,
                    canContinue = uiState.canContinueFromName,
                    onNameChanged = onNameChanged,
                    onContinue = onProceedToBio,
                    onBack = onBack
                )
                
                PersonalIntroStep.Bio -> BioStep(
                    bio = uiState.bio,
                    bioError = uiState.bioError,
                    canContinue = uiState.canContinueFromBio,
                    onBioChanged = onBioChanged,
                    onContinue = onProcessWithLlm,
                    onBack = onGoBackToName
                )
                
                PersonalIntroStep.LlmResponse -> LlmResponseStep(
                    userName = uiState.name,
                    llmResponse = uiState.llmResponse,
                    llmError = uiState.llmError,
                    isLoading = uiState.isLoading
                )
            }
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    nameError: String?,
    canContinue: Boolean,
    onNameChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(Unit) {
        delay(300) // Small delay for smooth animation
        focusRequester.requestFocus()
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Step indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.md))
        
        Text(
            text = "Who are you?",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Let's start with what we should call you",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(Spacing.lg))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text("Your name") },
            placeholder = { Text("What should we call you?") },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                capitalization = KeyboardCapitalization.Words
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.clearFocus()
                    if (canContinue) onContinue()
                }
            ),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(Spacing.xl))
        
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
        
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun BioStep(
    bio: String,
    bioError: String?,
    canContinue: Boolean,
    onBioChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        delay(300) // Small delay for smooth animation
        focusRequester.requestFocus()
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Step indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.md))
        
        Text(
            text = "Tell us a bit about yourself!",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "What makes you, you? Share whatever feels right",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(Spacing.lg))
        
        OutlinedTextField(
            value = bio,
            onValueChange = onBioChanged,
            label = { Text("About you") },
            placeholder = { Text("I love coffee, hiking, and terrible movies...") },
            isError = bioError != null,
            supportingText = bioError?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (canContinue) onContinue()
                }
            ),
            maxLines = 4
        )
        
        Spacer(modifier = Modifier.height(Spacing.xl))
        
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Let's see what I think!")
        }
        
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun LlmResponseStep(
    userName: String,
    llmResponse: String?,
    llmError: String?,
    isLoading: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Step indicator (complete)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.md))
        
        AnimatedContent(
            targetState = llmResponse != null || llmError != null,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith 
                (fadeOut() + scaleOut(targetScale = 0.8f))
            },
            label = "Response Content"
        ) { hasResponse ->
            if (hasResponse) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Text(
                        text = "Nice to meet you, $userName!",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.shapes.large
                            )
                            .padding(Spacing.lg)
                    ) {
                        Text(
                            text = llmResponse ?: llmError ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (llmError != null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    if (isLoading) {
                        Text(
                            text = "Setting up your profile...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Ready to start your journaling journey!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    CircularProgressIndicator()
                    
                    Text(
                        text = "Let me think about that...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}