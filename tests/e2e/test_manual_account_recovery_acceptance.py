from pathlib import Path
import json
import hashlib
import os
import runpy
import tempfile
import unittest
from unittest.mock import Mock, patch
import subprocess


MODULE = runpy.run_path(str(Path(__file__).with_name("manual-account-recovery-acceptance.py")))


class ManualAccountRecoveryAcceptanceTest(unittest.TestCase):
    def test_destination_installs_a_package_checked_private_passkey_provider(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            provider = root / "provider-source.apk"
            provider.write_bytes(b"synthetic provider")
            aapt2 = root / "sdk/build-tools/36.1.0/aapt2"
            aapt2.parent.mkdir(parents=True)
            aapt2.touch()
            adb = Mock()
            execute = Mock(return_value=subprocess.CompletedProcess([], 0, b"app.logdate.tools.passkeyprovider\n"))
            pinned = MODULE["install_passkey_provider"](
                "emulator-5558", root / "private", provider, root / "sdk", execute=execute, adb=adb)
            self.assertEqual(provider.read_bytes(), pinned.read_bytes())
            self.assertEqual(0o400, pinned.stat().st_mode & 0o777)
            self.assertEqual(str(aapt2), execute.call_args.args[0][0])
            adb.assert_called_once_with("emulator-5558", "install", "-r", "-t", str(pinned))
            self.assertEqual(hashlib.sha256(provider.read_bytes()).digest(), hashlib.sha256(pinned.read_bytes()).digest())

    def test_unexpected_provider_package_never_reaches_the_managed_guest(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            provider = root / "provider-source.apk"
            provider.write_bytes(b"wrong provider")
            aapt2 = root / "sdk/build-tools/36.1.0/aapt2"
            aapt2.parent.mkdir(parents=True)
            aapt2.touch()
            adb = Mock()
            execute = Mock(return_value=subprocess.CompletedProcess([], 0, b"unrelated.package\n"))
            with self.assertRaisesRegex(RuntimeError, "unexpected package identity"):
                MODULE["install_passkey_provider"](
                    "emulator-5558", root / "private", provider, root / "sdk", execute=execute, adb=adb)
            adb.assert_not_called()

    def test_proof_handoff_creates_fresh_private_directory_before_atomic_write(self):
        proof = json.dumps({"synthetic": True, "sourceFingerprint": "source", "bootstrapAccessToken": "private"}).encode()
        calls = []

        def adb(_serial, *arguments, **_kwargs):
            calls.append(arguments)
            return proof if arguments[:1] == ("exec-out",) else b""

        with tempfile.TemporaryDirectory() as folder:
            with patch.dict(MODULE["H"], adb=adb, private_file=Mock()):
                MODULE["publish_source_fixture"]("emulator-5556", "emulator-5558", Path(folder))
        handoff = calls[1][-1]
        self.assertIn("mkdir -p no_backup", handoff)
        self.assertLess(handoff.index("mkdir -p no_backup"), handoff.index("cat > no_backup"))

    def test_focused_pair_runs_wrong_then_correct_phrase_journey_without_claiming_other_oracles(self):
        phases = []
        def instrument(_serial, selector, _output, **kwargs):
            phases.append((selector, kwargs.get("extras")))

        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "run"
            with patch.dict(MODULE["MEDIA"], safe_target=Mock(), instrument=instrument), \
                 patch.dict(MODULE["H"], radio_state=Mock(return_value=(False, b"1")),
                            restore_radio_state=Mock(), private_file=Mock()), \
                 patch.dict(MODULE["run"].__globals__, publish_source_fixture=Mock(), install_passkey_provider=Mock()), \
                 patch.dict(os.environ, ANDROID_HOME=str(target)):
                receipt = MODULE["run"]("emulator-5556", "emulator-5558", target, focused=True)
            self.assertEqual(3, len(phases))
            self.assertIn("manual-account-recover", phases[-1][1])
            self.assertTrue(receipt["wrongAccountPhraseRejectedBeforeKeyMutation"])
            self.assertFalse(receipt["coldOfflineReadability"])
            self.assertFalse(receipt["nativeCredentialCallbackAutomaticRetryAccepted"])


if __name__ == "__main__":
    unittest.main()
