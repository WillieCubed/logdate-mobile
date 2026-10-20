from pathlib import Path
import runpy
import unittest

MODULE = runpy.run_path(str(Path(__file__).with_name("native-device-enrollment-acceptance.py")))


class EnrollmentLifetimeTest(unittest.TestCase):
    def test_direct_runs_have_time_for_native_transfer_network_policy_and_draft_checks(self):
        self.assertEqual(1800, MODULE["server_timeout_seconds"]({}))
        self.assertEqual(1800, MODULE["server_timeout_seconds"]({"LOGDATE_NATIVE_ACCEPTANCE_TIMEOUT_SECONDS": "1800"}))

    def test_invalid_or_unbounded_lifetimes_fail_before_server_creation(self):
        for value in ("", "unlimited", "0", "59", "3601", "-1", "1.5"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                MODULE["server_timeout_seconds"]({"LOGDATE_NATIVE_ACCEPTANCE_TIMEOUT_SECONDS": value})


if __name__ == "__main__":
    unittest.main()
