import contextlib
import importlib.util
import io
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch


TESTS = Path(__file__).parent


def load_script(name, filename):
    spec = importlib.util.spec_from_file_location(name, TESTS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


MISSING_MEDIA = load_script("missing_media_acceptance", "native-missing-media-acceptance.py")
PERMISSIONS = load_script("permission_acceptance", "native-permission-acceptance.py")


class AcceptanceOutputDirectoryTest(unittest.TestCase):
    def assert_main_creates_private_nested_output(self, module, extra_args):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "acceptance" / "run-1"
            argv = [
                "acceptance.py",
                "--emulator",
                "emulator-5554",
                "--app-apk",
                "app.apk",
                "--test-apk",
                "test.apk",
                "--output",
                str(output),
                *extra_args,
            ]
            original_umask = os.umask(0o077)
            try:
                with patch.object(sys, "argv", argv), patch.object(
                    module,
                    "run",
                    return_value={"serial": "emulator-5554", "accepted": True},
                ), contextlib.redirect_stdout(io.StringIO()):
                    module.main()
            finally:
                os.umask(original_umask)

            self.assertEqual(0o700, stat.S_IMODE(output.stat().st_mode))
            self.assertTrue((output / "acceptance.json").is_file())

    def test_missing_media_runner_creates_nested_output_directory(self):
        self.assert_main_creates_private_nested_output(MISSING_MEDIA, ["--synthetic-data"])

    def test_permission_runner_creates_nested_output_directory(self):
        self.assert_main_creates_private_nested_output(PERMISSIONS, ["--synthetic-data"])


if __name__ == "__main__":
    unittest.main()
