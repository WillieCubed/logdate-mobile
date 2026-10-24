"""Real stdin and missing-tool acceptance for native formatter routes."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
HELPER = Path(__file__).with_name("native-addons.py")
RULES = Path(__file__).with_name("native-addons-rules.json")


class NativeAddonTests(unittest.TestCase):
    def test_real_stdin_is_clean_idempotent_and_does_not_write_source(self):
        samples = {
            "c": ("sample.c", b"int sample(){return 1;}\n"),
            "swift": ("sample.swift", b"let value=1\n"),
            "terraform": ("sample.tf", b'variable "region" {default="sample"}\n'),
            "metal": ("sample.metal", b"kernel void sample(){int value=1;}\n"),
        }
        rules = json.loads(RULES.read_text())["rules"]
        for rule in rules:
            tool = rule["argv"][-3]
            with self.subTest(tool=tool), tempfile.TemporaryDirectory() as directory:
                name, source = samples[tool]
                target = Path(directory) / name
                before = b"private unstaged draft\n"
                target.write_bytes(before)
                command = [sys.executable, str(HELPER), tool, "--path", str(target)]
                first = subprocess.run(
                    command, input=source, capture_output=True, cwd=ROOT
                )
                self.assertEqual(first.returncode, 0, first.stderr.decode())
                self.assertNotEqual(first.stdout, source)
                self.assertTrue(first.stdout.endswith(b"\n"))
                second = subprocess.run(
                    command, input=first.stdout, capture_output=True, cwd=ROOT
                )
                self.assertEqual(second.returncode, 0, second.stderr.decode())
                self.assertEqual(first.stdout, second.stdout)
                self.assertEqual(target.read_bytes(), before)

    def test_commit_does_not_install_missing_tools(self):
        for rule in json.loads(RULES.read_text())["rules"]:
            tool = rule["argv"][-3]
            with self.subTest(tool=tool), tempfile.TemporaryDirectory() as directory:
                result = subprocess.run(
                    [sys.executable, str(HELPER), tool, "--path", "sample.swift"],
                    env=dict(os.environ, XDG_CACHE_HOME=directory),
                    input=b"let value=1\n",
                    capture_output=True,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b"")
                self.assertIn(b"setup", result.stderr.lower())
                self.assertEqual(list(Path(directory).iterdir()), [])


if __name__ == "__main__":
    unittest.main()
