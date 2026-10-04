import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

CHECKER = Path(__file__).resolve().parents[2] / "validation/check-source-file-sizes.py"


class SourceFileSizeTests(unittest.TestCase):
    def check(self, path):
        return subprocess.run([sys.executable, str(CHECKER), str(path)],
                              capture_output=True, text=True, check=False)

    def test_500_lines_are_allowed_and_501_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Journal.swift"
            path.write_text("let value = 1\n" * 500)
            self.assertEqual(self.check(path).returncode, 0)
            path.write_text("let value = 1\n" * 501)
            result = self.check(path)
            self.assertEqual(result.returncode, 1)
            self.assertIn("Journal.swift: 501 lines", result.stdout)

    def test_directory_checks_nested_code_without_counting_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "nested").mkdir()
            (root / "fixture.json").write_text("{}\n" * 900)
            path = root / "nested" / "Backup.kt"
            path.write_text("val value = 1\n" * 501)
            self.assertEqual(self.check(root).returncode, 1)
            path.write_text("val value = 1\n")
            self.assertEqual(self.check(root).returncode, 0)

    def test_deleted_file_in_commit_history_is_not_a_violation(self):
        result = subprocess.run([sys.executable, str(CHECKER), "--stdin"],
                                input="deleted-source.swift\n", capture_output=True,
                                text=True, check=False)
        self.assertEqual(result.returncode, 0)

    def test_missing_requested_source_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertNotEqual(self.check(Path(directory) / "missing.swift").returncode, 0)


if __name__ == "__main__":
    unittest.main()
