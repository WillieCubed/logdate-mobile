"""Interpret AndroidJUnitRunner output without treating skipped tests as passes."""

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def managed_cases_passed(directory, expected_cases):
    """Require every named managed-device case once, without failures or skips."""
    if not expected_cases or len(expected_cases) != len(set(expected_cases)):
        return False
    roots = [ET.parse(report).getroot() for report in Path(directory).glob("TEST-*.xml")]
    if any(int(suite.get(outcome, "0")) for root in roots for suite in root.iter()
           for outcome in ("errors", "failures", "skipped")):
        return False
    cases = [case for root in roots for case in root.iter("testcase")]
    actual = Counter((case.get("classname"), case.get("name")) for case in cases)
    return actual == Counter(expected_cases) and not any(
        case.find(outcome) is not None for case in cases for outcome in ("skipped", "failure", "error")
    )


def instrumentation_passed(output, expected_tests=1):
    count = str(expected_tests).encode()
    noun = b"test" if expected_tests == 1 else b"tests"
    summary = rb"^OK \(" + count + b" " + noun + rb"\)\r?$"
    failed_status = rb"^INSTRUMENTATION_STATUS_CODE:[ \t]*-[0-9]+[ \t]*\r?$"
    return (
        re.search(summary, output, re.MULTILINE) is not None
        and re.search(failed_status, output, re.MULTILINE) is None
        and not any(marker in output for marker in (
            b"Process crashed", b"FAILURES", b"INSTRUMENTATION_FAILED:",
            b"INSTRUMENTATION_RESULT: shortMsg=",
        ))
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Verify the exact required managed-device recovery cases")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--managed-results", type=Path)
    mode.add_argument("--print-classes", action="store_true")
    parser.add_argument("--expected-cases", type=Path, required=True)
    arguments = parser.parse_args()
    contract = json.loads(arguments.expected_cases.read_text())
    if not isinstance(contract, dict) or not contract or any(
        not isinstance(classname, str) or not classname or not isinstance(methods, list) or not methods or
        any(not isinstance(method, str) or not method for method in methods)
        for classname, methods in contract.items()
    ):
        raise SystemExit("Expected a nonempty class-to-method recovery contract")
    expected = [(classname, method) for classname, methods in contract.items() for method in methods]
    if len(expected) != len(set(expected)):
        raise SystemExit("Recovery contract contains duplicate cases")
    if arguments.print_classes:
        print(",".join(contract))
    elif not managed_cases_passed(arguments.managed_results, expected):
        raise SystemExit("Managed recovery cases must match the contract exactly, without skips, failures, or duplicates")
    else:
        print(f"Verified all {len(expected)} required managed recovery cases")
