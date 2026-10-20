from pathlib import Path
import hashlib
import json
import runpy
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-released-upgrade-acceptance.py")))


class ReleasedUpgradeAcceptanceTest(unittest.TestCase):
    def fixture(self, root):
        document = {"baselineVersion": 205, "baselineSchema": 55, "candidateVersion": 207, "candidateSchema": 57}
        for role in ("baselineApp", "baselineProbe", "candidateApp"):
            apk = root / (role + ".apk")
            apk.write_bytes(role.encode())
            document[role] = {"path": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}
        path = root / "fixture.json"
        path.write_text(json.dumps(document))
        path.chmod(0o600)
        return path, document

    def test_cleanup_attempts_both_radios_and_preserves_the_primary_failure(self):
        calls = []
        def restore(serial, state):
            calls.append(serial)
            if serial == "source":
                raise RuntimeError("first radio unavailable")
        cleanup = MODULE["restore_radios"]
        with patch.dict(MODULE["H"], restore_radio_state=restore):
            with self.assertRaises(RuntimeError):
                cleanup({"source": "on", "destination": "off"})
            self.assertEqual(["source", "destination"], calls)
            calls.clear()
            primary = ValueError("original probe failure")
            cleanup({"source": "on", "destination": "off"}, primary)
            self.assertEqual(["source", "destination"], calls)
            self.assertEqual(1, len(primary.__notes__))

    def test_changed_apks_and_downgrade_fixtures_fail_before_installing(self):
        for defect in ("hash", "version", "schema", "privacy"):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                fixture, document = self.fixture(root)
                if defect == "hash":
                    Path(document["baselineApp"]["path"]).write_bytes(b"replaced")
                if defect == "version":
                    document["candidateVersion"] = 204
                if defect == "schema":
                    document["baselineSchema"] = 54
                fixture.write_text(json.dumps(document))
                if defect == "privacy":
                    fixture.chmod(0o644)
                with self.assertRaises((ValueError, RuntimeError)):
                    MODULE["fixture_artifacts"](fixture, root)

    def test_old_agent_backs_up_before_upgrade_and_preservation_precedes_mutating_oracles(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fixture, _ = self.fixture(root)
            candidate_probe = root / "probe.apk"
            candidate_probe.write_bytes(b"candidate-probe")
            events = []
            snapshot = {"databases/logdate": {"sha256": "encrypted-retained-database"}}

            def adb(serial, *arguments, **kwargs):
                if arguments[:2] == ("exec-out", "tar"):
                    return b"old-native-transport"
                if arguments[:2] == ("exec-out", "run-as"):
                    return b"{}"
                if arguments[:3] == ("shell", "bmgr", "restore"):
                    events.append((serial, "native-restore"))
                    return b"restoreFinished: 0"
                if arguments[:3] == ("shell", "am", "start"):
                    events.append((serial, "launch"))
                return b""

            def backup(serial, output, verify_after_backup):
                events.append((serial, "backup55"))
                verify_after_backup()
                return {}, snapshot

            def stage(serial, name, version, schema, output):
                events.append((serial, name))
                self.assertEqual((205, 55) if name.startswith("baseline") else (207, 57), (version, schema))

            def installed(serial, app, probe):
                events.append((serial, "install205" if app.name == "baselineApp.apk" else "install207"))

            def timeline(serial, *args, **kwargs):
                self.assertIn((serial, "launch"), events)
                events.append((serial, "timeline"))

            def legacy(serial, name, *args, **kwargs):
                events.append((serial, "legacy-" + name))

            helpers = {"radio_state": lambda serial: "off", "restore_radio_state": lambda *args: None,
                       "prepare": lambda *args, **kwargs: None, "set_offline": lambda *args: None,
                       "adb": adb, "probe": legacy, "recording_policy_probe": lambda *args: None,
                       "backup_source": backup, "package_file_snapshot": lambda *args: snapshot,
                       "wait_for_timeline": timeline}
            options = SimpleNamespace(source="emulator-5556", destination="emulator-5558", seed_empty_source=True,
                                      output=root / "results", fixture_environment=fixture, test_apk=candidate_probe)
            globals_ = MODULE["run"].__globals__
            with patch.dict(globals_, require_owned_pair=lambda *args: None, install=installed, probe=stage,
                            write_destination_marker=lambda *args: None), patch.dict(MODULE["H"], helpers):
                result = MODULE["run"](options)
            self.assertLess(events.index((options.source, "backup55")), events.index((options.source, "install207")))
            for serial, last_preserved, mutation in ((options.source, "upgrade-restart", "legacy-verify-in-place"),
                                                      (options.destination, "restore-restart", "legacy-verify")):
                self.assertLess(events.index((serial, last_preserved)), events.index((serial, mutation)))
            self.assertEqual(2, events.count((options.destination, "native-restore")))
            self.assertTrue(result["releasedInstalledUpgrade"])
            self.assertFalse(result["googleProviderAccepted"])
            self.assertFalse(result["playSignedArtifact"])


if __name__ == "__main__":
    unittest.main()
