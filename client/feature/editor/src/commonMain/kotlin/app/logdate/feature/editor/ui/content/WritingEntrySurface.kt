@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.handwriting.HandwritingCanvas
import app.logdate.feature.editor.ui.handwriting.HandwritingPage
import app.logdate.feature.editor.ui.handwriting.handwritingSharedElement
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.ink_handwrite_action
import logdate.client.feature.editor.generated.resources.ink_type_action
import logdate.client.feature.editor.generated.resources.ink_write_action
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun WritingEntrySurface(
    onType: () -> Unit,
    onHandwrite: () -> Unit,
    handwritingId: Uuid,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).testTag("editor_writing_tile"),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.ink_write_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            WritingAction(stringResource(Res.string.ink_type_action), onType, "editor_start_text_block", false)
            WritingAction(
                stringResource(Res.string.ink_handwrite_action),
                onHandwrite,
                "editor_start_handwriting_block",
                true,
                handwritingId,
            )
        }
    }
}

@Composable
private fun WritingAction(
    label: String,
    onClick: () -> Unit,
    tag: String,
    handwriting: Boolean,
    handwritingId: Uuid? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            if (handwritingId != null) {
                HandwritingCanvas(
                    HandwritingPage(),
                    Modifier.matchParentSize().handwritingSharedElement(handwritingId, 0f),
                    ruled = false,
                    description = "",
                    paperColor = Color.Transparent,
                )
            }
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (handwriting) PlatformIcons.edit() else PlatformIcons.text(), null, Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
