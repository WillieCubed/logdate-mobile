@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_continue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

const val PERSONAL_INTRO_ROOT_TAG = "onboarding_personal_intro_root"
const val PERSONAL_INTRO_NAME_FIELD_TAG = "onboarding_personal_intro_name_field"
const val PERSONAL_INTRO_NAME_CONTINUE_TAG = "onboarding_personal_intro_name_continue"
const val PERSONAL_INTRO_BIO_FIELD_TAG = "onboarding_personal_intro_bio_field"
const val PERSONAL_INTRO_BIO_CONTINUE_TAG = "onboarding_personal_intro_bio_continue"

/**
 * Personal introduction screen that asks users to share their name and bio
 * in a playful, two-step flow with LLM-powered friendly responses.
 */
@Composable
fun PersonalIntroScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonalIntroViewModel = koinViewModel(),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        PersonalIntroContent(
            uiState = uiState,
            onNameChanged = viewModel::onNameChanged,
            onBioChanged = viewModel::onBioChanged,
            onProceedToBio = viewModel::proceedToBio,
            onGoBackToName = viewModel::goBackToName,
            onProcessWithLlm = viewModel::processWithLlm,
            onBack = onBack,
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        )
    }
}

@Composable
fun PersonalIntroContent(
    uiState: PersonalIntroUiState,
    onNameChanged: (String) -> Unit,
    onBioChanged: (String) -> Unit,
    onProceedToBio: () -> Unit,
    onGoBackToName: () -> Unit,
    onProcessWithLlm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocusInputs: Boolean = true,
    animateStepTransitions: Boolean = true,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .testTag(PERSONAL_INTRO_ROOT_TAG)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 444.dp)
                    .semantics {
                        contentDescription = PERSONAL_INTRO_ROOT_TAG
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Spacing.xl))

            AnimatedVisibility(
                visible = uiState.isProcessingLlm || uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = stringResource(Res.string.profile_photo),
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (animateStepTransitions) {
                        onboardingSlideTransition()
                    } else {
                        ContentTransform(EnterTransition.None, ExitTransition.None)
                    }
                },
                label = "Step Content",
            ) { step ->
                when (step) {
                    PersonalIntroStep.Name ->
                        NameStep(
                            name = uiState.name,
                            nameError = uiState.nameError,
                            canContinue = uiState.canContinueFromName,
                            onNameChanged = onNameChanged,
                            onContinue = onProceedToBio,
                            onBack = onBack,
                            autoFocus = autoFocusInputs,
                        )

                    PersonalIntroStep.Bio ->
                        BioStep(
                            bio = uiState.bio,
                            bioError = uiState.bioError,
                            canContinue = uiState.canContinueFromBio,
                            onBioChanged = onBioChanged,
                            onContinue = onProcessWithLlm,
                            onBack = onGoBackToName,
                            autoFocus = autoFocusInputs,
                        )

                    PersonalIntroStep.LlmResponse ->
                        LlmResponseStep(
                            userName = uiState.name,
                            llmResponse = uiState.llmResponse,
                            llmError = uiState.llmError,
                            isLoading = uiState.isLoading,
                        )
                }
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
    onBack: () -> Unit,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(autoFocus) {
        if (!autoFocus) {
            return@LaunchedEffect
        }

        delay(300) // Small delay for smooth animation
        focusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // Step indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = stringResource(Res.string.who_are_you),
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(Res.string.lets_start_with_what_we_should_call_you),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(Res.string.your_name)) },
            placeholder = { Text(stringResource(Res.string.account_username_display_name_prompt)) },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(PERSONAL_INTRO_NAME_FIELD_TAG)
                    .focusRequester(focusRequester),
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Words,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        focusManager.clearFocus()
                        if (canContinue) onContinue()
                    },
                ),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().testTag(PERSONAL_INTRO_NAME_CONTINUE_TAG),
        ) {
            Text(stringResource(UiRes.string.common_continue))
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(UiRes.string.common_back))
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
    onBack: () -> Unit,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (!autoFocus) {
            return@LaunchedEffect
        }

        delay(300) // Small delay for smooth animation
        focusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // Step indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = stringResource(Res.string.tell_us_a_bit_about_yourself),
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(Res.string.what_makes_you_you_share_whatever_feels_right),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        OutlinedTextField(
            value = bio,
            onValueChange = onBioChanged,
            label = { Text(stringResource(Res.string.about_you)) },
            placeholder = { Text(stringResource(Res.string.i_love_coffee_hiking_and_terrible_movies)) },
            isError = bioError != null,
            supportingText = bioError?.let { { Text(it) } },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag(PERSONAL_INTRO_BIO_FIELD_TAG)
                    .focusRequester(focusRequester),
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (canContinue) onContinue()
                    },
                ),
            maxLines = 4,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().testTag(PERSONAL_INTRO_BIO_CONTINUE_TAG),
        ) {
            Text(stringResource(Res.string.lets_see_what_i_think))
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(UiRes.string.common_back))
        }
    }
}

@Composable
private fun LlmResponseStep(
    userName: String,
    llmResponse: String?,
    llmError: String?,
    isLoading: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // Step indicator (complete)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        AnimatedContent(
            targetState = llmResponse != null || llmError != null,
            transitionSpec = { onboardingFadeTransition() },
            label = "Response Content",
        ) { hasResponse ->
            if (hasResponse) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Text(
                        text =
                            stringResource(
                                Res.string.nice_to_meet_you_name,
                                userName,
                            ),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.shapes.large,
                                ).padding(Spacing.lg),
                    ) {
                        Text(
                            text = llmResponse ?: llmError ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (llmError != null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (isLoading) {
                        Text(
                            text = stringResource(Res.string.setting_up_your_profile),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.ready_to_start_your_journaling_journey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    CircularProgressIndicator()

                    Text(
                        text = stringResource(Res.string.let_me_think_about_that),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PersonalIntroContentPreview_Name() {
    LogDateTheme {
        PersonalIntroContent(
            uiState = PersonalIntroUiState(currentStep = PersonalIntroStep.Name),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PersonalIntroContentPreview_Bio() {
    LogDateTheme {
        PersonalIntroContent(
            uiState =
                PersonalIntroUiState(
                    currentStep = PersonalIntroStep.Bio,
                    name = "Willie",
                ),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PersonalIntroContentPreview_LlmResponse() {
    LogDateTheme {
        PersonalIntroContent(
            uiState =
                PersonalIntroUiState(
                    currentStep = PersonalIntroStep.LlmResponse,
                    name = "Willie",
                    bio = "I love coffee, hiking, and terrible movies.",
                    llmResponse =
                        "Nice to meet you! Your journey through coffee, trails, and " +
                            "cinematic disasters sounds like a story worth capturing.",
                ),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}
