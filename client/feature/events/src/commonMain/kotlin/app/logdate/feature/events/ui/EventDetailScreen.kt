@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Uuid,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Event") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is EventDetailUiState.Loaded) {
                        IconButton(onClick = { viewModel.delete(onGoBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete event")
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        when (val state = uiState) {
            EventDetailUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            EventDetailUiState.NotFound -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Event not found", style = MaterialTheme.typography.bodyLarge)
                }
            }

            is EventDetailUiState.Loaded -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(Spacing.lg)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    OutlinedTextField(
                        value = state.event.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxSize(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.event.description.orEmpty(),
                        onValueChange = viewModel::updateDescription,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxSize(),
                        minLines = 3,
                    )
                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && state.event.title.isNotBlank(),
                    ) {
                        Text(if (state.isSaving) "Saving…" else "Save")
                    }
                }
            }
        }
    }
}
