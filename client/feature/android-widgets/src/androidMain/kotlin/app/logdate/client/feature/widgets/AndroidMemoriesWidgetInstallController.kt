package app.logdate.client.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import app.logdate.feature.core.settings.ui.MemoriesWidgetInstallController
import app.logdate.feature.core.settings.ui.MemoriesWidgetInstallUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation that requests launcher pinning for the Memories widget.
 */
class AndroidMemoriesWidgetInstallController(
    private val context: Context,
) : MemoriesWidgetInstallController {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(resolveUiState())

    override val uiState: StateFlow<MemoriesWidgetInstallUiState> = _uiState.asStateFlow()

    override suspend fun requestAddToHomeScreen() {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            _uiState.value = MemoriesWidgetInstallUiState.Unsupported
            return
        }

        val successIntent =
            MemoriesWidgetPinSuccessReceiver.createCallbackPendingIntent(appContext)
        val accepted =
            appWidgetManager.requestPinAppWidget(
                ComponentName(appContext, OnThisDayWidgetReceiver::class.java),
                null,
                successIntent,
            )

        if (!accepted) {
            Napier.w("Launcher rejected the Memories widget pin request")
            _uiState.value = MemoriesWidgetInstallUiState.Unsupported
            return
        }

        _uiState.value = MemoriesWidgetInstallUiState.Available
    }

    private fun resolveUiState(): MemoriesWidgetInstallUiState {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        return if (appWidgetManager.isRequestPinAppWidgetSupported) {
            MemoriesWidgetInstallUiState.Available
        } else {
            MemoriesWidgetInstallUiState.Unsupported
        }
    }
}

internal object MemoriesWidgetPinSuccessReceiver {
    private const val REQUEST_CODE = 7001

    fun createCallbackPendingIntent(context: Context): PendingIntent {
        val intent = android.content.Intent(context, WidgetPinnedReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
