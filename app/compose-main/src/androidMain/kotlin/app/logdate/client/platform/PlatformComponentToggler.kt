package app.logdate.client.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.notifications.FLOAT_RECORDER_RECEIVER_CLASS
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Turns one manifest component (a tile service, widget receiver, or activity) on or off. */
fun interface ComponentSwitch {
    fun setEnabled(
        className: String,
        enabled: Boolean,
    )
}

/** A [ComponentSwitch] backed by the package manager. Never kills the running app. */
class PackageManagerComponentSwitch(
    private val context: Context,
) : ComponentSwitch {
    override fun setEnabled(
        className: String,
        enabled: Boolean,
    ) {
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, className),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/**
 * Keeps flag-gated manifest components in step with their feature flags.
 *
 * Android shows a declared tile, widget, or bubble activity as soon as the app is installed, so a
 * flag checked in Kotlin cannot hide it. These components are declared `android:enabled="false"` and
 * listed in [components]; this class enables each one while its flag is on and disables it again when
 * the flag turns off.
 */
class PlatformComponentToggler(
    private val flags: FeatureFlagStore,
    private val components: Map<FeatureFlag, List<String>>,
    private val switch: ComponentSwitch,
) {
    fun start(scope: CoroutineScope) {
        components.forEach { (flag, classNames) ->
            scope.launch {
                flags.observe(flag).distinctUntilChanged().collect { enabled ->
                    classNames.forEach { className -> apply(className, enabled) }
                }
            }
        }
    }

    private fun apply(
        className: String,
        enabled: Boolean,
    ) {
        try {
            switch.setEnabled(className, enabled)
        } catch (error: IllegalArgumentException) {
            Napier.w("Could not ${if (enabled) "enable" else "disable"} $className; is it declared in the manifest?", error)
        }
    }

    companion object {
        /** Every flag-gated manifest component, by the flag that shows it. Add new surfaces here. */
        val osIntegrationComponents: Map<FeatureFlag, List<String>> =
            mapOf(
                FeatureFlag.QUICK_SETTINGS_TILES to
                    listOf(
                        "app.logdate.client.tiles.NewEntryTileService",
                        "app.logdate.client.tiles.RecordVoiceTileService",
                    ),
                FeatureFlag.LOCK_SCREEN_CAPTURE_WIDGET to
                    listOf("app.logdate.client.feature.widgets.CaptureWidgetReceiver"),
                FeatureFlag.RECORDING_BUBBLE to
                    listOf(
                        "app.logdate.client.bubble.RecordingBubbleActivity",
                        FLOAT_RECORDER_RECEIVER_CLASS,
                    ),
            )
    }
}
