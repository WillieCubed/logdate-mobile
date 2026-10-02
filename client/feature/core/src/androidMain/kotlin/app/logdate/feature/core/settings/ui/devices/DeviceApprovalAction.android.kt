@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.devices

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Shows "Connect a device" only where the Google code scanner can run. Without Google Play
 * services, or before its scanner module is installed, the card stays hidden instead of offering
 * a button that can't work.
 */
@Composable
actual fun DeviceApprovalAction() {
    if (LocalInspectionMode.current) {
        DeviceApprovalContent(DeviceApprovalUiState.Idle, onConnectClick = {}, onApprove = {}, onReject = {}, onDismiss = {})
        return
    }
    val context = LocalContext.current
    val scannerAvailability: CodeScannerAvailability = koinInject()
    val scannerReady by produceState(initialValue = false, context) { value = scannerAvailability.isReady(context) }
    if (!scannerReady) return

    val viewModel: DeviceApprovalViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceApprovalContent(
        state = state,
        onConnectClick = {
            viewModel.dismiss()
            GmsBarcodeScanning
                .getClient(context, scannerOptions)
                .startScan()
                .addOnSuccessListener { barcode -> viewModel.onCodeScanned(barcode.rawValue) }
                .addOnFailureListener { failure ->
                    Napier.w("Connection code scan failed (${failure::class.simpleName})")
                    viewModel.onScanFailed(DeviceApprovalFailure.ScanFailed)
                }
        },
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
        onDismiss = viewModel::dismiss,
    )
}

private val scannerOptions =
    GmsBarcodeScannerOptions
        .Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

/** Whether the Google code scanner can open on this device, installing its module first when needed. */
fun interface CodeScannerAvailability {
    suspend fun isReady(context: Context): Boolean
}

internal object PlayServicesCodeScannerAvailability : CodeScannerAvailability {
    override suspend fun isReady(context: Context): Boolean = isCodeScannerReady(context)
}

private suspend fun isCodeScannerReady(context: Context): Boolean {
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
        return false
    }
    val scanner = GmsBarcodeScanning.getClient(context, scannerOptions)
    val moduleInstall = ModuleInstall.getClient(context)
    if (moduleInstall.areModulesAvailable(scanner).awaitOrNull()?.areModulesAvailable() == true) return true
    Napier.i("Code scanner module isn't installed yet; installing it before showing Connect a device")
    val install = moduleInstall.installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build()).awaitOrNull()
    if (install == null) return false
    if (install.areModulesAlreadyInstalled()) return true
    repeat(SCANNER_INSTALL_CHECKS) {
        delay(SCANNER_INSTALL_CHECK_INTERVAL)
        if (moduleInstall.areModulesAvailable(scanner).awaitOrNull()?.areModulesAvailable() == true) return true
    }
    Napier.w("Code scanner module did not finish installing; Connect a device stays hidden")
    return false
}

private const val SCANNER_INSTALL_CHECKS = 60
private val SCANNER_INSTALL_CHECK_INTERVAL = 2.seconds

private suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> continuation.resume(if (task.isSuccessful) task.result else null) }
    }
