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
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

actual val diagnosticsModule: Module =
    module {
        includes(diagnosticCoreModule)
        single<DiagnosticContext> {
            val info = runCatching { get<AppInfoProvider>().getAppInfo() }.getOrNull()
            DiagnosticContext(
                appBuild = info?.versionCode?.takeIf { it >= 0 },
                appVersion =
                    diagnosticVersion(info?.versionName),
                platform = DiagnosticPlatform.DESKTOP,
                osVersion =
                    diagnosticVersion(System.getProperty("os.version")),
                protocols =
                    DiagnosticProtocol.entries
                        .toList(),
            )
        }
        single<DiagnosticStorage> {
            val home = System.getProperty("user.home")
            val cache =
                if (System.getProperty("os.name").startsWith("Mac")) {
                    "$home/Library/Caches"
                } else {
                    System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
                }
            FileDiagnosticStorage(Path("$cache/studio.hypertext.logdate/sync-diagnostics")) { path ->
                val file =
                    java.nio.file.Path
                        .of(path.toString())
                Files.setPosixFilePermissions(
                    file,
                    PosixFilePermissions.fromString(if (Files.isDirectory(file)) "rwx------" else "rw-------"),
                )
            }
        }
        single<DiagnosticStorage>(named("verbose-diagnostic-storage")) {
            val home = System.getProperty("user.home")
            val cache =
                if (System.getProperty("os.name").startsWith("Mac")) {
                    "$home/Library/Caches"
                } else {
                    System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
                }
            FileDiagnosticStorage(Path("$cache/studio.hypertext.logdate/sync-diagnostics/verbose")) { path ->
                val file =
                    java.nio.file.Path
                        .of(path.toString())
                Files.setPosixFilePermissions(
                    file,
                    PosixFilePermissions.fromString(if (Files.isDirectory(file)) "rwx------" else "rw-------"),
                )
            }
        }
        single<DiagnosticStorage>(named("reporting-diagnostic-storage")) {
            val home = System.getProperty("user.home")
            val cache =
                if (System.getProperty("os.name").startsWith("Mac")) {
                    "$home/Library/Caches"
                } else {
                    System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
                }
            FileDiagnosticStorage(Path("$cache/studio.hypertext.logdate/sync-diagnostics/reporting")) { path ->
                val file =
                    java.nio.file.Path
                        .of(path.toString())
                Files.setPosixFilePermissions(
                    file,
                    PosixFilePermissions.fromString(if (Files.isDirectory(file)) "rwx------" else "rw-------"),
                )
            }
        }
    }
