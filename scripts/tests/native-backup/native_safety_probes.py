"""Native acceptance safety contracts for probes."""

import json
import runpy
from pathlib import Path
import stat
import tempfile
from unittest.mock import patch

from native_safety_support import HARNESS, ROOT


class NativeProbesSafety:
    def test_post_transfer_callbacks_execute_and_report_the_supplied_contract(self):
        post = runpy.run_path(
            str(ROOT / "tests/e2e/native-post-transfer-acceptance.py")
        )
        saved = json.loads(
            (ROOT / "tests/e2e/native-modern-callback-cases.json").read_text()
        )
        changed = {
            "fixture.Callbacks": ["first", "second"],
            "fixture.MoreCallbacks": ["third"],
        }
        for contract in (saved, changed):
            with (
                self.subTest(contract=contract),
                tempfile.TemporaryDirectory() as temporary,
            ):
                root = Path(temporary)
                (root / "native-modern-callback-cases.json").write_text(
                    json.dumps(contract)
                )
                calls = []
                commands = []
                captures = []

                def retain_lock_evidence(*args):
                    captures.append(args)

                def instrument(*args, **kwargs):
                    calls.append(args)

                def adb(*args, **kwargs):
                    commands.append(args)
                    return b"1"

                with (
                    patch.dict(
                        post["run"].__globals__,
                        {
                            "instrument": instrument,
                            "retain_lock_evidence": retain_lock_evidence,
                            "__file__": str(
                                root / "native-post-transfer-acceptance.py"
                            ),
                        },
                    ),
                    patch.dict(post["NATIVE"], {"adb": adb}),
                ):
                    post["run"]("emulator-5556", root / "output")
                self.assertEqual(
                    [("emulator-5556", root / "output/restored-app-lock")], captures
                )
                expected_count = sum(len(methods) for methods in contract.values())
                callback_calls = [
                    call for call in calls if call[3].name == "callbacks.log"
                ]
                self.assertEqual(
                    [
                        (
                            "emulator-5556",
                            ",".join(contract),
                            expected_count,
                            root / "output/callbacks.log",
                        ),
                    ],
                    callback_calls,
                )
                receipt = json.loads((root / "output/acceptance.json").read_text())
                self.assertEqual(expected_count, receipt["callbackCases"])
                self.assertFalse(receipt["modernCallbackAccepted"])
                self.assertFalse(receipt["googleProviderAccepted"])
                self.assertFalse(
                    any(
                        command[1:4] == ("shell", "am", "start") for command in commands
                    )
                )

    def test_native_backup_integrity_probe_preserves_session_owner(self):
        probe = (
            ROOT
            / "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/NativeBackupTransferProbeTest.kt"
        ).read_text()
        self.assertIn(
            'assertEquals(arguments.getString("sourceOwner"), session.accountId)', probe
        )
        self.assertIn('arguments.getString("sourceHasSession") == "true"', probe)
        self.assertIn(
            "assertFalse(\n                        sessions.hasValidSession()", probe
        )

    def test_native_backup_verifies_source_keys_for_every_transfer(self):
        harness = (ROOT / "tests/e2e/native-backup-acceptance.py").read_text()
        self.assertIn('"source-backup-integrity"', harness)
        self.assertIn("verify_after_backup=validate_source_integrity", harness)
        self.assertIn("source_has_session=exercise_data_only", harness)

    def test_timeline_acceptance_requires_destination_controls_and_restored_content(
        self,
    ):
        hierarchy = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        self.assertTrue(HARNESS.timeline_is_open(hierarchy))
        self.assertFalse(
            HARNESS.timeline_is_open(
                b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
            )
        )

    def test_ui_poll_evidence_can_be_updated_without_changing_private_permissions(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "destination-ui.xml"
            HARNESS.private_file(path, b"first poll")
            HARNESS.overwrite_private_file(path, b"latest poll")

            self.assertEqual(b"latest poll", path.read_bytes())
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))

    def test_timeline_wait_keeps_polling_until_restored_content_is_visible(self):
        loading = b'<hierarchy><node text="Search your log"/><node text="Timeline"/><node text="Loading"/></hierarchy>'
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                    loading,
                    b"loading image",
                    b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                    timeline,
                    b"restored journal image",
                ],
            ) as adb,
            patch.object(HARNESS.time, "sleep"),
        ):
            HARNESS.wait_for_timeline(
                "emulator-5582", Path(temporary), timeout_seconds=5
            )

            self.assertEqual(6, adb.call_count)
            self.assertEqual(
                timeline, (Path(temporary) / "destination-ui.xml").read_bytes()
            )
            self.assertEqual(
                b"restored journal image",
                (Path(temporary) / "destination-journal.png").read_bytes(),
            )

    def test_archive_probe_uses_the_supplied_per_run_fixture_label(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                HARNESS,
                "adb",
                return_value=b"INSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\n",
            ) as adb,
        ):
            HARNESS.archive_probe(
                "emulator-5582",
                "seed",
                Path(temporary) / "archive-seed.log",
                fixture_label="transfer-unique-run",
            )

        command = adb.call_args.args[2]
        self.assertIn("-e archiveRecoveryFixtureLabel transfer-unique-run", command)

    def test_quota_probe_restores_transport_and_cleans_fixture_after_rejection_failure(
        self,
    ):
        calls = []

        def adb(serial, *arguments, **kwargs):
            calls.append(arguments)
            if arguments[:4] == ("shell", "settings", "get", "secure"):
                return b"is_encrypted=true,is_device_transfer=true"
            if arguments[:3] == ("shell", "bmgr", "backupnow"):
                return b"Backup finished with result: Success"
            return b""

        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(HARNESS, "adb", side_effect=adb),
            patch.object(
                HARNESS,
                "probe",
            ) as probe,
        ):
            with self.assertRaisesRegex(
                RuntimeError, "did not reject the oversized cloud backup"
            ):
                HARNESS.verify_quota("emulator-5582", Path(temporary))

        self.assertIn(
            (
                "shell",
                "settings",
                "put",
                "secure",
                "backup_local_transport_parameters",
                "is_encrypted=true,is_device_transfer=true",
            ),
            calls,
        )
        self.assertEqual(
            ["quota-cleanup", "quota", "quota-cleanup"],
            [call.args[1] for call in probe.call_args_list],
        )
