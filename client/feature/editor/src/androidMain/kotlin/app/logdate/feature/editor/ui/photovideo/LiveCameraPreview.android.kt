package app.logdate.feature.editor.ui.photovideo

import android.content.Context
import android.icu.text.SimpleDateFormat
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.io.File
import java.util.Date

@Composable
fun rememberSurfaceRequestState(): MutableState<SurfaceRequest?> {
    val surfaceRequestState = remember { mutableStateOf<SurfaceRequest?>(null) }
    return surfaceRequestState
}


@Composable
actual fun LiveCameraPreview(
    canUseCamera: Boolean,
    cameraType: CameraType,
    modifier: Modifier,
) {
    val surfaceRequestState = rememberSurfaceRequestState()
    val preview by remember(cameraType) {
        derivedStateOf {

//        val isPreviewStabilizationSupported =
//            Preview.getPreviewCapabilities(cameraProvider.getCameraInfo(cameraSelector))
//                .isStabilizationSupported
            Preview.Builder().apply {
//            if (isPreviewStabilizationSupported) {
                setPreviewStabilizationEnabled(true)
//            }
            }.build()
        }
    }
// Set up the surface provider
    LaunchedEffect(preview) {
        preview.setSurfaceProvider { newSurfaceRequest ->
            surfaceRequestState.value = newSurfaceRequest
        }
    }

    val lensFacing = when (cameraType) {
        CameraType.FRONT -> "front"
        CameraType.BACK -> CameraSelector.LENS_FACING_BACK
    }
    if (!canUseCamera) {
        Text("Camera permission not granted")
    }

// Render the viewfinder when we have a surface request
//    surfaceRequestState.value?.let { surfaceRequest ->
//        Viewfinder(
//            surfaceRequest = surfaceRequest,
//            implementationMode = ImplementationMode.EXTERNAL,
//            transformationInfo = TransformationInfo(
//                sourceRotation = 0,
//                cropRectLeft = 0,
//                cropRectTop = 0,
//                cropRectRight = 0,
//                cropRectBottom = 0,
//                shouldMirror = cameraType == CameraType.FRONT
//            ),
//            modifier = modifier
//        )
//    }
// TODO: Show placeholder if no surface request exists
    val surfaceRequest = surfaceRequestState.value
    surfaceRequest?.let {
        CameraXViewfinder(
            surfaceRequest = surfaceRequest,
            implementationMode = ImplementationMode.EXTERNAL, // Or EMBEDDED
            modifier = modifier
        )
    }
}

// TODO: Move somewhere else
fun Context.createImageFile(): File {
    val timeStamp = SimpleDateFormat.getDateTimeInstance().format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    return File.createTempFile(
        imageFileName, /* prefix */
        ".jpg", /* suffix */
        externalCacheDir      /* directory */
    )
}


private suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    val provider = ProcessCameraProvider.awaitInstance(this)
    return provider
}