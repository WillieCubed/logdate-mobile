import base64
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "scripts" / "materialize-ci-debug-keystore.py"


class CiDebugKeystoreTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = tempfile.TemporaryDirectory()
        cls.keystore = Path(cls.fixture.name) / "fixture.keystore"
        subprocess.run([
            "keytool", "-genkeypair", "-keystore", str(cls.keystore),
            "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey",
            "-dname", "CN=CI fixture", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
        ], check=True, capture_output=True)
        listing = subprocess.check_output([
            "keytool", "-list", "-v", "-keystore", str(cls.keystore),
            "-storepass", "android", "-alias", "androiddebugkey",
        ], text=True)
        cls.sha1 = re.search(r"SHA1:\s*([0-9A-F:]+)", listing)[1]
        cls.encoded = base64.b64encode(cls.keystore.read_bytes()).decode()

    @classmethod
    def tearDownClass(cls):
        cls.fixture.cleanup()

    def invoke(self, destination, **overrides):
        env = dict(os.environ, GITHUB_ACTIONS="true",
                   LOGDATE_CI_DEBUG_KEYSTORE_BASE64=self.encoded,
                   LOGDATE_CI_DEBUG_CERT_SHA1=self.sha1)
        env.update(overrides)
        return subprocess.run(["python3", str(SCRIPT), str(destination)],
                              env=env, capture_output=True, text=True)

    def test_materializes_only_the_expected_signer_with_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "android" / "debug.keystore"
            result = self.invoke(destination)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(destination.read_bytes(), self.keystore.read_bytes())
            self.assertEqual(destination.stat().st_mode & 0o777, 0o600)
            self.assertNotIn(self.encoded, result.stdout + result.stderr)

    def test_bad_inputs_preserve_an_existing_signer(self):
        for overrides in [
            {"LOGDATE_CI_DEBUG_KEYSTORE_BASE64": "not base64"},
            {"LOGDATE_CI_DEBUG_CERT_SHA1": "00" * 20},
            {"LOGDATE_CI_DEBUG_CERT_SHA1": ""},
            {"GITHUB_ACTIONS": "false"},
        ]:
            with self.subTest(overrides=list(overrides)), tempfile.TemporaryDirectory() as directory:
                destination = Path(directory) / "debug.keystore"
                destination.write_bytes(b"existing signer")
                result = self.invoke(destination, **overrides)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(destination.read_bytes(), b"existing signer")
                self.assertNotIn(self.encoded, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
