@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Android implementation of [rememberCoverImageLauncher] backed by the system photo picker
 * (`ActivityResultContracts.PickVisualMedia`).
 *
 * The picker requires no runtime permissions on Android 13+; on older devices the system shrugs
 * it through. Selecting an item produces a content URI that we hand back as its string form so
 * the caller can store it without pulling Android types into commonMain.
 */
@Composable
actual fun rememberCoverImageLauncher(onPicked: (String?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            callback(uri?.toString())
        }
    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}
