package app.logdate.feature.editor.ui.photovideo

import android.content.Context
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.aakira.napier.Napier

@Composable
fun rememberSurfaceRequestState(): MutableState<SurfaceRequest?> {
    return remember { mutableStateOf<SurfaceRequest?>(null) }
}

@Composable
actual fun LiveCameraPreview(
    canUseCamera: Boolean,
    cameraType: CameraType,
    modifier: Modifier,
) {
    if (!canUseCamera) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera permission not granted",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceRequestState = rememberSurfaceRequestState()
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val cameraSelector = remember(cameraType) {
        when (cameraType) {
            CameraType.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraType.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    val preview = remember {
        Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider { newSurfaceRequest ->
                    surfaceRequestState.value = newSurfaceRequest
                }
            }
    }

    LaunchedEffect(cameraType) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider

            provider.unbindAll()

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )

            Napier.d("LiveCameraPreview: Camera bound successfully with $cameraType")
        } catch (e: Exception) {
            Napier.e("LiveCameraPreview: Failed to bind camera", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            Napier.d("LiveCameraPreview: Camera unbound on dispose")
        }
    }

    val surfaceRequest = surfaceRequestState.value
    if (surfaceRequest != null) {
        CameraXViewfinder(
            surfaceRequest = surfaceRequest,
            implementationMode = ImplementationMode.EXTERNAL,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading camera...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
