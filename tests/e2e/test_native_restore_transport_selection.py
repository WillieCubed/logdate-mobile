from contextlib import nullcontext
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import tarfile
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location(
    "native_restore_transport_selection",
    Path(__file__).with_name("native-backup-acceptance.py"),
)
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class NativeRestoreTransportSelectionTest(unittest.TestCase):
    def test_missing_privacy_uses_cloud_without_an_intact_authenticated_policy_snapshot(
        self,
    ):
        class Prepared(Exception):
            pass

        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            app, probe = folder / "app.apk", folder / "probe.apk"
            app.touch()
            probe.touch()
            arguments = [
                "native-backup-acceptance.py",
                "--source",
                "emulator-5554",
                "--destination",
                "emulator-5556",
                "--app-apk",
                str(app),
                "--test-apk",
                str(probe),
                "--output",
                str(folder / "proof"),
                "--seed-empty-source",
                "--exercise-missing-privacy",
            ]
            with (
                patch("sys.argv", arguments),
                patch.object(HARNESS, "prepare", side_effect=Prepared) as prepare,
            ):
                with self.assertRaises(Prepared):
                    HARNESS.main()
            self.assertTrue(
                prepare.call_args.kwargs["cloud_backup"],
                "Missing privacy must not leave its original policy in a D2D snapshot",
            )

    def test_missing_privacy_refuses_a_payload_that_still_contains_an_authenticated_policy_copy(
        self,
    ):
        def archive(entries):
            output = io.BytesIO()
            with tarfile.open(fileobj=output, mode="w") as tar:
                for name, data in entries.items():
                    member = tarfile.TarInfo(name)
                    member.size = len(data)
                    tar.addfile(member, io.BytesIO(data))
            return output.getvalue()

        field = HARNESS.length_delimited_field
        preferences = field(1, field(1, b"is_onboarded") + field(2, b"\x08\x01"))
        prefix = "apps/" + HARNESS.PACKAGE + "/f/"
        payload = archive(
            {
                "1/_full/" + HARNESS.PACKAGE: archive(
                    {
                        prefix + "logdate.preferences_pb": preferences,
                        prefix
                        + "native_journal_transfer/current/files/logdate.preferences_pb": preferences,
                    }
                )
            }
        )
        with self.assertRaisesRegex(RuntimeError, "cloud selection"):
            HARNESS.without_privacy_metadata(payload)

    def transfer_probes(
        self,
        *,
        cloud=False,
        privacy=False,
        partial=False,
        camera=False,
        snapshot_failure=None,
        post_repair_backup_succeeded=True,
    ):
        metadata = {
            "owner": "synthetic",
            "device": "synthetic",
            "fingerprint": "synthetic",
            "mediaNonce": "synthetic",
            "mediaCiphertext": "synthetic",
            "recordingTransferFixture": True,
        }

        backup_attempts = []

        def adb(serial, *arguments, **kwargs):
            if arguments[:3] == ("shell", "bmgr", "backupnow"):
                backup_attempts.append(arguments)
                result = (
                    "Success"
                    if len(backup_attempts) > 1 and post_repair_backup_succeeded
                    else "Transport rejected package because it wasn't able to process it at the time"
                )
                return f"Package {HARNESS.PACKAGE} with result: {result}\n".encode()
            if arguments[:2] == ("exec-out", "cat"):
                return json.dumps(metadata).encode()
            if arguments[:2] == ("shell", "date"):
                return b"1000.0"
            return b"OK (1 test)\nrestoreFinished: 0"

        mocks = {
            "prepare_source": Mock(),
            "adb": Mock(side_effect=adb),
            "backup_source": Mock(return_value=(b"backup", {})),
            "data_only_source_session": Mock(
                side_effect=lambda *args: nullcontext(None)
            ),
            "selected_database_snapshot": Mock(return_value={"sha256": "synthetic"}),
            "assert_cloud_backup_contents": Mock(),
            "without_privacy_metadata": Mock(return_value=b"mutated"),
            "without_one_native_media_file": Mock(
                return_value=(b"mutated", "missing.png")
            ),
            "restore_transport": Mock(return_value=b"restoreFinished: 0"),
            "package_file_snapshot": Mock(return_value={}),
            "verify_restored_snapshot": Mock(),
            "assert_snapshot_preserved": Mock(),
            "radio_state": Mock(return_value={}),
            "set_offline": Mock(),
            "restore_radio_state": Mock(),
            "wait_for_timeline": Mock(),
            "probe": Mock(),
            "privacy_probe": Mock(),
            "verify_privacy_backup_refusal": Mock(),
        }
        self.last_mocks = mocks
        if snapshot_failure is not None:
            mocks["verify_restored_snapshot"].side_effect = [None, snapshot_failure]
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.multiple(HARNESS, **mocks),
        ):
            HARNESS.transfer(
                "emulator-5554",
                "emulator-5556",
                Path("app.apk"),
                Path("probe.apk"),
                Path(directory),
                "phrase",
                exercise_cloud_backup=cloud,
                exercise_missing_privacy=privacy,
                exercise_partial_media=partial,
                exercise_pending_camera_draft=camera,
            )
        return mocks

    def test_failed_privacy_repair_snapshot_stays_failed_and_collects_resolved_diagnostics(
        self,
    ):
        failure = RuntimeError("strict journal fingerprint mismatch")
        with self.assertRaises(RuntimeError) as caught:
            self.transfer_probes(privacy=True, snapshot_failure=failure)
        self.assertIs(
            failure,
            caught.exception,
            "Diagnostics cannot convert a failed acceptance into success",
        )
        self.assertEqual(
            ["blocked", "blocked", "resolved"],
            [call.args[1] for call in self.last_mocks["privacy_probe"].call_args_list],
        )

    def test_repaired_privacy_must_also_allow_the_next_native_backup(self):
        mocks = self.transfer_probes(privacy=True)
        backups = [
            item
            for item in mocks["adb"].call_args_list
            if item.args[1:4] == ("shell", "bmgr", "backupnow")
        ]
        self.assertEqual(
            2,
            len(backups),
            "Test both blocked backup and post-repair backup completion",
        )

    def test_post_repair_backup_refusal_remains_a_failed_acceptance(self):
        with self.assertRaisesRegex(
            RuntimeError, "Native backup is still refused after privacy repair"
        ):
            self.transfer_probes(privacy=True, post_repair_backup_succeeded=False)

    def test_device_transfer_verifies_originals_both_before_and_after_restart(self):
        mocks = self.transfer_probes()
        calls = [
            call
            for call in mocks["probe"].call_args_list
            if call.args[0] == "emulator-5556"
        ]
        self.assertEqual(
            2,
            len(calls),
            "Both initial acceptance and cold restart must verify original bytes",
        )
        self.assertEqual(
            ["d2d", "d2d"], [call.kwargs.get("recording_transport") for call in calls]
        )

    def test_partial_media_still_uses_device_transfer_recording_contract(self):
        mocks = self.transfer_probes(partial=True)
        probe = mocks["probe"].call_args
        self.assertEqual("partial-media", probe.args[1])
        self.assertEqual("d2d", probe.kwargs.get("recording_transport"))

    def test_pending_camera_replay_preserves_device_transfer_recording_contract(self):
        mocks = self.transfer_probes(camera=True)
        calls = [
            call
            for call in mocks["probe"].call_args_list
            if call.args[0] == "emulator-5556"
        ]
        self.assertEqual(
            3,
            len(calls),
            "Camera acceptance must run before replay, after replay, and after restart",
        )
        self.assertEqual(
            ["d2d"] * 3, [call.kwargs.get("recording_transport") for call in calls]
        )

    def test_cloud_verifies_originals_absent_both_before_and_after_restart(self):
        mocks = self.transfer_probes(cloud=True)
        calls = [
            call
            for call in mocks["probe"].call_args_list
            if call.args[0] == "emulator-5556"
        ]
        self.assertEqual(2, len(calls))
        self.assertEqual(
            ["cloud", "cloud"],
            [call.kwargs.get("recording_transport") for call in calls],
        )

    def test_privacy_mutation_uses_cloud_database_and_preserves_blocked_repaired_oracles(
        self,
    ):
        mocks = self.transfer_probes(privacy=True)
        self.assertTrue(
            mocks["selected_database_snapshot"].call_args.kwargs["cloud_backup"]
        )
        mocks["assert_cloud_backup_contents"].assert_called_once()
        self.assertEqual(
            ["blocked", "blocked", "resolved", "resolved"],
            [call.args[1] for call in mocks["privacy_probe"].call_args_list],
        )
        mocks["verify_privacy_backup_refusal"].assert_called_once()
        self.assertEqual(
            0,
            mocks["probe"].call_count,
            "Privacy recovery must not run ordinary readable-content acceptance",
        )


if __name__ == "__main__":
    unittest.main()
