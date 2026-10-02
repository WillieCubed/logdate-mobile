package app.logdate.client.sync.di

import android.system.Os
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.sync.diagnostics.DiagnosticStorage
import app.logdate.client.sync.diagnostics.FileDiagnosticStorage
import app.logdate.shared.model.diagnostics.DiagnosticContext
import app.logdate.shared.model.diagnostics.DiagnosticPlatform
import app.logdate.shared.model.diagnostics.DiagnosticProtocol
import app.logdate.shared.model.diagnostics.diagnosticVersion
import kotlinx.io.files.Path
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

actual val diagnosticsModule: Module =
    module {
        includes(diagnosticCoreModule)
        single<DiagnosticContext> {
            val info = runCatching { get<AppInfoProvider>().getAppInfo() }.getOrNull()
            DiagnosticContext(
                appBuild = info?.versionCode?.takeIf { it >= 0 },
                appVersion =
                    diagnosticVersion(info?.versionName),
                platform = DiagnosticPlatform.ANDROID,
                osVersion =
                    diagnosticVersion(android.os.Build.VERSION.RELEASE),
                protocols =
                    DiagnosticProtocol.entries
                        .toList(),
            )
        }
        single<DiagnosticStorage> {
            FileDiagnosticStorage(Path(File(androidContext().noBackupFilesDir, "sync-diagnostics").path)) { path ->
                Os.chmod(path.toString(), if (File(path.toString()).isDirectory) 448 else 384)
            }
        }
        single<DiagnosticStorage>(named("verbose-diagnostic-storage")) {
            FileDiagnosticStorage(Path(File(androidContext().noBackupFilesDir, "sync-diagnostics/verbose").path)) { path ->
                Os.chmod(path.toString(), if (File(path.toString()).isDirectory) 448 else 384)
            }
        }
        single<DiagnosticStorage>(named("reporting-diagnostic-storage")) {
            FileDiagnosticStorage(Path(File(androidContext().noBackupFilesDir, "sync-diagnostics/reporting").path)) { path ->
                Os.chmod(path.toString(), if (File(path.toString()).isDirectory) 448 else 384)
            }
        }
    }
