import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("managed_permissions", Path(__file__).with_name("managed-permission-acceptance.py"))
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class ManagedPermissionSafetyTest(unittest.TestCase):
    def test_another_fixture_never_authorizes_a_grant(self):
        with self.assertRaisesRegex(RuntimeError, "another managed run"):
            acceptance.permissions_for_receipt({"nonce": "older", "phase": "RESTORE_LOCATION_GRANT_REQUIRED"}, "current")

    def test_location_receipt_grants_only_location(self):
        self.assertEqual(["ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"],
                         acceptance.permissions_for_receipt({"nonce": "current", "phase": "RESTORE_LOCATION_GRANT_REQUIRED"}, "current"))

    def test_motion_receipt_grants_only_motion(self):
        self.assertEqual(["ACTIVITY_RECOGNITION"],
                         acceptance.permissions_for_receipt({"nonce": "current", "phase": "RESTORE_MOTION_GRANT_REQUIRED"}, "current"))

    def test_completion_and_unknown_phases_never_authorize_grants(self):
        for phase in [None, "RESTORE_ACTIVITY_READY", "passed", "unknown"]:
            self.assertEqual([], acceptance.permissions_for_receipt({"nonce": "current", "phase": phase}, "current"))
