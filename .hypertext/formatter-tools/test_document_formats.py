import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("document-formats.py")


class BuildSettingsTests(unittest.TestCase):
    def test_assignment_spacing_preserves_values_conditions_and_directives(self):
        source = b'#include? "Generated.xcconfig"\n// comment\nBUNDLE_ID=studio.example\nFLAGS[sdk=iphoneos*]+= $(inherited)  -DNAME="a  b"\nPATHS = one \\\n    two  \\\n    three\n'
        expected = b'#include? "Generated.xcconfig"\n// comment\nBUNDLE_ID = studio.example\nFLAGS[sdk=iphoneos*] += $(inherited)  -DNAME="a  b"\nPATHS = one \\\n    two  \\\n    three\n'
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "xcconfig"], input=source, capture_output=True
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(result.stdout, expected)

    def test_block_comments_and_quoted_comment_markers_are_preserved(self):
        source = b'/*\nEXAMPLE=not a setting\n*/\nVALUE="/* literal */"\nOTHER?=value /* comment\nNEXT=still comment\n*/\n'
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "xcconfig"], input=source, capture_output=True
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(
            result.stdout,
            b'/*\nEXAMPLE=not a setting\n*/\nVALUE = "/* literal */"\nOTHER ?= value /* comment\nNEXT=still comment\n*/\n',
        )


if __name__ == "__main__":
    unittest.main()
