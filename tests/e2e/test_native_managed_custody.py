from pathlib import Path
import os
import io
import json
import tempfile
import runpy
import subprocess
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
NATIVE = runpy.run_path(str(ROOT / "tests/e2e/native-backup-acceptance.py"))
MEDIA = runpy.run_path(str(ROOT / "tests/e2e/native-missing-media-acceptance.py"))
WORK = runpy.run_path(str(ROOT / "tests/e2e/native-restore-work-restart-acceptance.py"))
TARGET = runpy.run_path(str(ROOT / "tests/e2e/managed-native-target.py"))


class NativeManagedCustodyTest(unittest.TestCase):
    def test_native_device_access_requires_managed_custody_before_spawning(self):
        for harness in (NATIVE, MEDIA, WORK):
            with self.subTest(harness=harness["__file__"]), patch.dict(os.environ, {}, clear=True), patch.object(
                subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"unsafe", b"")
            ) as run:
                with self.assertRaisesRegex(RuntimeError, "managed-native-restore-acceptance"):
                    harness["adb"]("emulator-5556", "shell", "true")
                run.assert_not_called()

    def test_every_native_command_uses_the_live_custody_proxy(self):
        with patch.dict(os.environ, {"LOGDATE_MANAGED_CUSTODY": "/private/test-custody.json"}), patch.object(
            subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"retained", b"")
        ) as run:
            self.assertEqual(b"retained", NATIVE["adb"]("emulator-5556", "exec-in", "sh", input_bytes=b"script", timeout=7))
            call = run.call_args
            self.assertEqual(
                [sys.executable, str(ROOT / "tests/e2e/managed-native-target.py"), "-s", "emulator-5556", "exec-in", "sh"],
                call.args[0],
            )
            self.assertEqual(b"script", call.kwargs["input"])
            self.assertEqual(7, call.kwargs["timeout"])
            self.assertTrue(call.kwargs["check"])


    def test_streaming_instrumentation_uses_the_same_custody_proxy(self):
        function = MEDIA["instrument"]
        result = b"INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\n"
        process = unittest.mock.Mock(stdout=io.BytesIO(result))
        process.wait.return_value = 0
        process.poll.return_value = 0
        with tempfile.TemporaryDirectory() as temporary, patch.dict(
            os.environ, {"LOGDATE_MANAGED_CUSTODY": "/private/test-custody.json"}
        ), patch.dict(function.__globals__, {"safe_target": lambda _: None}), patch.object(
            subprocess, "Popen", return_value=process
        ) as started:
            function("emulator-5556", "fixture.Case", Path(temporary) / "instrument.log")
            self.assertEqual(sys.executable, started.call_args.args[0][0])
            self.assertEqual(str(ROOT / "tests/e2e/managed-native-target.py"), started.call_args.args[0][1])

    def test_proxy_replaces_itself_with_adb_so_timeout_cannot_leave_a_writer_child(self):
        function = TARGET["proxy_main"]
        with tempfile.TemporaryDirectory() as temporary:
            custody = Path(temporary) / "custody.json"
            custody.write_text(json.dumps({"phase": "active", "adb": "/sdk/adb", "targets": {"emulator-5556": {}}}))
            with patch.dict(os.environ, {"LOGDATE_MANAGED_CUSTODY": str(custody)}), patch.dict(
                function.__globals__, {"require_binding": lambda *args: None}
            ), patch.object(os, "execv") as replaced, patch.object(subprocess, "call") as forked:
                function(["-s", "emulator-5556", "shell", "true"])
                replaced.assert_called_once_with("/sdk/adb", ["/sdk/adb", "-s", "emulator-5556", "shell", "true"])
                forked.assert_not_called()


if __name__ == "__main__":
    unittest.main()
