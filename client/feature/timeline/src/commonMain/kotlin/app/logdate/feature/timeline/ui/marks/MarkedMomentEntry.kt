@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.marks

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.logdate.client.domain.moments.AttachPhotoToMarkUseCase
import app.logdate.client.domain.moments.MarkPhotoSuggestions
import app.logdate.client.domain.moments.ObserveMarkPhotoSuggestionsUseCase
import app.logdate.client.permissions.rememberMediaLibraryPermissionState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.ui.common.AlertDialog
import kotlinx.coroutines.CancellationException
import logdate.client.feature.timeline.generated.resources.Res
import logdate.client.feature.timeline.generated.resources.mark_cancel
import logdate.client.feature.timeline.generated.resources.mark_delete
import logdate.client.feature.timeline.generated.resources.mark_delete_message
import logdate.client.feature.timeline.generated.resources.mark_delete_title
import logdate.client.feature.timeline.generated.resources.mark_save_failed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Composable
fun MarkedMomentEntry(
    mark: MarkedMoment,
    onWriteMore: (Uuid) -> Unit,
) {
    val marks: MarkedMomentRepository = koinInject()
    val suggestions: ObserveMarkPhotoSuggestionsUseCase = koinInject()
    val attach: AttachPhotoToMarkUseCase = koinInject()
    val saveCaption: app.logdate.client.domain.moments.SaveMarkCaptionUseCase = koinInject()
    val enrich: app.logdate.client.domain.moments.EnrichMarkLocationUseCase = koinInject()
    val places: app.logdate.client.repository.places.UserPlacesRepository = koinInject()
    androidx.compose.runtime.LaunchedEffect(mark.id) {
        try {
            enrich(mark.id)
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (
            failure: Exception,
        ) {
            io.github.aakira.napier.Napier
                .w("Could not enrich marked moment place", failure)
        }
    }
    val placeName by produceState<String?>(null, mark.location) {
        val location = mark.location
        if (location != null) {
            try {
                value = places.getPlacesNear(location.latitude, location.longitude, 100.0).firstOrNull()?.name
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                failure: Exception,
            ) {
                io.github.aakira.napier.Napier
                    .w("Could not read marked moment place label", failure)
            }
        }
    }
    val viewModel: MarkedMomentViewModel =
        org.koin.compose.viewmodel.koinViewModel(
            key = "mark-${mark.id}",
            parameters = {
                org.koin.core.parameter
                    .parametersOf(mark)
            },
        )
    val editing by viewModel.state.collectAsState()
    val caption = editing.caption
    val busy = editing.busy
    var confirmDelete by remember { mutableStateOf(false) }
    val access = rememberMediaLibraryPermissionState(includeVideos = false)
    val permissions: app.logdate.client.permissions.PermissionManager = koinInject()
    var resumeRevision by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { resumeRevision += 1 }
    val photos by produceState<MarkPhotoSuggestions>(
        MarkPhotoSuggestions.Loading,
        mark.id,
        access.hasPermission,
        access.permissionRequested,
        access.refreshRevision,
        resumeRevision,
    ) {
        suggestions(mark).collect { value = it }
    }
    val failedMessage = stringResource(Res.string.mark_save_failed)

    fun mutate(
        action: suspend (String) -> Boolean,
        onSuccess: () -> Unit = {},
    ) {
        viewModel.submit(action, onSuccess)
    }
    MarkedMomentContent(
        mark,
        placeName,
        caption,
        viewModel::editCaption,
        photos,
        busy,
        if (editing.failed) failedMessage else null,
        onRequestAccess = {
            if (photos is MarkPhotoSuggestions.Limited) permissions.openPermissionSettings() else access.requestPermission()
        },
        onAttach = { uri -> mutate({ caption -> attach(mark.id, uri, caption) }) },
        onSaveCaption = { mutate({ caption -> saveCaption(mark.id, caption) }) },
        onWriteMore = { mutate({ true }, { onWriteMore(mark.id) }) },
        onLeave = { mutate({ marks.dismiss(mark.id, Clock.System.now()) }) },
        onDelete = { confirmDelete = true },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.mark_delete_title)) },
            text = { Text(stringResource(Res.string.mark_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    mutate({ marks.delete(mark.id, Clock.System.now()) })
                }) { Text(stringResource(Res.string.mark_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.mark_cancel)) } },
        )
    }
}
