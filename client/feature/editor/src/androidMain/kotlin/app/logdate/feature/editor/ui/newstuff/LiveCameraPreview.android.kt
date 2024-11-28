package app.logdate.feature.editor.ui.newstuff

import android.content.Context
import android.icu.text.SimpleDateFormat
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Date

@Composable
actual fun LiveCameraPreview(
    canUseCamera: Boolean,
    lensDirection: LensDirection,
) {
    val lensFacing = when (lensDirection) {
        LensDirection.FRONT -> "front"
        LensDirection.BACK -> CameraSelector.LENS_FACING_BACK
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = androidx.camera.core.Preview.Builder()
        .build()
    val previewView = remember {
        PreviewView(context)
    }
    val file = context.createImageFile()
    if (!canUseCamera) {
        Text("Camera permission not granted")
    }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
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
