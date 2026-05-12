@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.platform.PlatformIcons
import org.koin.compose.viewmodel.koinViewModel

/**
 * One-line affordance shown atop the timeline whenever there are sync writes that exceeded
 * their retry budget. Tapping routes to [SyncIssuesScreen]. Renders nothing when the queue
 * is empty.
 */
@Composable
fun SyncIssuesBanner(
    onOpenSyncIssues: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncIssuesViewModel = koinViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val count = records.size
    if (count == 0) return

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onClick = onOpenSyncIssues,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painter = PlatformIcons.syncProblem(), contentDescription = null)
            Text(
                modifier = Modifier.weight(1f),
                text =
                    if (count == 1) {
                        "1 change needs attention"
                    } else {
                        "$count changes need attention"
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(painter = PlatformIcons.chevronRight(), contentDescription = null)
        }
    }
}
