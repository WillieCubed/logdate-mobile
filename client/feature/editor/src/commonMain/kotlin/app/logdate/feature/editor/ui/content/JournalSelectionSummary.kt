package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/** Selection context while capture owns the editor. No editing callbacks are supplied. */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun JournalSelectionSummary(
    journals: List<Journal>,
    selectedIds: List<Uuid>,
) {
    val titles = journals.filter { it.id in selectedIds }.joinToString { it.title }
    if (titles.isBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { disabled() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.heightIn(min = 72.dp).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titles, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
