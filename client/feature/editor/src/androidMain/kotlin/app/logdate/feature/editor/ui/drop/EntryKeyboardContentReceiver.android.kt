@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalComposeUiApi::class)

package app.logdate.feature.editor.ui.drop

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
actual fun EntryKeyboardContentReceiver(
    onMediaImported: (List<String>) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val mediaManager: MediaManager = koinInject()
    val scope = rememberCoroutineScope()
    val currentOnMediaImported by rememberUpdatedState(onMediaImported)
    val interceptor =
        remember(context, mediaManager, scope) {
            KeyboardContentInterceptor { info ->
                scope.launch {
                    try {
                        val imported = importIntoEntry(context, mediaManager, listOf(info.contentUri.toString()))
                        if (imported.isNotEmpty()) currentOnMediaImported(imported)
                    } finally {
                        info.releasePermission()
                    }
                }
            }
        }
    InterceptPlatformTextInput(interceptor, content)
}

/**
 * Tells the keyboard behind every text field it wraps that images, videos, and audio are welcome,
 * and hands each file the keyboard commits to [onContent] with read access already granted.
 */
private class KeyboardContentInterceptor(
    private val onContent: (InputContentInfoCompat) -> Unit,
) : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing {
        val withContent =
            object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    EditorInfoCompat.setContentMimeTypes(outAttributes, keyboardContentMimeTypes.toTypedArray())
                    return InputConnectionCompat.createWrapper(connection, outAttributes) { info, flags, _ -> commitContent(info, flags) }
                }
            }
        nextHandler.startInputMethod(withContent)
    }

    private fun commitContent(
        info: InputContentInfoCompat,
        flags: Int,
    ): Boolean {
        val description = info.description
        if ((0 until description.mimeTypeCount).none { acceptsKeyboardContent(description.getMimeType(it)) }) return false
        if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
            try {
                info.requestPermission()
            } catch (error: Exception) {
                Napier.w("The keyboard's file could not be read", error)
                return false
            }
        }
        onContent(info)
        return true
    }
}
