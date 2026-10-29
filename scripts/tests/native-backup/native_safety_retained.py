"""Native acceptance safety contracts for retained."""

import io
import subprocess
from unittest.mock import patch

from native_safety_support import HARNESS


class NativeRetainedSafety:
    def test_preinstalled_empty_emulator_can_contain_sdk_state_without_journal_data(
        self,
    ):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"1",
                    f"package:{HARNESS.PACKAGE}".encode(),
                    b"./shared_prefs/com.google.firebase.crashlytics.xml",
                    b"",
                    b"",
                    b"0",
                    b"0",
                    b"",
                    b"1",
                    b"",
                    b"",
                ],
            ) as adb,
            patch.object(
                HARNESS,
                "wait_for_package_manager",
            ),
            patch.object(HARNESS, "select_local_transport"),
        ):
            HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

        self.assertIn(
            ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"),
            [call.args[1:] for call in adb.call_args_list],
        )
        self.assertIn(
            ("shell", "settings", "put", "secure", "user_setup_complete", "1"),
            [call.args[1:] for call in adb.call_args_list],
        )

    def test_preinstalled_emulator_with_journal_or_media_data_is_rejected(self):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"1",
                    f"package:{HARNESS.PACKAGE}".encode(),
                    b"./files/media/objects/retained.jpg",
                ],
            ) as adb,
            patch.object(
                HARNESS,
                "wait_for_package_manager",
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

        self.assertEqual(3, adb.call_count)

    def test_preinstalled_emulator_with_retained_recovery_keys_is_rejected(self):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"1",
                    f"package:{HARNESS.PACKAGE}".encode(),
                    b"./files/identity_recovery/phrase_backup",
                ],
            ),
            patch.object(HARNESS, "wait_for_package_manager"),
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_backup_managed_recovery_or_widget_data_is_rejected(
        self,
    ):
        retained_paths = (
            b"./files/archive_recovery/records/pending.json",
            b"./files/archive_recovery/sources/source.zip",
            b"./shared_prefs/logdate_widget_instances.xml",
        )
        for retained_path in retained_paths:
            with (
                self.subTest(path=retained_path),
                patch.object(
                    HARNESS,
                    "adb",
                    side_effect=[
                        b"1",
                        f"package:{HARNESS.PACKAGE}".encode(),
                        retained_path,
                    ],
                ),
                patch.object(HARNESS, "wait_for_package_manager"),
            ):
                with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                    HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_encrypted_identity_or_session_is_rejected(self):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"1",
                    f"package:{HARNESS.PACKAGE}".encode(),
                    b"./shared_prefs/logdate_secure_storage.xml",
                ],
            ),
            patch.object(HARNESS, "wait_for_package_manager"),
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_credentials_only_fixture_keeps_owner_preferences_but_excludes_journal_and_media(
        self,
    ):
        prefix = f"apps/{HARNESS.PACKAGE}"
        payload = self.credentials_backup(
            extra=(
                (prefix + "/db/logdate", b"encrypted-journal"),
                (prefix + "/f/media/original.jpg", b"original-media"),
            ),
        )

        filtered = HARNESS.credentials_only_from_full_backup(payload)
        with HARNESS.tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            retained = {item.name for item in archive.getmembers()}

        self.assertIn(prefix + "/_manifest", retained)
        self.assertIn(prefix + "/f/logdate.preferences_pb", retained)
        self.assertIn(prefix + "/f/db_recovery/passphrase_backup", retained)
        self.assertNotIn(prefix + "/db/logdate", retained)
        self.assertNotIn(prefix + "/f/media/original.jpg", retained)

    def test_preinstalled_emulator_with_retained_datastore_state_is_rejected(self):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"1",
                    f"package:{HARNESS.PACKAGE}".encode(),
                    b"./files/datastore/user_preferences.preferences_pb",
                ],
            ),
            patch.object(HARNESS, "wait_for_package_manager"),
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_root_datastore_preferences_is_rejected(self):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"1",
                    f"package:{HARNESS.PACKAGE}".encode(),
                    b"./files/logdate.preferences_pb",
                ],
            ),
            patch.object(HARNESS, "wait_for_package_manager"),
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_installed_package_without_user_data_is_an_empty_fixture(self):
        error = subprocess.CalledProcessError(
            1,
            ["adb", "shell", "run-as"],
            stderr=f"run-as: couldn't stat /data/user/0/{HARNESS.PACKAGE}: No such file or directory".encode(),
        )
        with patch.object(HARNESS, "adb", side_effect=error):
            self.assertEqual([], HARNESS.package_user_files("emulator-5582"))
