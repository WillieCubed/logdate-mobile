package app.logdate.feature.journals.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import app.logdate.ui.common.AspectRatios
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.sharing.SharingLauncher
import app.logdate.shared.model.Journal
import app.logdate.ui.common.MaterialContainer
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Screen for sharing a journal with others.
 * 
 * Provides options to share via the system share sheet or Instagram.
 *
 * @param journalId ID of the journal to share
 * @param onGoBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareJournalScreen(
    journalId: String,
    onGoBack: () -> Unit,
) {
    val viewModel = koinInject<ShareJournalViewModel>()
    val parsedId = remember(journalId) {
        kotlin.uuid.Uuid.parse(journalId)
    }
    
    // Set the journal ID in the ViewModel
    LaunchedEffect(parsedId) {
        viewModel.setJournalId(parsedId)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share journal") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ShareJournalUiState.Loading -> {
                // Show loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading...")
                }
            }
            is ShareJournalUiState.Error -> {
                // Show error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Failed to load journal")
                }
            }
            is ShareJournalUiState.Success -> {
                ShareJournalContent(
                    journal = state.journal,
                    lastUpdatedText = state.lastUpdatedDisplay,
                    onShareToInstagram = { viewModel.shareToInstagram(state.journal) },
                    onShareJournal = { viewModel.shareJournal(state.journal) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Content of the share journal screen.
 *
 * @param journal Journal to be shared
 * @param lastUpdatedText Text showing when the journal was last updated
 * @param onShareToInstagram Callback when sharing to Instagram
 * @param onShareJournal Callback when using general share sheet
 * @param modifier Modifier for this composable
 */
@Composable
private fun ShareJournalContent(
    journal: Journal,
    lastUpdatedText: String,
    onShareToInstagram: () -> Unit,
    onShareJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Journal preview
        JournalPreview(
            title = journal.title,
            lastUpdatedText = lastUpdatedText,
            modifier = Modifier
                .width(240.dp)
                .aspectRatio(AspectRatios.RATIO_3_4)
        )
        
        // Web availability text
        MaterialContainer {
            SurfaceItem {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "This journal is available on the web. Anyone with the link can view it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Share buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Instagram button
            Button(
                onClick = onShareToInstagram,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5F5F5),
                    contentColor = Color.Black
                )
            ) {
                // Instagram icon 
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // General share button
            Button(
                onClick = onShareJournal,
                modifier = Modifier
                    .weight(3f)
                    .height(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Share")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Bottom info about nearby sharing
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Also sharing to nearby LogDate contacts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bring your devices together to invite someone to add stuff.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        
        // Bottom indicator
        Divider(
            modifier = Modifier
                .width(32.dp)
                .padding(vertical = 16.dp),
            thickness = 4.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

/**
 * Journal preview card shown in the sharing screen.
 *
 * @param title Title of the journal
 * @param lastUpdatedText Text showing when the journal was last updated
 * @param modifier Modifier for this composable
 */
@Composable
private fun JournalPreview(
    title: String,
    lastUpdatedText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image preview (70% of height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                // This would be the journal cover image
                // For now we're using a placeholder color
            }
            
            // Journal title and last updated (30% of height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title.ifEmpty { "Untitled Journal" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = lastUpdatedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Data class for journal sharing information.
 *
 * @param journalId String ID of the journal to share
 */
data class JournalShareData(
    val journalId: String,
)