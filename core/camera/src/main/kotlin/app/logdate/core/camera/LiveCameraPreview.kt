package app.logdate.core.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

enum class LensDirection {
    FRONT,
    BACK,
}

@Composable
fun LiveCameraPreview(
    canUseCamera: Boolean,
    lensDirection: LensDirection,
) {
    val lensFacing = when (lensDirection) {
        LensDirection.FRONT -> "front"
        LensDirection.BACK -> CameraSelector.LENS_FACING_BACK
    }
    val context = LocalContext.current
    val file = context.createImageFile()
    if (!canUseCamera) {
        Text("Camera permission not granted")
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