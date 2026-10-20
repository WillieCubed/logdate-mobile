from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = ROOT / "scripts/validation/validate-commit-message.sh"


class CommitMessageValidationTest(unittest.TestCase):
    def validate(self, message):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "message.txt"
            path.write_text(message)
            return subprocess.run(["bash", str(VALIDATOR), "--file", str(path), "--context", "pre-push"],
                                  cwd=ROOT, capture_output=True, text=True)

    def test_long_valid_body_cannot_break_the_title_reader(self):
        message = "chore(restore): Verify released Android backup compatibility\n\n" + "Preserve retained journal metadata across the upgrade.\n" * 3000
        result = self.validate(message)
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)

    def test_long_body_still_rejects_forbidden_history_labels(self):
        message = "chore(restore): Verify released Android backup compatibility\n\n" + "Preserve journal metadata.\n" * 3000 + "phase 9\n"
        result = self.validate(message)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("PHASE:", result.stdout)


if __name__ == "__main__":
    unittest.main()
