import runpy
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET


RESULTS = runpy.run_path(str(Path(__file__).with_name("native-instrumentation-results.py")))
EXPECTED = {("recovery.Keys", "retainedKey"), ("recovery.Account", "sameOwnerResume")}


class ManagedRecoveryResultsTest(unittest.TestCase):
    def result(self, cases):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            root = ET.Element("testsuite")
            for classname, name, outcome in cases:
                case = ET.SubElement(root, "testcase", classname=classname, name=name)
                if outcome:
                    ET.SubElement(case, outcome)
            ET.ElementTree(root).write(directory / "TEST-recovery.xml")
            return RESULTS["managed_cases_passed"](directory, EXPECTED)

    def test_accepts_each_required_case_once(self):
        self.assertTrue(self.result([(c, n, None) for c, n in EXPECTED]))

    def test_rejects_a_replacement_case_even_when_count_matches(self):
        self.assertFalse(self.result([("recovery.Keys", "retainedKey", None), ("recovery.Account", "unrelated", None)]))

    def test_rejects_duplicate_case_instead_of_missing_case(self):
        self.assertFalse(self.result([("recovery.Keys", "retainedKey", None)] * 2))

    def test_rejects_any_skip_failure_or_error(self):
        for outcome in ("skipped", "failure", "error"):
            with self.subTest(outcome=outcome):
                self.assertFalse(self.result([("recovery.Keys", "retainedKey", outcome), ("recovery.Account", "sameOwnerResume", None)]))

    def test_rejects_unexpected_additional_cases(self):
        self.assertFalse(self.result([(c, n, None) for c, n in EXPECTED] + [("recovery.Other", "extra", None)]))

    def test_rejects_absent_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.assertFalse(RESULTS["managed_cases_passed"](Path(temporary), EXPECTED))

    def test_rejects_empty_contract(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.assertFalse(RESULTS["managed_cases_passed"](Path(temporary), set()))

    def test_rejects_suite_setup_error_without_a_failed_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = ET.Element("testsuite", errors="1")
            for classname, name in EXPECTED:
                ET.SubElement(root, "testcase", classname=classname, name=name)
            ET.ElementTree(root).write(Path(temporary) / "TEST-recovery.xml")
            self.assertFalse(RESULTS["managed_cases_passed"](Path(temporary), EXPECTED))

    def test_cli_prints_only_the_classes_from_the_required_contract(self):
        script = Path(__file__).with_name("native-instrumentation-results.py")
        contract = Path(__file__).with_name("native-key-recovery-cases.json")
        result = subprocess.run([sys.executable, str(script), "--expected-cases", str(contract), "--print-classes"],
                                capture_output=True, check=True, text=True)
        self.assertEqual(",".join(json.loads(contract.read_text())) + "\n", result.stdout)

    def test_required_contract_matches_current_test_sources(self):
        root = Path(__file__).resolve().parents[2]
        for filename in ("native-key-recovery-cases.json", "native-recording-recovery-cases.json", "native-alarm-delivery-cases.json", "native-modern-callback-cases.json", "native-modern-callback-driver-cases.json"):
            contract = json.loads(Path(__file__).with_name(filename).read_text())
            for classname, methods in contract.items():
                module = "tools/native-restore-driver" if classname.startswith("app.logdate.tools.") else "app/android-main"
                source = root / module / "src/androidTest/kotlin" / (classname.replace(".", "/") + ".kt")
                declarations = re.findall(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))", source.read_text())
                with self.subTest(classname=classname):
                    self.assertEqual(set(methods), {quoted or ordinary for quoted, ordinary in declarations})


if __name__ == "__main__":
    unittest.main()
