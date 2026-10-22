package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.logdate.client.repository.user.UserStateRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** Retains rejected setup while private content and writes remain behind fresh authorization. */
abstract class SecureWidgetConfigActivity : FragmentActivity() {
    internal abstract val widgetSetupTarget: WidgetSetupTarget
    private var contentVisible = false
    private var hasStopped = false
    private var foregroundGeneration = 0L
    private var needsReauthentication = false
    private var preserveOnBackground = false
    private var userState: UserStateRepository? = null
    private var reauthentication: Job? = null
    private val saveRecovery by lazy {
        WidgetSetupSaveRecovery<WidgetSetupRequest>(
            scope = lifecycleScope,
            persist = ::persistRequest,
            onSaved = ::completeSetup,
            onFailure = { Napier.w("Widget configuration remains pending", it) },
            onCanceled = { finish() },
        )
    }
    internal val widgetSetupSaveState get() = saveRecovery.state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preserveOnBackground = savedInstanceState?.getBoolean("widgetSetupRetained") == true
        WidgetSetupRequest
            .decodeFor(
                savedInstanceState?.getString("widgetSetupPending"),
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
                widgetSetupTarget,
            )?.let(saveRecovery::submit)
    }

    internal fun persistWidgetSetup(request: WidgetSetupRequest) {
        if (!request.isValid() || request.target != widgetSetupTarget || !isWidgetBound(request)) {
            finish()
            return
        }
        preserveOnBackground = true
        saveRecovery.submit(request)
    }

    internal fun cancelPendingWidgetSetup() = saveRecovery.cancel()

    internal open suspend fun persistRequest(
        request: WidgetSetupRequest,
        canCommit: () -> Boolean,
    ): WidgetSetupSaveResult =
        withContext(Dispatchers.IO) {
            if (!canCommit()) return@withContext WidgetSetupSaveResult.Retry
            if (!isWidgetBound(request)) return@withContext WidgetSetupSaveResult.TargetRemoved
            val saved = WidgetInstanceSettings(this@SecureWidgetConfigActivity).saveSetup(request) { canCommit() && isWidgetBound(request) }
            if (saved && canCommit() && enqueueWidgetRefresh(this@SecureWidgetConfigActivity, request.refreshEventId)) {
                WidgetSetupSaveResult.Saved
            } else if (runCatching { StatFs(filesDir.path).availableBytes < 1_048_576 }.getOrDefault(false)) {
                WidgetSetupSaveResult.StorageFull
            } else {
                WidgetSetupSaveResult.Retry
            }
        }

    private fun completeSetup(request: WidgetSetupRequest) {
        if (contentVisible && !hasStopped && isWidgetBound(request)) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, request.widgetId))
        }
        finish()
    }

    private fun isWidgetBound(request: WidgetSetupRequest): Boolean {
        val receiver =
            when (request.target) {
                WidgetSetupTarget.PINNED_MEMORY -> FixedMemoryWidgetReceiver::class.java
                WidgetSetupTarget.MEMORIES -> OnThisDayWidgetReceiver::class.java
                WidgetSetupTarget.PHOTO_PROMPT -> NewEntryWidgetReceiver::class.java
            }
        return AppWidgetManager.getInstance(this).getAppWidgetInfo(request.widgetId)?.provider == ComponentName(this, receiver)
    }

    protected suspend fun unlockSetup(userStateRepository: UserStateRepository): Boolean {
        userState = userStateRepository
        while (lifecycle.currentState != Lifecycle.State.DESTROYED) {
            val started = lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) || it == Lifecycle.State.DESTROYED }
            if (started == Lifecycle.State.DESTROYED) return false
            if (hasStopped) {
                yield()
                continue
            }
            val generation = foregroundGeneration
            val allowed = unlockWidgetContent(userStateRepository)
            if (hasStopped || foregroundGeneration != generation) continue
            if (!allowed) {
                finish()
                return false
            }
            contentVisible = true
            needsReauthentication = false
            findViewById<View>(android.R.id.content).visibility = View.VISIBLE
            saveRecovery.setActive(true)
            return true
        }
        return false
    }

    override fun onStart() {
        hasStopped = false
        foregroundGeneration++
        super.onStart()
        if (needsReauthentication && reauthentication?.isActive != true) {
            val repository = userState ?: return
            reauthentication = lifecycleScope.launch { unlockSetup(repository) }
        }
    }

    override fun onStop() {
        hasStopped = true
        foregroundGeneration++
        saveRecovery.setActive(false)
        if (contentVisible && !isChangingConfigurations) {
            if (preserveOnBackground || saveRecovery.pending != null) {
                findViewById<View>(android.R.id.content).visibility = View.INVISIBLE
                needsReauthentication = true
                contentVisible = false
            } else {
                finish()
            }
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("widgetSetupPending", saveRecovery.pending?.encode())
        outState.putBoolean("widgetSetupRetained", preserveOnBackground)
        super.onSaveInstanceState(outState)
    }

    internal fun openWidgetStorageSettings() {
        runCatching { startActivity(Intent(StorageManager.ACTION_MANAGE_STORAGE)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            .onFailure { Napier.w("Storage settings could not open; widget setup remains available", it) }
    }
}
