package app.logdate.feature.editor.ui.newstuff

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.logdate.ui.theme.Spacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


/**
 * A wrapper container for a note editor
 */
@Composable
internal fun NoteEditorSurface(
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .animateContentSize()
            .widthIn(max = 600.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        children()
    }
}

/**
 * An editor for text-based notes.
 *
 * Should be wrapped in a [NoteEditorSurface].
 */
@Composable
internal fun TextNoteEditor(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val keyboardOptions = remember {
        KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
        )
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (textFieldState.text.isEmpty()) {
            Text(
                "Write something…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = 16.dp, y = 16.dp),
            )
        }
        BasicTextField(
            modifier = Modifier
                .padding(Spacing.lg)
                .fillMaxSize(),
            enabled = enabled,
            state = textFieldState,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            interactionSource = interactionSource,
            decorator = { innerTextField ->
                innerTextField()
            },
        )
    }
}


enum class LensDirection {
    FRONT,
    BACK,
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    val provider = ProcessCameraProvider.awaitInstance(this)
    return provider
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

@Composable
internal fun VisualNoteEditor(cameraEnabled: Boolean = true) {
    val canUseCamera by remember {
        derivedStateOf { cameraEnabled }
    }
    Box {
        LiveCameraPreview(canUseCamera = canUseCamera, lensDirection = LensDirection.BACK)
    }
}

@Preview
@Composable
private fun TextNoteEditorPreview() {
    NoteEditorSurface {
        TextNoteEditor(
            textFieldState = TextFieldState()
        )
    }
}

@Preview
@Composable
private fun VisualNoteEditorPreview() {
    NoteEditorSurface {
        VisualNoteEditor()
    }
}
