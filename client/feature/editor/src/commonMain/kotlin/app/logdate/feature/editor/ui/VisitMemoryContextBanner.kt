@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.location.VisitMemoryContext
import app.logdate.util.toReadableDateShort
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_visit_memory_context
import logdate.client.feature.editor.generated.resources.editor_visit_memory_unknown_place
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VisitMemoryContextBanner(context: VisitMemoryContext) {
    Surface(modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(
                Res.string.editor_visit_memory_context,
                context.placeName ?: stringResource(Res.string.editor_visit_memory_unknown_place),
                context.visitStart.toReadableDateShort(),
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
