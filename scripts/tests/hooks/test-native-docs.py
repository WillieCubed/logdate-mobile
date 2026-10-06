import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]

class SecretDocumentationTests(unittest.TestCase):
    def check_hook(self, path, content, tracked):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            common = root / 'scripts/validation/hook-common.sh'
            common.parent.mkdir(parents=True)
            shutil.copyfile(ROOT / 'scripts/validation/hook-common.sh', common)
            document = root / path
            document.parent.mkdir(parents=True, exist_ok=True)
            document.write_text('Company signing instructions.\n')
            if tracked:
                subprocess.run(['git', 'add', path], cwd=root, check=True)
                subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.com', 'commit', '-q', '-F', '-'], input='chore: Create fixture\n', text=True, cwd=root, check=True)
            document.write_text(content)
            subprocess.run(['git', 'add', path], cwd=root, check=True)
            return subprocess.run(['bash', str(ROOT / '.githooks/pre-commit')], cwd=root, capture_output=True, text=True).returncode

    def test_tracked_secret_runbook_accepts_company_team_correction(self):
        self.assertEqual(self.check_hook('docs/runbook/release-secrets.md', 'Company signing team T95VDD3A4W.\n', True), 0)

    def test_new_secret_file_stays_blocked(self):
        self.assertNotEqual(self.check_hook('secret.md', 'An unknown value.\n', False), 0)

    def test_private_key_in_tracked_document_stays_blocked(self):
        self.assertNotEqual(self.check_hook('docs/runbook/release-secrets.md', '-----BEGIN ' + 'PRIVATE KEY-----\n', True), 0)

if __name__ == '__main__':
    unittest.main()
