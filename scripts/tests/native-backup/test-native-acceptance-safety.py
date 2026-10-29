#!/usr/bin/env python3
"""Physical serials must be rejected before the native acceptance harness calls adb."""

import unittest

from native_safety_transport import NativeTransportSafety
from native_safety_workflow import NativeWorkflowSafety
from native_safety_platform import NativePlatformSafety
from native_safety_probes import NativeProbesSafety
from native_safety_source import NativeSourceSafety
from native_safety_integrity import NativeIntegritySafety
from native_safety_backup import NativeBackupSafety
from native_safety_retained import NativeRetainedSafety
from native_safety_payloads import NativePayloadsSafety
from native_safety_support import CredentialsBackupFixture


class NativeAcceptanceSafetyTest(
    NativeTransportSafety,
    NativeWorkflowSafety,
    NativePlatformSafety,
    NativeProbesSafety,
    NativeSourceSafety,
    NativeIntegritySafety,
    NativeBackupSafety,
    NativeRetainedSafety,
    NativePayloadsSafety,
    CredentialsBackupFixture,
    unittest.TestCase,
):
    """Keep the original suite identity while grouping its safety contracts."""


if __name__ == "__main__":
    unittest.main()
