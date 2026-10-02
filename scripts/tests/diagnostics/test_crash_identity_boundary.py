"""Guard the native crash bridge against reintroducing persistent account identity."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]


class CrashIdentityBoundaryTest(unittest.TestCase):
    def test_ios_bridge_only_clears_legacy_identity(self):
        kotlin = (ROOT / 'app/compose-main/src/iosMain/kotlin/app/logdate/IosCrashReportingUserBridge.kt').read_text()
        swift = (ROOT / 'iosApp/iosApp/iOSApp.swift').read_text()
        self.assertNotIn('PasskeyAccountRepository', kotlin)
        self.assertNotIn('setObject(', kotlin)
        self.assertIn('removeObjectForKey(CRASHLYTICS_USER_ID_KEY)', kotlin)
        self.assertNotIn('setUserID(id)', swift)
        self.assertIn('setUserID("")', swift)
        self.assertIn('removeObject(forKey: crashReportingUserIdKey)', swift)


if __name__ == '__main__':
    unittest.main()
