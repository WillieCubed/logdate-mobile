"""Failed managed callback runs retain diagnostics without an acceptance receipt."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("managed-modern-callback-acceptance.sh")
RESULTS = "tools/native-restore-driver/build/outputs/androidTest-results/managedDevice/debug/restoreCallbackApi36_1"
ADDITIONAL = (
    "tools/native-restore-driver/build/intermediates/managed_device_android_test_additional_output/"
    "debugAndroidTest/restoreCallbackApi36_1DebugAndroidTest"
)


class ManagedModernCallbackEvidenceTest(unittest.TestCase):
    def run_fixture(self, root, managed_exit, verifier_exit, replace_apk=False):
        scripts = root / "tests/e2e"
        scripts.mkdir(parents=True)
        entrypoint = scripts / SCRIPT.name
        shutil.copyfile(SCRIPT, entrypoint)
        (scripts / "native-modern-callback-cases.json").write_text("{}")
        (scripts / "native-modern-callback-driver-cases.json").write_text("{}")
        (scripts / "native-instrumentation-results.py").write_text(f"raise SystemExit({verifier_exit})\n")
        for name in ["app/android-main/build/outputs/apk/debug/android-main-debug.apk",
                     "app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk"]:
            apk = root / name
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"synthetic APK identity")
        gradle = root / "gradlew"
        replacement = "echo changed-build-output > app/android-main/build/outputs/apk/debug/android-main-debug.apk\n" if replace_apk else ""
        gradle.write_text(
            "#!/bin/bash\nset -eu\n"
            'if [[ "$*" == *restoreCallbackApi36_1DebugAndroidTest* ]]; then\n'
            f"mkdir -p {RESULTS} {ADDITIONAL}\n"
            f"echo retained-managed-result > {RESULTS}/result.txt\n"
            f"echo nested-callback-crash > {ADDITIONAL}/modern-callback-instrumentation.log\n"
            f"{replacement}"
            f"exit {managed_exit}\nfi\n"
        )
        gradle.chmod(0o700)
        output = root / "private-evidence"
        result = subprocess.run(["bash", str(entrypoint), str(output)], cwd=root, capture_output=True, text=True)
        return result, output

    def assert_retained_diagnostics(self, output):
        self.assertTrue((output / "results/result.txt").is_file(), "The failed managed result must be retained")
        self.assertTrue((output / "additional-output/modern-callback-instrumentation.log").is_file(),
                        "The nested callback result must be retained")
        self.assertEqual("retained-managed-result\n", (output / "results/result.txt").read_text())
        self.assertEqual(
            "nested-callback-crash\n",
            (output / "additional-output/modern-callback-instrumentation.log").read_text(),
        )
        self.assertFalse((output / "acceptance.json").exists())

    def test_gradle_failure_preserves_nested_callback_diagnostics_and_failure_exit(self):
        with tempfile.TemporaryDirectory() as temporary:
            result, output = self.run_fixture(Path(temporary), managed_exit=9, verifier_exit=0)
            self.assertEqual(9, result.returncode, result.stderr)
            self.assert_retained_diagnostics(output)

    def test_result_rejection_preserves_diagnostics_without_claiming_acceptance(self):
        with tempfile.TemporaryDirectory() as temporary:
            result, output = self.run_fixture(Path(temporary), managed_exit=0, verifier_exit=7)
            self.assertEqual(7, result.returncode, result.stderr)
            self.assert_retained_diagnostics(output)

    def test_success_receipt_keeps_attested_hashes_when_shared_build_outputs_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            result, output = self.run_fixture(Path(temporary), managed_exit=0, verifier_exit=0, replace_apk=True)
            self.assertEqual(0, result.returncode, result.stderr)
            receipt = json.loads((output / "acceptance.json").read_text())
            self.assertEqual(hashlib.sha256(b"synthetic APK identity").hexdigest(), receipt["apks"]["android-main-debug.apk"])


if __name__ == "__main__":
    unittest.main()
