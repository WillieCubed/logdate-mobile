@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:filename")

package app.logdate.feature.journals.ui.creation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.uuid.Uuid

private const val TYPE_IMAGE = "public.image"
private const val TYPE_MOVIE = "public.movie"

/**
 * Active picker delegates retained for the duration of the picker's life. Without this set, the
 * delegate would be GC'd before the user dismisses the picker (UIKit holds delegates weakly).
 */
private val activeDelegates = mutableSetOf<PickerDelegate>()

@Composable
actual fun rememberMediaPickerLauncher(onMediaSelected: (List<String>) -> Unit): MediaPickerState {
    val scope = rememberCoroutineScope()
    return remember(scope, onMediaSelected) {
        MediaPickerState(
            launchAction = { presentPicker(scope, onMediaSelected) },
        )
    }
}

actual class MediaPickerState(
    private val launchAction: () -> Unit,
) {
    actual fun launchPicker() {
        launchAction()
    }
}

private fun presentPicker(
    scope: CoroutineScope,
    onMediaSelected: (List<String>) -> Unit,
) {
    val configuration =
        PHPickerConfiguration().apply {
            filter =
                PHPickerFilter.anyFilterMatchingSubfilters(
                    listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter),
                )
            selectionLimit = 0
        }
    val picker = PHPickerViewController(configuration = configuration)
    val delegate = PickerDelegate(scope, picker, onMediaSelected)
    activeDelegates += delegate
    picker.delegate = delegate

    val host =
        topPresentedViewController() ?: run {
            Napier.w("PHPicker: no presentable UIViewController; aborting present")
            activeDelegates -= delegate
            return
        }
    host.presentViewController(picker, animated = true, completion = null)
}

private class PickerDelegate(
    private val scope: CoroutineScope,
    private val picker: PHPickerViewController,
    private val onMediaSelected: (List<String>) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)

        @Suppress("UNCHECKED_CAST")
        val results = didFinishPicking as List<PHPickerResult>
        scope.launch {
            val uris =
                results
                    .map { async(Dispatchers.Default) { extractFileUri(it.itemProvider) } }
                    .awaitAll()
                    .filterNotNull()
            withContext(Dispatchers.Main) {
                onMediaSelected(uris)
                activeDelegates -= this@PickerDelegate
            }
        }
    }
}

private suspend fun extractFileUri(provider: NSItemProvider): String? {
    val (typeId, ext) =
        when {
            provider.hasItemConformingToTypeIdentifier(TYPE_IMAGE) -> TYPE_IMAGE to "jpg"
            provider.hasItemConformingToTypeIdentifier(TYPE_MOVIE) -> TYPE_MOVIE to "mov"
            else -> return null
        }
    val tempUrl =
        suspendCancellableCoroutine<NSURL?> { continuation ->
            provider.loadFileRepresentationForTypeIdentifier(typeIdentifier = typeId) { url, error ->
                if (error != null) {
                    Napier.w("PHPicker load failed: ${error.localizedDescription}")
                }
                if (continuation.isActive) continuation.resume(url)
            }
        } ?: return null
    return copyToImports(tempUrl, ext)
}

private fun copyToImports(
    srcUrl: NSURL,
    ext: String,
): String? {
    val fileManager = NSFileManager.defaultManager
    val docsDir =
        fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: return null
    val importsDir = docsDir.URLByAppendingPathComponent("imports") ?: return null
    fileManager.createDirectoryAtURL(
        url = importsDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val destUrl = importsDir.URLByAppendingPathComponent("${Uuid.random()}.$ext") ?: return null
    return memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val ok = fileManager.copyItemAtURL(srcURL = srcUrl, toURL = destUrl, error = errorPtr.ptr)
        if (ok) {
            destUrl.absoluteString
        } else {
            Napier.w("PHPicker: copyItemAtURL failed: ${errorPtr.value?.localizedDescription}")
            null
        }
    }
}

private fun topPresentedViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    @Suppress("DEPRECATION")
    val window =
        app.keyWindow
            ?: (app.windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow)
            ?: (app.windows.firstOrNull() as? UIWindow)
    var vc: UIViewController = window?.rootViewController ?: return null
    while (true) {
        val presented = vc.presentedViewController ?: break
        vc = presented
    }
    return vc
}
