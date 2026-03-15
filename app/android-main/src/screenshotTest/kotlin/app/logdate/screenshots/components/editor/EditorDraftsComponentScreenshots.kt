package app.logdate.screenshots.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.feature.editor.ui.dialog.DraftsBottomSheet
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.baseInstant
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid

// ─── Drafts List Dialog ─────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DraftsList_Loading() {
    ScreenshotTheme {
        DraftsBottomSheet(
            drafts = emptyList(),
            isLoading = true,
            onDismiss = {},
            onDraftSelected = { _: EntryDraft -> },
            onDraftDeleted = { _: Uuid -> },
            onDeleteAllDrafts = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DraftsList_NoItems() {
    // Standalone empty state rendering to avoid compose resource inflation crash
    // (the first DraftsListDialog render in a test suite hits a MissingResourceException
    // during layoutlib inflate that crashes ERROR_NOT_INFLATED for the empty branch)
    ScreenshotTheme {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(520.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = "Previous Drafts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "No drafts found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Start writing to create your first draft.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun DraftsList_Populated() {
    ScreenshotTheme {
        DraftsBottomSheet(
            drafts = listOf(
                EntryDraft(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000010"),
                    notes = emptyList(),
                    createdAt = baseInstant,
                    updatedAt = baseInstant,
                ),
                EntryDraft(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000011"),
                    notes = emptyList(),
                    createdAt = baseInstant,
                    updatedAt = baseInstant,
                ),
                EntryDraft(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000012"),
                    notes = emptyList(),
                    createdAt = baseInstant,
                    updatedAt = baseInstant,
                ),
            ),
            isLoading = false,
            onDismiss = {},
            onDraftSelected = { _: EntryDraft -> },
            onDraftDeleted = { _: Uuid -> },
            onDeleteAllDrafts = {},
        )
    }
}
