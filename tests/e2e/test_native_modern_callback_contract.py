"""Keep the managed callback driver's strict count aligned with its source contract."""

import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class NativeModernCallbackContractTest(unittest.TestCase):
    def test_driver_requires_every_case_in_the_authoritative_manifest(self):
        contract = json.loads(
            (ROOT / "tests/e2e/native-modern-callback-cases.json").read_text()
        )
        expected = [
            (classname, method)
            for classname, methods in contract.items()
            for method in methods
        ]
        self.assertTrue(expected)
        self.assertEqual(len(expected), len(set(expected)))
        driver = (
            ROOT
            / "tools/native-restore-driver/src/androidTest/kotlin/app/logdate/tools/restoredriver/NativeModernRestoreCallbackTest.kt"
        ).read_text()
        guard = re.search(r"assertEquals\((\d+), cases\.size\)", driver)
        self.assertIsNotNone(guard, "Retain the driver's strict expected-case guard")
        self.assertEqual(
            len(expected),
            int(guard.group(1)),
            "Update the driver when the approved callback manifest changes",
        )


if __name__ == "__main__":
    unittest.main()
