import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class ExportLoggingBoundaryTest(unittest.TestCase):
    def test_media_and_export_logs_never_include_paths_or_raw_failures(self):
        sources = [
            'client/sync/src/androidMain/kotlin/app/logdate/client/sync/AndroidLogDateSyncWorker.kt',
            'client/sync/src/androidMain/kotlin/app/logdate/client/sync/migration/SharedPreferencesMigrationStorage.kt',
            'client/sync/src/iosMain/kotlin/app/logdate/client/sync/migration/KeychainMigrationStorage.kt',
            'client/sync/src/desktopMain/kotlin/app/logdate/client/sync/migration/FileMigrationStorage.kt',
            'client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/restore/RestoreViewModel.kt',
            'client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/export/ExportViewModel.kt',
            'client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/export/AndroidMediaSourceOpener.kt',
            'server/src/main/kotlin/app/logdate/server/routes/sync/SyncMaintenanceRoutes.kt',
            'server/src/main/kotlin/app/logdate/server/routes/sync/SyncHelpers.kt',
            'server/src/main/kotlin/app/logdate/server/routes/sync/SyncCollectionRoutes.kt',
            'client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/export/AndroidExportLauncher.kt',
            'client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/export/ExportWorker.kt',
            'client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/restore/RestoreWorker.kt',
            'client/feature/core/src/androidMain/kotlin/app/logdate/feature/core/restore/AndroidRestoreLauncher.kt',
            'client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/restore/DesktopRestoreLauncher.kt',
            'client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/restore/IosRestoreLauncher.kt',
            "client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/account/ConnectedServer.kt",
            'client/feature/core/src/desktopMain/kotlin/app/logdate/feature/core/export/DesktopExportLauncher.kt',
            'client/feature/core/src/iosMain/kotlin/app/logdate/feature/core/export/IosExportLauncher.kt',
            'server/src/main/kotlin/app/logdate/server/routes/sync/SyncMediaRoutes.kt',
        ]
        for source in sources:
            for call in re.findall(r'Napier\.[a-z]+\([^\n]*\)', (ROOT / source).read_text()):
                with self.subTest(source=source):
                    self.assertNotIn('$', call, 'Export logging must exclude dynamic data')
                    self.assertRegex(call, r'^Napier\.[a-z]+\("[^"\n]*"\)$',
                                     'Export logging must not forward exception objects')


if __name__ == '__main__':
    unittest.main()
