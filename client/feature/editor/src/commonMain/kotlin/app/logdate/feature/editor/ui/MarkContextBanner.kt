@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.ui.provenance.CaptureTimeLabel
import app.logdate.ui.theme.Spacing
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.mark_context_continue_new
import logdate.client.feature.editor.generated.resources.mark_context_label
import logdate.client.feature.editor.generated.resources.mark_context_unavailable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun MarkContextBanner(
    markId: String,
    refreshKey: Any?,
    onContinueAsNew: () -> Unit,
) {
    val marks: MarkedMomentRepository = koinInject()
    var resumeRevision by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { resumeRevision += 1 }
    val lookup by produceState<MarkContextLookup>(MarkContextLookup.Loading, markId, refreshKey, resumeRevision) {
        value =
            try {
                marks.getMark(Uuid.parse(markId))?.let(MarkContextLookup::Available) ?: MarkContextLookup.Unavailable
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Napier.w("Could not load marked editor context", failure)
                MarkContextLookup.Unavailable
            }
    }
    when (val current = lookup) {
        MarkContextLookup.Loading -> Unit
        MarkContextLookup.Unavailable -> MissingMarkContextContent(onContinueAsNew)
        is MarkContextLookup.Available -> MarkContextBannerContent(current.mark)
    }
}

private sealed interface MarkContextLookup {
    data object Loading : MarkContextLookup

    data object Unavailable : MarkContextLookup

    data class Available(
        val mark: MarkedMoment,
    ) : MarkContextLookup
}

@Composable
internal fun MissingMarkContextContent(onContinueAsNew: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
        Text(stringResource(Res.string.mark_context_unavailable), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onContinueAsNew) { Text(stringResource(Res.string.mark_context_continue_new)) }
    }
}

@Composable
fun MarkContextBannerContent(context: MarkedMoment) {
    val captured =
        context.capturedAt.toLocalDateTime(
            runCatching {
                TimeZone.of(context.timeZoneId)
            }.getOrDefault(TimeZone.currentSystemDefault()),
        )
    CaptureTimeLabel(
        text =
            stringResource(
                Res.string.mark_context_label,
                "${captured.date} ${captured.time.hour.toString().padStart(2, '0')}:${captured.time.minute.toString().padStart(2, '0')}",
            ),
        capturedOnWatch = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}
