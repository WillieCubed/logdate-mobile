@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.core.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.`continue`
import logdate.client.feature.core.generated.resources.display_name
import logdate.client.feature.core.generated.resources.enter_your_name
import logdate.client.feature.core.generated.resources.examples
import logdate.client.feature.core.generated.resources.go_back
import logdate.client.feature.core.generated.resources.text_1_of_3
import logdate.client.feature.core.generated.resources.this_is_how_your_name_will_appear_to_others
import logdate.client.feature.core.generated.resources.what_should_we_call_you
import logdate.client.feature.core.generated.resources.your_display_name_is_how_youll_appear_to_others_when_sharing_journal_entries_you_can_always_change_this_later
import org.jetbrains.compose.resources.stringResource

@Composable
fun DisplayNameSetupScreen(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    isValid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    DisplayNameSetupContent(
        displayName = displayName,
        onDisplayNameChange = onDisplayNameChange,
        onContinue = onContinue,
        onBack = onBack,
        isValid = isValid,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayNameSetupContent(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    isValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.go_back),
                    )
                }

                LinearProgressIndicator(
                    progress = { 0.33f }, // Step 1 of 3
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.md),
                )

                Text(
                    text = stringResource(Res.string.text_1_of_3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Title and description
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.what_should_we_call_you),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                val displayNameInfo =
                    stringResource(
                        Res.string
                            .your_display_name_is_how_youll_appear_to_others_when_sharing_journal_entries_you_can_always_change_this_later,
                    )
                Text(
                    text = displayNameInfo,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            // Input field
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    label = { Text(stringResource(Res.string.display_name)) },
                    placeholder = { Text(stringResource(Res.string.enter_your_name)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                        )
                    },
                    supportingText = {
                        Text(stringResource(Res.string.this_is_how_your_name_will_appear_to_others))
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            capitalization = KeyboardCapitalization.Words,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isValid && displayName.isNotBlank()) {
                                    onContinue()
                                }
                            },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )

                // Examples card
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = stringResource(Res.string.examples),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "• Alex Johnson\n• Sarah M.\n• Coffee Lover\n• The Wanderer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Continue button
        Button(
            onClick = onContinue,
            enabled = isValid && displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.`continue`))
        }
    }
}

@Preview
@Composable
private fun DisplayNameSetupScreenPreview() {
    MaterialTheme {
        Surface {
            DisplayNameSetupContent(
                displayName = "Alex Johnson",
                onDisplayNameChange = {},
                onContinue = {},
                onBack = {},
                isValid = true,
            )
        }
    }
}

@Preview
@Composable
private fun DisplayNameSetupScreenEmptyPreview() {
    MaterialTheme {
        Surface {
            DisplayNameSetupContent(
                displayName = "",
                onDisplayNameChange = {},
                onContinue = {},
                onBack = {},
                isValid = true,
            )
        }
    }
}
