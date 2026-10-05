import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
HOOK = ROOT / ".codex/hooks/block_unsafe_android_commands.py"


class AndroidPolicyTest(unittest.TestCase):
    def decision(self, command, key="command"):
        result = subprocess.run(
            ["python3", str(HOOK)],
            input=json.dumps({"tool_input": {key: command}, "cwd": str(ROOT)}),
            capture_output=True,
            text=True,
            check=True,
        )
        if not result.stdout.strip():
            return None
        return json.loads(result.stdout)["hookSpecificOutput"]["permissionDecision"]

    def test_inventory_and_scoped_read_only_diagnostics_are_not_blocked(self):
        for command in (
            "adb devices -l",
            "adb -s approved-phone shell pidof studio.hypertext.logdate",
            "adb -s approved-phone shell dumpsys package studio.hypertext.logdate",
            "adb -s approved-phone shell dumpsys jobscheduler studio.hypertext.logdate",
            "adb -s approved-phone logcat -d --pid=123 -v threadtime",
            "adb -s approved-phone logcat -d -t 300 --pid=123 | rg 'sync|backup'",
        ):
            with self.subTest(command=command):
                self.assertIsNone(self.decision(command))

    def test_device_mutations_and_unscoped_diagnostics_stay_blocked(self):
        for command in (
            "adb shell dumpsys package studio.hypertext.logdate",
            "adb -d logcat -d",
            "adb -s approved-phone install app.apk",
            "adb -s approved-phone uninstall studio.hypertext.logdate",
            "adb -s approved-phone shell pm clear studio.hypertext.logdate",
            "adb -s approved-phone shell pm clear co.reasonabletech.logdate",
            "adb -s approved-phone shell am instrument test/package",
            "adb -s approved-phone shell sh -c 'rm -rf /data/data/studio.hypertext.logdate'",
            "adb -s approved-phone shell pidof studio.hypertext.logdate > /tmp/log '; pm clear studio.hypertext.logdate'",
            "adb -s approved-phone logcat -c",
            "adb -s approved-phone logcat -d",
            "adb -s approved-phone logcat -d --pid=123 -f /sdcard/output",
            "bash -lc 'adb -s approved-phone uninstall studio.hypertext.logdate'",
            "/opt/android/adb -s approved-phone shell pm clear studio.hypertext.logdate",
            "adb -s approved-phone logcat -d; adb -s approved-phone shell pm clear studio.hypertext.logdate",
            "adb -s approved-phone shell 'dumpsys package studio.hypertext.logdate; pm clear studio.hypertext.logdate'",
            "fastboot devices",
            "./gradlew :app:android-main:connectedDebugAndroidTest",
            "./gradlew :app:android-main:installDebug",
        ):
            with self.subTest(command=command):
                self.assertEqual(self.decision(command), "deny")

    def test_executing_shell_script_is_checked_but_reading_it_is_allowed(self):
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / "diagnostics.sh"
            script.write_text("#!/bin/bash\nadb -s approved-phone shell pm clear studio.hypertext.logdate\n")
            self.assertEqual(self.decision(f"bash {script}"), "deny")
            self.assertIsNone(self.decision(f"cat {script}"))
            script.write_text("adb -s approved-phone shell pidof studio.hypertext.logdate\n")
            self.assertIsNone(self.decision(f"bash {script}"))

    def test_multiline_commands_check_every_device_operation(self):
        command = "adb devices -l\nadb -s approved-phone shell pm clear studio.hypertext.logdate"
        self.assertEqual(self.decision(command), "deny")

    def test_unified_exec_input_is_checked(self):
        self.assertEqual(self.decision("adb -s approved-phone shell pm clear studio.hypertext.logdate", key="cmd"), "deny")

    def test_reading_policy_source_is_not_treated_as_executing_it(self):
        self.assertIsNone(self.decision("rg -n adb .codex/hooks/block_unsafe_android_commands.py"))

    def test_quoted_heredoc_content_is_not_parsed_as_shell_syntax(self):
        command = "python3 - <<'PY'\nvalue = '''couldn't sync'''\nPY\n"
        self.assertIsNone(self.decision(command))
        self.assertEqual(self.decision(command + "adb -s approved-phone install app.apk"), "deny")

    def test_shell_heredocs_are_still_checked_for_device_commands(self):
        for shell in ("bash", "sh", "zsh"):
            command = f"{shell} <<'SH'\nadb -s approved-phone shell pm clear studio.hypertext.logdate\nSH\n"
            self.assertEqual(self.decision(command), "deny")


if __name__ == "__main__":
    unittest.main()
