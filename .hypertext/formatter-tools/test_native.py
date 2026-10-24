"""Exercise the native staged-byte formatter without touching source files."""

import os
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HELPER = Path(__file__).with_name("native.py")


class NativeFormatterTests(unittest.TestCase):
    def test_unknown_formatter_fails_closed(self):
        result = subprocess.run(
            [sys.executable, str(HELPER), "unknown", "sample.kt"],
            input=b"val x=1\n",
            capture_output=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"Unknown native formatter", result.stderr)

    def test_missing_cache_blocks_without_installing_during_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            environment = dict(os.environ, XDG_CACHE_HOME=directory)
            result = subprocess.run(
                [sys.executable, str(HELPER), "ktlint", "sample.kt"],
                env=environment,
                input=b"val x=1\n",
                capture_output=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, b"")
            self.assertIn(b"--setup", result.stderr)
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_invalid_kotlin_never_returns_success_or_empty_success(self):
        result = subprocess.run(
            [sys.executable, str(HELPER), "ktlint", "Broken.kt"],
            input=b'val broken = "',
            capture_output=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, b"")
        self.assertTrue(result.stderr)

    def test_pinned_kotlin_stdin_is_clean_and_idempotent(self):
        command = [sys.executable, str(HELPER), "ktlint", "sample.gradle.kts"]
        first = subprocess.run(
            command, input=b"val value= listOf(1,2)\n", capture_output=True
        )
        self.assertEqual(first.returncode, 0, first.stderr.decode())
        self.assertEqual(first.stdout, b"val value = listOf(1, 2)\n")
        second = subprocess.run(command, input=first.stdout, capture_output=True)
        self.assertEqual(second.returncode, 0, second.stderr.decode())
        self.assertEqual(second.stdout, first.stdout)

    def test_relative_virtual_path_honors_repository_editorconfig(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".editorconfig").write_text(
                "root = true\n[*.{kt,kts}]\nindent_size = 2\nktlint_code_style = intellij_idea\n"
            )
            helper = root / ".hypertext/formatter-tools/native.py"
            helper.parent.mkdir(parents=True)
            shutil.copyfile(HELPER, helper)
            (root / "src").mkdir()
            result = subprocess.run(
                [sys.executable, str(helper), "ktlint", "src/Sample.kt"],
                cwd=root.parent,
                input=b"fun main() {\nprintln(1)\n}\n",
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            self.assertIn(b"\n  println(1)\n", result.stdout)

    def test_virtual_path_preserves_nested_editorconfig_and_worktree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".editorconfig").write_text(
                "root = true\n[*.{kt,kts}]\nindent_size = 2\nktlint_code_style = intellij_idea\n"
            )
            source = root / "Sample.kt"
            before = b"private draft must remain unchanged\n"
            source.write_bytes(before)
            result = subprocess.run(
                [sys.executable, str(HELPER), "ktlint", str(source)],
                cwd=root,
                input=b"fun main() {\nprintln(1)\n}\n",
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            self.assertIn(b"\n  println(1)\n", result.stdout)
            self.assertEqual(source.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
