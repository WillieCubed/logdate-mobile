package app.logdate.wear.v2.ambient

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientModeManager
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import io.github.aakira.napier.Napier

/**
 * Whether this watch has the Wear SDK ambient service that [rememberAmbientModeManager] is built on.
 * Wear OS 6 has it. On Wear OS 5 its classes are missing and the call would crash the app.
 */
internal object WearSdkAmbient {
    private const val AMBIENT_MANAGER = "com.google.wear.services.ambient.AmbientManager"
    private const val WEAR_SDK = "com.google.wear.Sdk"

    fun isAvailable(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
        loadClass: (String) -> Class<*>? = ::loadOrNull,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.BAKLAVA) return false
        val ambientManager = loadClass(AMBIENT_MANAGER) ?: return false
        val sdk = loadClass(WEAR_SDK) ?: return false
        return try {
            val getWearManager = sdk.getMethod("getWearManager", Context::class.java, Class::class.java)
            getWearManager.invoke(null, context.applicationContext, ambientManager) != null
        } catch (e: ReflectiveOperationException) {
            Napier.w("The Wear SDK on this watch has no ambient service", e)
            false
        } catch (e: RuntimeException) {
            Napier.w("Looking up the Wear SDK ambient service failed", e)
            false
        }
    }

    private fun loadOrNull(name: String): Class<*>? =
        try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: LinkageError) {
            null
        }
}

/**
 * Keeps the app on screen in ambient mode while [enabled], by providing [LocalAmbientModeManager] to
 * [content]. The app turns it on only while recording. On a watch without the Wear SDK ambient
 * service nothing is provided, and the screen dims to the watch face, where the recording's
 * Ongoing Activity shows.
 */
@Composable
fun WatchAmbientScope(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supported = remember(context) { WearSdkAmbient.isAvailable(context) }
    var manager by remember { mutableStateOf<AmbientModeManager?>(null) }
    if (enabled && supported) AmbientManagerHost(onManager = { manager = it })
    CompositionLocalProvider(LocalAmbientModeManager provides manager, content = content)
}

/** Runs the ambient manager while composed and reports it; leaving composition stops always-on again. */
@Composable
private fun AmbientManagerHost(onManager: (AmbientModeManager?) -> Unit) {
    val manager = rememberAmbientModeManager()
    DisposableEffect(manager) {
        onManager(manager)
        onDispose { onManager(null) }
    }
}

/** The ambient mode the app is in; interactive when no ambient manager is provided. */
@Composable
fun currentAmbientMode(): AmbientMode = LocalAmbientModeManager.current?.currentAmbientMode ?: AmbientMode.Interactive
