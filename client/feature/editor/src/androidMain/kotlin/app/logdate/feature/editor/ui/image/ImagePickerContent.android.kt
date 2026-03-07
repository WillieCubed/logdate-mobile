package app.logdate.feature.editor.ui.image

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.logdate.client.media.MediaManager
import app.logdate.client.permissions.PermissionManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val mediaManager: MediaManager = koinInject()
    val permissionManager: PermissionManager = koinInject()
    val imagePermission = remember { imageLibraryPermission() }

    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var libraryState by remember { mutableStateOf<ImagePickerLibraryState>(ImagePickerLibraryState.Loading) }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let {
                Napier.d("Android image selected from manual picker: $it")
                onImageSelected(it.toString())
            } ?: Napier.d("Android image picker dismissed without a selection")
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            hasRequestedPermission = true
            reloadTrigger++
        }

    fun permissionRequiredState(): ImagePickerLibraryState.PermissionRequired {
        val permanentlyDenied =
            hasRequestedPermission &&
                context.findActivity()?.let { activity ->
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, imagePermission)
                } == true
        return ImagePickerLibraryState.PermissionRequired(permanentlyDenied = permanentlyDenied)
    }

    fun refreshRecentImages() {
        if (ContextCompat.checkSelfPermission(context, imagePermission) != PackageManager.PERMISSION_GRANTED) {
            libraryState = permissionRequiredState()
            return
        }

        coroutineScope.launch {
            libraryState = ImagePickerLibraryState.Loading

            libraryState =
                try {
                    val images = recentImagePreviews(mediaManager.getRecentMedia().first())
                    if (images.isEmpty()) {
                        ImagePickerLibraryState.Empty
                    } else {
                        ImagePickerLibraryState.Loaded(images)
                    }
                } catch (error: SecurityException) {
                    Napier.w("Recent image access requires permission", error)
                    permissionRequiredState()
                } catch (error: Exception) {
                    Napier.e("Failed to load recent Android images", error)
                    ImagePickerLibraryState.Error
                }
        }
    }

    LaunchedEffect(imagePermission, reloadTrigger) {
        refreshRecentImages()
    }

    DisposableEffect(lifecycleOwner, imagePermission, hasRequestedPermission) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshRecentImages()
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ImagePickerBrowser(
        state = libraryState,
        onImageSelected = onImageSelected,
        onBrowseLibrary = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onRequestLibraryAccess = {
            hasRequestedPermission = true
            permissionLauncher.launch(imagePermission)
        },
        onOpenSettings = { permissionManager.openPermissionSettings() },
        onRetryLoading = { reloadTrigger++ },
        modifier = modifier,
    )
}

private fun imageLibraryPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
