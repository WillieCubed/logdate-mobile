@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.sync.di

import app.logdate.client.device.AppInfoProvider
import app.logdate.client.sync.diagnostics.DiagnosticStorage
import app.logdate.client.sync.diagnostics.FileDiagnosticStorage
import app.logdate.shared.model.diagnostics.DiagnosticContext
import app.logdate.shared.model.diagnostics.DiagnosticPlatform
import app.logdate.shared.model.diagnostics.DiagnosticProtocol
import app.logdate.shared.model.diagnostics.diagnosticVersion
import kotlinx.io.files.Path
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

actual val diagnosticsModule: Module =
    module {
        includes(diagnosticCoreModule)
        single<DiagnosticContext> {
            val info = runCatching { get<AppInfoProvider>().getAppInfo() }.getOrNull()
            DiagnosticContext(
                appBuild = info?.versionCode?.takeIf { it >= 0 },
                appVersion =
                    diagnosticVersion(info?.versionName),
                platform = DiagnosticPlatform.IOS,
                osVersion =
                    diagnosticVersion(platform.UIKit.UIDevice.currentDevice.systemVersion),
                protocols =
                    DiagnosticProtocol.entries
                        .toList(),
            )
        }
        single<DiagnosticStorage> {
            FileDiagnosticStorage(Path("${NSHomeDirectory()}/Library/Application Support/sync-diagnostics")) { path ->
                check(
                    NSFileManager.defaultManager.setAttributes(
                        mapOf(NSFileProtectionKey to NSFileProtectionComplete),
                        ofItemAtPath = path.toString(),
                        error = null,
                    ),
                ) { "Diagnostic protection unavailable" }
                check(NSURL.fileURLWithPath(path.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) {
                    "Diagnostic backup exclusion unavailable"
                }
            }
        }
        single<DiagnosticStorage>(named("verbose-diagnostic-storage")) {
            FileDiagnosticStorage(Path("${NSHomeDirectory()}/Library/Application Support/sync-diagnostics/verbose")) { path ->
                check(
                    NSFileManager.defaultManager.setAttributes(
                        mapOf(NSFileProtectionKey to NSFileProtectionComplete),
                        ofItemAtPath = path.toString(),
                        error = null,
                    ),
                ) { "Diagnostic protection unavailable" }
                check(NSURL.fileURLWithPath(path.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) {
                    "Diagnostic backup exclusion unavailable"
                }
            }
        }
        single<DiagnosticStorage>(named("reporting-diagnostic-storage")) {
            FileDiagnosticStorage(Path("${NSHomeDirectory()}/Library/Application Support/sync-diagnostics/reporting")) { path ->
                check(
                    NSFileManager.defaultManager.setAttributes(
                        mapOf(NSFileProtectionKey to NSFileProtectionComplete),
                        ofItemAtPath = path.toString(),
                        error = null,
                    ),
                ) { "Diagnostic protection unavailable" }
                check(NSURL.fileURLWithPath(path.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) {
                    "Diagnostic backup exclusion unavailable"
                }
            }
        }
    }
