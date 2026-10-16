#!/usr/bin/env python3
"""Physical serials must be rejected before the native acceptance harness calls adb."""

import argparse
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("native_acceptance", ROOT / "tests/e2e/native-backup-acceptance.py")
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class NativeAcceptanceSafetyTest(unittest.TestCase):
    def test_transport_registration_and_selection_are_both_confirmed(self):
        transport = HARNESS.TRANSPORT.encode()
        responses = [b"* other", b"  " + transport, b"ignored selection", b"* other\n  " + transport,
                     b"  " + transport, b"selected", b"* " + transport]
        with patch.object(HARNESS, "adb", side_effect=responses) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.select_local_transport("emulator-5582", attempts=3)
        self.assertEqual(2, sum(call.args[2:] == ("bmgr", "transport", HARNESS.TRANSPORT) for call in adb.call_args_list))

    def test_unavailable_transport_fails_before_installing_or_backing_up(self):
        with patch.object(HARNESS, "adb", return_value=b"* other") as adb, patch.object(HARNESS.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "local backup transport"):
                HARNESS.select_local_transport("emulator-5582", attempts=2)
        self.assertTrue(all(call.args[2:] == ("bmgr", "list", "transports") for call in adb.call_args_list))

    def test_physical_remote_and_ambiguous_targets_are_rejected(self):
        for value in ("38191FDJG00FNY", "192.0.2.1:5555", "adb-phone._tcp", "emulator-", "emulator-5580; id"):
            with self.subTest(serial=value):
                with self.assertRaises(argparse.ArgumentTypeError):
                    HARNESS.emulator_serial(value)

    def test_explicit_emulator_serial_is_accepted(self):
        self.assertEqual("emulator-5582", HARNESS.emulator_serial("emulator-5582"))


if __name__ == "__main__":
    unittest.main()
