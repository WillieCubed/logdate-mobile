"""Interpret AndroidJUnitRunner output without treating skipped tests as passes."""

import re


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
