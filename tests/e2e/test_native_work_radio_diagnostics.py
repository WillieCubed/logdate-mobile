import json
from pathlib import Path
import runpy
import unittest
from unittest.mock import patch

from test_native_work_cellular_admission import WORK, wire

D = runpy.run_path(str(Path(__file__).with_name("native-work-radio-diagnostics.py")))


class WorkRadioDiagnosticsTest(unittest.TestCase):
    def test_binding_refusal_reports_only_bounded_known_shape(self):
        private = b"phoneNumber=15555550100\nsubscriberId=PRIVATE_SUBSCRIBER\nhistory=PRIVATE_HISTORY\n"
        phone = b"------- PhoneGlobals -------\n mDefaultDataSubId=1\n" + private
        with patch.object(WORK, "adb", return_value=wire(phone=phone)):
            with self.assertRaises(RuntimeError) as caught:
                WORK.radio_state("emulator-5554")
        notes = getattr(caught.exception, "__notes__", [])
        self.assertTrue(notes, "Refusal must retain redacted shape evidence")
        summary = json.loads(notes[0].split(": ", 1)[1])
        self.assertEqual(1, summary["labels"]["phoneGlobals"])
        self.assertEqual(0, summary["labels"]["dataSettingsManager"])
        rendered = str(caught.exception) + " ".join(notes)
        for secret in (
            "15555550100",
            "PRIVATE_SUBSCRIBER",
            "PRIVATE_HISTORY",
            "subscriberId",
            "phoneNumber",
        ):
            self.assertNotIn(secret, rendered)

    def test_only_strict_numeric_ids_and_boolean_fields_are_emitted(self):
        raw = (
            b"GsmCdmaPhone extends:\nPhone: subId=1\n mPhoneId=0\nDataSettingsManager-0:\n"
            b" isUserDataEnabled=true\n device_provisioned=1\n mDefaultDataSubId=1\n"
            b"phoneNumber=15555550100\n mPhoneId=15555550100\nPhone: subId=PRIVATE_SUBSCRIBER\n"
        )
        summary = D["redacted_radio_shape"](raw)
        self.assertEqual([1], summary.get("ids", {}).get("phoneSubIds"))
        self.assertEqual([0], summary.get("ids", {}).get("phoneIds"))
        self.assertEqual([True], summary.get("booleans", {}).get("userDataEnabled"))
        encoded = json.dumps(summary)
        self.assertNotIn("15555550100", encoded)
        self.assertNotIn("PRIVATE_SUBSCRIBER", encoded)

    def test_oversized_repeated_malformed_input_has_bounded_redacted_evidence(self):
        raw = b"DataSettingsManager-0:\n mPhoneId=0\nSECRET_HISTORY\n" * 40000
        summary = D["redacted_radio_shape"](raw)
        self.assertTrue(summary.get("truncated"))
        encoded = json.dumps(summary)
        self.assertLess(len(encoded), 4096)
        self.assertNotIn("SECRET_HISTORY", encoded)

    def test_redacted_refusal_preserves_fixed_failure_type_and_no_payload(self):
        failure = D["radio_read_refusal"](
            "Fixed refusal", b"Phone: subId=1\nprivateValue=SECRET"
        )
        self.assertIsInstance(failure, RuntimeError)
        self.assertEqual("Fixed refusal", str(failure))
        self.assertTrue(getattr(failure, "__notes__", []))
        self.assertNotIn("SECRET", " ".join(failure.__notes__))


if __name__ == "__main__":
    unittest.main()
