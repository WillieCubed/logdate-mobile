@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * A panel displaying a text journal note in story format.
 *
 * Renders a journal text entry with date information and stylized presentation.
 * Designed to showcase written content in an elegant, readable format.
 *
 * ## Visual Design:
 * - **Content Area**: Padded container with subtle background
 * - **Typography**: Clear, readable text styles with appropriate sizing
 * - **Date Display**: Secondary emphasis for contextual information
 * - **Background Options**: Supports both solid colors and image backgrounds
 *
 * @param content The text content from the journal note
 * @param dateFormatted Formatted date string showing when the note was created
 * @param backgroundColor Solid background color (used when no image provided)
 * @param backgroundImageUri Optional background image URI
 * @param modifier Modifier for customizing the panel container
 */
@Composable
fun TextNotePanel(
    content: String,
    dateFormatted: String,
    backgroundColor: Color,
    backgroundImageUri: String?,
    modifier: Modifier = Modifier,
    memoryId: String = "text-memory",
) {
    RewindMemoryStage(modifier) { availableHeight ->
        RewindMemoryFrame(
            memoryId,
            RewindMemoryLoadState.Ready,
            Modifier
                .widthIn(max = RewindReadingMaxWidth)
                .fillMaxWidth()
                .heightIn(min = minOf(240.dp, availableHeight), max = availableHeight),
            backgroundColor = backgroundColor,
        ) {
            if (backgroundImageUri != null) {
                AsyncImage(backgroundImageUri, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(if (backgroundImageUri != null) Color.Black.copy(alpha = 0.6f) else Color.Transparent)
                    .padding(RewindMemoryContentPadding)
                    .testTag("rewind-reading-content"),
                horizontalAlignment = Alignment.Start,
            ) {
                RewindRichMemoryText(
                    content = content,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    dateFormatted,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
