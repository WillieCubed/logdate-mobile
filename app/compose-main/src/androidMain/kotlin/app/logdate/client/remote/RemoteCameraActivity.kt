package app.logdate.client.remote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.PhoneWearSyncBridge
import app.logdate.client.sync.datalayer.RemoteCameraCaptureResult
import app.logdate.feature.editor.ui.camera.CameraCaptureContent
import app.logdate.feature.editor.ui.camera.CameraRemoteControl
import app.logdate.feature.editor.ui.camera.CapturedMediaType
import app.logdate.ui.theme.LogDateTheme
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlin.time.Clock

class RemoteCameraActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LogDateTheme {
                CameraCaptureContent(
                    onMediaCaptured = { uri, mediaType, _ ->
                        Napier.i("Remote camera captured $mediaType: $uri")
                        saveCapturedMedia(uri, mediaType)
                    },
                    onClose = { finish() },
                    modifier = Modifier.fillMaxSize(),
                    remoteControl =
                        CameraRemoteControl(
                            commands = RemoteCameraSessionController.commands,
                            autoAcceptCapturedMedia = true,
                            onCameraSelectionChanged = ::handleCameraSelectionChanged,
                            onCaptureCompleted = { uri, mediaType ->
                                Napier.i("Remote camera accepted $mediaType capture: $uri")
                            },
                            onCaptureFailed = { message ->
                                Napier.w("Remote camera capture failed: $message")
                                publishRemoteCameraCaptureResult(
                                    RemoteCameraCaptureResult(
                                        isSaved = false,
                                        message = message.ifBlank { "Remote camera capture failed" },
                                    ),
                                )
                            },
                        ),
                )
            }
        }
    }

    private fun handleCameraSelectionChanged(selection: MediaDeviceSelectionUiState) {
        RemoteCameraSessionController.updateCameraSelection(selection)
        lifecycleScope.launch {
            runCatching {
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<PhoneWearSyncBridge>()
                    .publishRemoteCameraDevices(selection)
            }.onFailure { error ->
                Napier.w("Remote camera device list could not be published to watch", error)
            }
        }
    }

    private fun saveCapturedMedia(
        uri: String,
        mediaType: CapturedMediaType,
    ) {
        lifecycleScope.launch {
            try {
                val notesRepository =
                    org.koin.java.KoinJavaComponent
                        .getKoin()
                        .get<JournalNotesRepository>()
                notesRepository.create(uri.toRemoteJournalNote(mediaType))
                publishRemoteCameraCaptureResult(
                    RemoteCameraCaptureResult(
                        isSaved = true,
                        message = mediaType.savedMessage,
                        mediaType = mediaType.remoteResultValue,
                    ),
                )
                Toast
                    .makeText(
                        this@RemoteCameraActivity,
                        mediaType.savedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
            } catch (e: Exception) {
                Napier.w("Remote camera capture could not be saved", e)
                publishRemoteCameraCaptureResult(
                    RemoteCameraCaptureResult(
                        isSaved = false,
                        message = "Could not save remote camera capture",
                        mediaType = mediaType.remoteResultValue,
                    ),
                )
                Toast
                    .makeText(
                        this@RemoteCameraActivity,
                        "Could not save remote camera capture",
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    private fun publishRemoteCameraCaptureResult(result: RemoteCameraCaptureResult) {
        lifecycleScope.launch {
            runCatching {
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<PhoneWearSyncBridge>()
                    .publishRemoteCameraCaptureResult(result)
            }.onFailure { error ->
                Napier.w("Remote camera capture result could not be published to watch", error)
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, RemoteCameraActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}

private fun String.toRemoteJournalNote(mediaType: CapturedMediaType): JournalNote {
    val now = Clock.System.now()
    return when (mediaType) {
        CapturedMediaType.PHOTO ->
            JournalNote.Image(
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = this,
            )
        CapturedMediaType.VIDEO ->
            JournalNote.Video(
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = this,
            )
    }
}

private val CapturedMediaType.savedMessage: String
    get() =
        when (this) {
            CapturedMediaType.PHOTO -> "Remote photo saved"
            CapturedMediaType.VIDEO -> "Remote video saved"
        }

private val CapturedMediaType.remoteResultValue: String
    get() =
        when (this) {
            CapturedMediaType.PHOTO -> "photo"
            CapturedMediaType.VIDEO -> "video"
        }
